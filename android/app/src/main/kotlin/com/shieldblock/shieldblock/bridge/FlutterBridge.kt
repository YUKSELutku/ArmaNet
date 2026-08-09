package com.shieldblock.shieldblock.bridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.shieldblock.shieldblock.MainActivity
import com.shieldblock.shieldblock.database.TrafficFilter
import com.shieldblock.shieldblock.database.TrafficRepository
import com.shieldblock.shieldblock.filter.AndroidAssetFilterSource
import com.shieldblock.shieldblock.filter.FilterDecision
import com.shieldblock.shieldblock.filter.FilterEngine
import com.shieldblock.shieldblock.filter.FilterLoader
import com.shieldblock.shieldblock.filter.FilterRuleset
import com.shieldblock.shieldblock.settings.SettingsSnapshot
import com.shieldblock.shieldblock.settings.ShieldBlockPreferences
import com.shieldblock.shieldblock.stats.AppStats
import com.shieldblock.shieldblock.stats.DomainStats
import com.shieldblock.shieldblock.traffic.AppResolver
import com.shieldblock.shieldblock.traffic.InstalledApp
import com.shieldblock.shieldblock.traffic.TrafficEvent
import com.shieldblock.shieldblock.vpn.AdBlockVpnService
import com.shieldblock.shieldblock.vpn.VpnRuntimeState
import com.shieldblock.shieldblock.vpn.VpnState
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class FlutterBridge(
    private val activity: MainActivity,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val context = activity.applicationContext
    private val methodChannel = MethodChannel(messenger, BridgeContract.METHOD_CHANNEL)
    private val eventChannel = EventChannel(messenger, BridgeContract.EVENT_CHANNEL)
    private val eventStream = BridgeEventStream()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ShieldBlock-Bridge-IO").apply { isDaemon = true }
    }
    private val repository = TrafficRepository.getInstance(context)
    private val preferences = ShieldBlockPreferences(context)
    private val appResolver = AppResolver(context)
    private val ruleset: Result<FilterRuleset> by lazy {
        runCatching { FilterLoader(AndroidAssetFilterSource(context)).load() }
    }
    private var pendingPermissionResult: MethodChannel.Result? = null

    init {
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(eventStream)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            BridgeContract.Method.REQUEST_VPN_PERMISSION -> requestVpnPermission(result)
            BridgeContract.Method.START_VPN -> startVpn(result)
            BridgeContract.Method.STOP_VPN -> stopVpn(result)
            BridgeContract.Method.IS_VPN_RUNNING -> result.success(AdBlockVpnService.isRunning())
            BridgeContract.Method.GET_VPN_STATUS -> result.success(
                VpnRuntimeState.run { snapshot().toMap() },
            )
            BridgeContract.Method.GET_FILTER_INFO -> runIo(result) {
                withRulesetValue { loaded ->
                    mapOf(
                        "version" to loaded.version,
                        "updatedAt" to loaded.updatedAt,
                        "advertisementRules" to loaded.adDomains.size,
                        "trackerRules" to loaded.trackerDomains.size,
                        "totalRules" to loaded.blockedDomains.size,
                    )
                }
            }
            BridgeContract.Method.GET_FILTER_RULES -> runIo(result) {
                withRulesetValue { loaded ->
                    loaded.blockedDomains.map { domain ->
                        val metadata = loaded.metadata.getValue(domain)
                        mapOf(
                            "domain" to domain,
                            "network" to metadata.network,
                            "category" to metadata.category.wireValue,
                        )
                    }.sortedWith(
                        compareBy<Map<String, String>>(
                            { it.getValue("category") },
                            { it.getValue("network") },
                            { it.getValue("domain") },
                        ),
                    )
                }
            }
            BridgeContract.Method.EVALUATE_DOMAIN -> evaluateDomain(call, result)
            BridgeContract.Method.GET_STATISTICS -> runIo(result) { statisticsMap() }
            BridgeContract.Method.GET_APPS -> runIo(result) { appsMap() }
            BridgeContract.Method.GET_APP_STATS -> runIo(result) { appStatsMap(call) }
            BridgeContract.Method.GET_RECENT_TRAFFIC -> runIo(result) { recentTraffic(call) }
            BridgeContract.Method.GET_TRAFFIC_FOR_APP -> runIo(result) { trafficForApp(call) }
            BridgeContract.Method.ADD_ALLOWLIST_DOMAIN -> runIo(result) { addAllowlist(call) }
            BridgeContract.Method.REMOVE_ALLOWLIST_DOMAIN -> runIo(result) { removeAllowlist(call) }
            BridgeContract.Method.GET_ALLOWLIST -> runIo(result) { repository.getAllowlist() }
            BridgeContract.Method.ADD_CUSTOM_BLOCKLIST_DOMAIN -> runIo(result) {
                addCustomBlocklist(call)
            }
            BridgeContract.Method.REMOVE_CUSTOM_BLOCKLIST_DOMAIN -> runIo(result) {
                removeCustomBlocklist(call)
            }
            BridgeContract.Method.GET_CUSTOM_BLOCKLIST -> runIo(result) {
                repository.getCustomBlocklist()
            }
            BridgeContract.Method.CLEAR_HISTORY,
            BridgeContract.Method.CLEAR_STATISTICS,
            -> runIo(result) {
                repository.clearHistory()
                NativeEventBus.emit("STATS_UPDATED")
                true
            }
            BridgeContract.Method.GET_SETTINGS -> runIo(result) { preferences.snapshot().toMap() }
            BridgeContract.Method.UPDATE_SETTINGS -> runIo(result) {
                val updated = preferences.update(
                    historyLimit = call.argument<Int>("historyLimit"),
                    debugMode = call.argument<Boolean>("debugMode"),
                )
                AdBlockVpnService.refreshSettings()
                updated.toMap()
            }
            else -> result.notImplemented()
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != VPN_PERMISSION_REQUEST) return false
        pendingPermissionResult?.success(resultCode == Activity.RESULT_OK)
        pendingPermissionResult = null
        return true
    }

    fun handleRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            continueVpnPermissionRequest()
        } else {
            pendingPermissionResult?.success(false)
            pendingPermissionResult = null
        }
        return true
    }

    fun dispose() {
        pendingPermissionResult?.error("ACTIVITY_DESTROYED", "Activity was destroyed.", null)
        pendingPermissionResult = null
        ioExecutor.shutdownNow()
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        eventStream.clear()
    }

    private fun requestVpnPermission(result: MethodChannel.Result) {
        if (pendingPermissionResult != null) {
            result.error(
                BridgeContract.ErrorCode.PERMISSION_REQUEST_ACTIVE,
                "A permission request is already active.",
                null,
            )
            return
        }
        pendingPermissionResult = result
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        } else {
            continueVpnPermissionRequest()
        }
    }

    private fun continueVpnPermissionRequest() {
        val intent = VpnService.prepare(activity)
        if (intent == null) {
            pendingPermissionResult?.success(true)
            pendingPermissionResult = null
        } else {
            activity.startActivityForResult(intent, VPN_PERMISSION_REQUEST)
        }
    }

    private fun startVpn(result: MethodChannel.Result) {
        if (VpnService.prepare(activity) != null) {
            result.error(
                BridgeContract.ErrorCode.VPN_PERMISSION_REQUIRED,
                "Call requestVpnPermission before starting protection.",
                null,
            )
            return
        }
        val intent = AdBlockVpnService.startIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        result.success(true)
    }

    private fun stopVpn(result: MethodChannel.Result) {
        if (VpnRuntimeState.snapshot().state == VpnState.STOPPED) {
            result.success(false)
            return
        }
        context.startService(AdBlockVpnService.stopIntent(context))
        result.success(true)
    }

    private fun evaluateDomain(call: MethodCall, result: MethodChannel.Result) {
        val domain = call.argument<String>("domain")
        if (domain.isNullOrBlank()) {
            result.error(BridgeContract.ErrorCode.INVALID_ARGUMENT, "A non-empty domain is required.", null)
            return
        }
        val allowlist = call.argument<List<*>>("allowlist").orEmpty().filterIsInstance<String>()
        runIo(result) {
            withRulesetValue { loaded ->
                FilterEngine(loaded, allowlist, repository.getCustomBlocklist())
                    .evaluate(domain)
                    .toBridgeMap()
            }
        }
    }

    private fun statisticsMap(): Map<String, Any?> {
        val stats = repository.getStatistics()
        return mapOf(
            "totalConnections" to stats.totalConnections,
            "blockedConnections" to stats.blockedConnections,
            "allowedConnections" to stats.allowedConnections,
            "advertisements" to stats.advertisements,
            "trackers" to stats.trackers,
            "todayBlocked" to stats.todayBlocked,
        )
    }

    private fun appsMap(): List<Map<String, Any?>> {
        val stats = repository.getAppStats().associateBy { it.packageName }
        val installed = appResolver.listLaunchableApps()
        val mapped = installed.map { app -> app.toMap(stats[app.packageName]) }.toMutableList()
        stats.values
            .filter { stat -> stat.packageName == null || installed.none { it.packageName == stat.packageName } }
            .forEach { stat -> mapped += stat.toMap() }
        return mapped.sortedByDescending { (it["totalConnections"] as Number).toLong() }
    }

    private fun appStatsMap(call: MethodCall): Map<String, Any?> {
        val unknownApp = call.argument<Boolean>("unknownApp") == true
        val packageName = call.argument<String>("packageName")
        if (packageName == null && !unknownApp) {
            throw IllegalArgumentException("packageName or unknownApp is required")
        }
        val stat = repository.getAppStats(packageName)
        val app = packageName?.let(appResolver::resolvePackage)
        return mapOf(
            "packageName" to packageName,
            "appName" to (stat?.appName ?: app?.appName ?: "Unknown App"),
            "uid" to app?.uid,
            "icon" to app?.iconPng,
            "totalConnections" to (stat?.totalConnections ?: 0),
            "blockedConnections" to (stat?.blockedConnections ?: 0),
            "allowedConnections" to (stat?.allowedConnections ?: 0),
            "advertisements" to (stat?.advertisements ?: 0),
            "trackers" to (stat?.trackers ?: 0),
            "domains" to repository.getDomainStats(packageName, unknownApp).map { it.toMap() },
        )
    }

    private fun recentTraffic(call: MethodCall): List<Map<String, Any?>> = repository.getRecentEvents(
        TrafficFilter(
            packageName = call.argument("packageName"),
            unknownApp = call.argument<Boolean>("unknownApp") == true,
            domainQuery = call.argument("domain"),
            blocked = call.argument("blocked"),
            category = call.argument("category"),
            sinceTimestamp = call.argument<Number>("sinceTimestamp")?.toLong(),
            limit = call.argument<Int>("limit") ?: 200,
        ),
    ).map { it.toMap() }

    private fun trafficForApp(call: MethodCall): List<Map<String, Any?>> {
        val packageName = call.argument<String>("packageName")
            ?: throw IllegalArgumentException("packageName is required")
        return repository.getEventsForApp(packageName).map { it.toMap() }
    }

    private fun addAllowlist(call: MethodCall): String {
        val domain = repository.addAllowlistDomain(
            call.argument<String>("domain") ?: throw IllegalArgumentException("domain is required"),
        )
        AdBlockVpnService.refreshAllowlist()
        NativeEventBus.emit("ALLOWLIST_UPDATED")
        return domain
    }

    private fun removeAllowlist(call: MethodCall): Boolean {
        val removed = repository.removeAllowlistDomain(
            call.argument<String>("domain") ?: throw IllegalArgumentException("domain is required"),
        )
        AdBlockVpnService.refreshAllowlist()
        NativeEventBus.emit("ALLOWLIST_UPDATED")
        return removed
    }

    private fun addCustomBlocklist(call: MethodCall): String {
        val domain = repository.addCustomBlocklistDomain(
            call.argument<String>("domain")
                ?: throw IllegalArgumentException("domain is required"),
        )
        AdBlockVpnService.refreshCustomBlocklist()
        NativeEventBus.emit("CUSTOM_BLOCKLIST_UPDATED")
        return domain
    }

    private fun removeCustomBlocklist(call: MethodCall): Boolean {
        val removed = repository.removeCustomBlocklistDomain(
            call.argument<String>("domain")
                ?: throw IllegalArgumentException("domain is required"),
        )
        AdBlockVpnService.refreshCustomBlocklist()
        NativeEventBus.emit("CUSTOM_BLOCKLIST_UPDATED")
        return removed
    }

    private fun runIo(result: MethodChannel.Result, action: () -> Any?) {
        ioExecutor.execute {
            try {
                val value = action()
                mainHandler.post { result.success(value) }
            } catch (error: Throwable) {
                mainHandler.post {
                    result.error(
                        if (error is IllegalArgumentException) {
                            BridgeContract.ErrorCode.INVALID_ARGUMENT
                        } else {
                            "NATIVE_ERROR"
                        },
                        error.message ?: "Native operation failed.",
                        null,
                    )
                }
            }
        }
    }

    private fun <T> withRulesetValue(action: (FilterRuleset) -> T): T = ruleset.fold(
        onSuccess = action,
        onFailure = { throw IllegalStateException("The bundled filter ruleset could not be loaded.", it) },
    )

    private fun FilterDecision.toBridgeMap(): Map<String, Any?> = mapOf(
        "action" to action.name.lowercase(),
        "blocked" to blocked,
        "normalizedDomain" to normalizedDomain,
        "matchedDomain" to matchedDomain,
        "network" to network,
        "category" to category.wireValue,
        "allowlistOverride" to allowlistOverride,
    )

    private fun InstalledApp.toMap(stats: AppStats?): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "appName" to appName,
        "uid" to uid,
        "icon" to iconPng,
        "totalConnections" to (stats?.totalConnections ?: 0),
        "blockedConnections" to (stats?.blockedConnections ?: 0),
        "allowedConnections" to (stats?.allowedConnections ?: 0),
        "advertisements" to (stats?.advertisements ?: 0),
        "trackers" to (stats?.trackers ?: 0),
    )

    private fun AppStats.toMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "appName" to (appName ?: "Unknown App"),
        "uid" to null,
        "icon" to null,
        "totalConnections" to totalConnections,
        "blockedConnections" to blockedConnections,
        "allowedConnections" to allowedConnections,
        "advertisements" to advertisements,
        "trackers" to trackers,
    )

    private fun DomainStats.toMap(): Map<String, Any?> = mapOf(
        "domain" to domain,
        "networkName" to networkName,
        "category" to category,
        "totalConnections" to totalConnections,
        "blockedConnections" to blockedConnections,
        "allowedConnections" to allowedConnections,
    )

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

    private fun SettingsSnapshot.toMap(): Map<String, Any?> = mapOf(
        "historyLimit" to historyLimit,
        "debugMode" to debugMode,
    )

    companion object {
        private const val VPN_PERMISSION_REQUEST = 9101
        private const val NOTIFICATION_PERMISSION_REQUEST = 9102
    }
}
