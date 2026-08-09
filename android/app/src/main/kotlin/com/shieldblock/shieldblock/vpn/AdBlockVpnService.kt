package com.shieldblock.shieldblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import com.shieldblock.shieldblock.MainActivity
import com.shieldblock.shieldblock.bridge.NativeEventBus
import com.shieldblock.shieldblock.database.TrafficRepository
import com.shieldblock.shieldblock.dns.DnsPacketParser
import com.shieldblock.shieldblock.dns.DnsResponseBuilder
import com.shieldblock.shieldblock.dns.IpPacketParser
import com.shieldblock.shieldblock.dns.UdpPacket
import com.shieldblock.shieldblock.dns.UdpResponsePacketBuilder
import com.shieldblock.shieldblock.dns.UpstreamDnsResolver
import com.shieldblock.shieldblock.filter.AndroidAssetFilterSource
import com.shieldblock.shieldblock.filter.FilterEngine
import com.shieldblock.shieldblock.filter.FilterLoader
import com.shieldblock.shieldblock.settings.ShieldBlockPreferences
import com.shieldblock.shieldblock.traffic.AppResolver
import com.shieldblock.shieldblock.traffic.ConnectionOwnerResolver
import com.shieldblock.shieldblock.traffic.TrafficEvent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AdBlockVpnService : VpnService() {
    private val active = AtomicBoolean(false)
    private val outputLock = Any()
    private var tunnel: ParcelFileDescriptor? = null
    private var tunnelInput: FileInputStream? = null
    private var tunnelOutput: FileOutputStream? = null
    private var readerExecutor: ExecutorService? = null
    private var packetExecutor: ExecutorService? = null
    private val controlExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ShieldBlock-Control").apply { isDaemon = true }
    }
    private lateinit var filterEngine: FilterEngine
    private lateinit var repository: TrafficRepository
    private lateinit var preferences: ShieldBlockPreferences
    private lateinit var appResolver: AppResolver
    private lateinit var ownerResolver: ConnectionOwnerResolver
    private lateinit var upstreamResolver: UpstreamDnsResolver
    @Volatile
    private var historyLimit = ShieldBlockPreferences.DEFAULT_HISTORY_LIMIT

    override fun onCreate() {
        super.onCreate()
        instance = this
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        repository = TrafficRepository.getInstance(this)
        preferences = ShieldBlockPreferences(this)
        appResolver = AppResolver(this)
        ownerResolver = ConnectionOwnerResolver(connectivity)
        upstreamResolver = UpstreamDnsResolver(this, connectivity)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            promoteToForeground()
        } catch (error: Throwable) {
            Log.e(TAG, "Could not promote VPN service to foreground", error)
            VpnRuntimeState.update(VpnState.ERROR, error.message ?: "Foreground service startup failed")
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            controlExecutor.execute {
                when (intent?.action ?: ACTION_START) {
                    ACTION_STOP -> stopVpn()
                    ACTION_START -> startVpn()
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not schedule VPN lifecycle operation", error)
            VpnRuntimeState.update(VpnState.ERROR, error.message ?: "VPN lifecycle failed")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        runCatching {
            controlExecutor.execute { stopVpn("VPN permission was revoked by Android.") }
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        val stateBeforeDestroy = VpnRuntimeState.snapshot().state
        closeTunnel()
        controlExecutor.shutdownNow()
        instance = null
        if (stateBeforeDestroy != VpnState.STOPPED && stateBeforeDestroy != VpnState.ERROR) {
            VpnRuntimeState.update(VpnState.STOPPED, "VPN service was destroyed by Android.")
        }
        super.onDestroy()
    }

    fun reloadAllowlist() {
        if (::filterEngine.isInitialized) {
            filterEngine.updateAllowlist(repository.getAllowlist())
        }
    }

    fun reloadCustomBlocklist() {
        if (::filterEngine.isInitialized) {
            filterEngine.updateCustomBlocklist(repository.getCustomBlocklist())
        }
    }

    fun reloadSettings() {
        historyLimit = preferences.snapshot().historyLimit
        repository.pruneToLimit(historyLimit)
    }

    @Synchronized
    private fun startVpn() {
        if (active.get()) return
        if (prepare(this) != null) {
            VpnRuntimeState.update(VpnState.ERROR, "VPN permission is required.")
            stopSelf()
            return
        }
        VpnRuntimeState.update(VpnState.STARTING)
        try {
            // Disk and asset I/O intentionally happens after startForeground and
            // off the main thread so slow devices cannot trigger a service ANR.
            filterEngine = FilterEngine(
                FilterLoader(AndroidAssetFilterSource(this)).load(),
                repository.getAllowlist(),
                repository.getCustomBlocklist(),
            )
            historyLimit = preferences.snapshot().historyLimit
            repository.pruneToLimit(historyLimit)
            VpnRuntimeState.resetHealth(upstreamResolver.hasActivePrivateDns())
            val descriptor = Builder()
                .setSession("ArmaNet")
                .setMtu(TUN_MTU)
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                .allowFamily(OsConstants.AF_INET6)
                .addDisallowedApplication(packageName)
                .setBlocking(true)
                .establish()
                ?: error("Android did not establish the VPN interface")
            tunnel = descriptor
            tunnelInput = FileInputStream(descriptor.fileDescriptor)
            tunnelOutput = FileOutputStream(descriptor.fileDescriptor)
            active.set(true)
            packetExecutor = Executors.newFixedThreadPool(PACKET_WORKERS) { runnable ->
                Thread(runnable, "ShieldBlock-DNS-Worker").apply { isDaemon = true }
            }
            readerExecutor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "ShieldBlock-TUN-Reader").apply { isDaemon = true }
            }.also { executor -> executor.execute(::readPackets) }
            VpnRuntimeState.update(VpnState.RUNNING, "DNS filtering is active.")
            Log.i(TAG, "VPN tunnel started with DNS-only route $VIRTUAL_DNS/32")
        } catch (error: Throwable) {
            Log.e(TAG, "VPN startup failed", error)
            VpnRuntimeState.recordPacketError(error)
            VpnRuntimeState.update(VpnState.ERROR, error.message ?: "VPN startup failed")
            closeTunnel()
            stopSelf()
        }
    }

    private fun readPackets() {
        val buffer = ByteArray(TUN_MTU)
        try {
            val input = tunnelInput ?: return
            while (active.get()) {
                val length = input.read(buffer)
                if (length <= 0) continue
                val packetBytes = buffer.copyOf(length)
                val executor = packetExecutor ?: break
                runCatching { executor.execute { processPacketSafely(packetBytes) } }
                    .onFailure { error ->
                        if (active.get()) handleRuntimeError("Packet dispatch failed", error)
                    }
            }
        } catch (error: Throwable) {
            if (active.get()) {
                Log.e(TAG, "VPN packet reader stopped", error)
                VpnRuntimeState.recordPacketError(error)
                VpnRuntimeState.update(VpnState.ERROR, error.message ?: "VPN packet reader stopped")
                stopSelf()
            }
        }
    }

    private fun processPacketSafely(packetBytes: ByteArray) {
        try {
            processPacket(packetBytes)
        } catch (error: Throwable) {
            handleRuntimeError("DNS packet processing failed", error)
        }
    }

    private fun handleRuntimeError(message: String, error: Throwable) {
        Log.e(TAG, message, error)
        VpnRuntimeState.recordPacketError(error)
        NativeEventBus.emit(
            "VPN_RUNTIME_WARNING",
            mapOf("message" to (error.message ?: message)),
        )
    }

    private fun processPacket(packetBytes: ByteArray) {
        val packet = IpPacketParser.parseUdp(packetBytes) ?: return
        if (packet.destinationPort != DNS_PORT) return
        val dnsQuery = DnsPacketParser.parseQuery(packet.payload)
        if (dnsQuery == null) {
            relayMalformedQuery(packet)
            return
        }
        val decision = if (dnsQuery.isAddressQuery) filterEngine.evaluate(dnsQuery.domain) else null
        val responsePayload = if (decision?.blocked == true) {
            DnsResponseBuilder.nxdomain(packet.payload, dnsQuery)
        } else {
            when (val upstream = upstreamResolver.resolve(packet.payload)) {
                is UpstreamDnsResolver.ResolutionResult.Success -> upstream.payload
                is UpstreamDnsResolver.ResolutionResult.Failure -> {
                    DnsResponseBuilder.serverFailure(packet.payload, dnsQuery)
                }
            }
        }
        writeResponse(packet, responsePayload)
        VpnRuntimeState.recordDnsQuery(decision?.blocked == true)

        val uid = ownerResolver.resolveUid(packet)
        val identity = appResolver.resolveUid(uid)
        val event = TrafficEvent(
            uid = identity.uid,
            packageName = identity.packageName,
            appName = identity.appName,
            domain = dnsQuery.domain,
            destinationIp = packet.destinationAddress.hostAddress,
            destinationPort = packet.destinationPort,
            protocol = "UDP/DNS",
            networkName = decision?.network,
            category = decision?.category?.wireValue ?: "unknown",
            blocked = decision?.blocked == true,
            attributionConfidence = identity.confidence,
        )
        val persisted = event.copy(id = repository.insertEvent(event, historyLimit))
        NativeEventBus.emit(
            if (persisted.blocked) "BLOCKED_EVENT" else "TRAFFIC_EVENT",
            mapOf("event" to persisted.toMap()),
        )
        NativeEventBus.emit("STATS_UPDATED")
    }

    private fun relayMalformedQuery(packet: UdpPacket) {
        when (val upstream = upstreamResolver.resolve(packet.payload)) {
            is UpstreamDnsResolver.ResolutionResult.Success -> writeResponse(packet, upstream.payload)
            is UpstreamDnsResolver.ResolutionResult.Failure -> Unit
        }
    }

    private fun writeResponse(request: UdpPacket, payload: ByteArray) {
        val output = tunnelOutput ?: return
        val response = UdpResponsePacketBuilder.buildResponse(request, payload)
        synchronized(outputLock) {
            runCatching { output.write(response) }
                .onFailure { error -> handleRuntimeError("TUN response write failed", error) }
        }
    }

    @Synchronized
    private fun stopVpn(detail: String? = null) {
        if (!active.get()) {
            VpnRuntimeState.update(VpnState.STOPPED, detail)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        VpnRuntimeState.update(VpnState.STOPPING)
        closeTunnel()
        VpnRuntimeState.update(VpnState.STOPPED, detail)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTunnel() {
        active.set(false)
        runCatching { tunnelInput?.close() }
        runCatching { tunnelOutput?.close() }
        runCatching { tunnel?.close() }
        tunnelInput = null
        tunnelOutput = null
        tunnel = null
        readerExecutor?.shutdownNow()
        packetExecutor?.shutdownNow()
        runCatching { readerExecutor?.awaitTermination(500, TimeUnit.MILLISECONDS) }
        runCatching { packetExecutor?.awaitTermination(500, TimeUnit.MILLISECONDS) }
        readerExecutor = null
        packetExecutor = null
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags(),
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags(),
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentTitle("ArmaNet protection is active")
            .setContentText("Known advertisement and tracker DNS requests are filtered on-device.")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "ArmaNet protection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Persistent VPN protection status" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun TrafficEvent.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "uid" to uid,
        "packageName" to packageName,
        "appName" to appName,
        "domain" to domain,
        "destinationIp" to destinationIp,
        "destinationPort" to destinationPort,
        "protocol" to protocol,
        "networkName" to networkName,
        "category" to category,
        "blocked" to blocked,
        "timestamp" to timestamp,
        "attributionConfidence" to attributionConfidence.wireValue,
    )

    companion object {
        const val ACTION_START = "com.shieldblock.action.START_VPN"
        const val ACTION_STOP = "com.shieldblock.action.STOP_VPN"
        private const val NOTIFICATION_CHANNEL = "shieldblock_vpn"
        private const val NOTIFICATION_ID = 1401
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"
        private const val TUN_MTU = 32_767
        private const val PACKET_WORKERS = 4
        private const val DNS_PORT = 53
        private const val TAG = "ShieldBlockVpn"

        @Volatile
        private var instance: AdBlockVpnService? = null

        fun isRunning(): Boolean = VpnRuntimeState.snapshot().running

        fun refreshAllowlist() {
            instance?.reloadAllowlist()
        }

        fun refreshCustomBlocklist() {
            instance?.reloadCustomBlocklist()
        }

        fun refreshSettings() {
            instance?.reloadSettings()
        }

        fun startIntent(context: Context): Intent =
            Intent(context, AdBlockVpnService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, AdBlockVpnService::class.java).setAction(ACTION_STOP)
    }
}
