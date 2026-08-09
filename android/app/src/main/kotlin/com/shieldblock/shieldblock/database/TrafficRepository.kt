package com.shieldblock.shieldblock.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.shieldblock.shieldblock.filter.DomainNormalizer
import com.shieldblock.shieldblock.stats.AppStats
import com.shieldblock.shieldblock.stats.DomainStats
import com.shieldblock.shieldblock.stats.OverallStatistics
import com.shieldblock.shieldblock.traffic.AttributionConfidence
import com.shieldblock.shieldblock.traffic.TrafficEvent
import java.util.Calendar

class TrafficRepository private constructor(context: Context) {
    private val helper = TrafficDatabaseHelper(context.applicationContext)

    @Synchronized
    fun insertEvent(event: TrafficEvent, historyLimit: Int): Long {
        val db = helper.writableDatabase
        val id = db.insertOrThrow("traffic_events", null, event.toValues())
        if (id % PRUNE_INTERVAL == 0L) {
            pruneToLimit(historyLimit.coerceIn(100, 50_000))
        }
        return id
    }

    @Synchronized
    fun pruneToLimit(limit: Int) {
        helper.writableDatabase.execSQL(
            """
            DELETE FROM traffic_events
            WHERE id NOT IN (
                SELECT id FROM traffic_events ORDER BY timestamp DESC, id DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(limit.coerceIn(100, 50_000)),
        )
    }

    @Synchronized
    fun getRecentEvents(filter: TrafficFilter = TrafficFilter()): List<TrafficEvent> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (filter.unknownApp) {
            clauses += "package_name IS NULL"
        } else {
            filter.packageName?.let {
                clauses += "package_name = ?"
                args += it
            }
        }
        filter.domainQuery?.takeIf(String::isNotBlank)?.let {
            clauses += "domain LIKE ?"
            args += "%${it.trim()}%"
        }
        filter.blocked?.let {
            clauses += "blocked = ?"
            args += if (it) "1" else "0"
        }
        filter.category?.takeIf { it != "all" }?.let {
            clauses += "category = ?"
            args += it
        }
        filter.sinceTimestamp?.let {
            clauses += "timestamp >= ?"
            args += it.toString()
        }
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val sql = "SELECT * FROM traffic_events $where ORDER BY timestamp DESC, id DESC LIMIT ?"
        args += filter.limit.coerceIn(1, 1_000).toString()
        return helper.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrafficEvent())
            }
        }
    }

    fun getEventsForApp(packageName: String, limit: Int = 500): List<TrafficEvent> =
        getRecentEvents(TrafficFilter(packageName = packageName, limit = limit))

    @Synchronized
    fun getStatistics(): OverallStatistics {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sql =
            """
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(blocked), 0) AS blocked_count,
                COALESCE(SUM(CASE WHEN blocked = 0 THEN 1 ELSE 0 END), 0) AS allowed_count,
                COALESCE(SUM(CASE WHEN blocked = 1 AND category = 'advertisement' THEN 1 ELSE 0 END), 0) AS ads,
                COALESCE(SUM(CASE WHEN blocked = 1 AND category = 'tracker' THEN 1 ELSE 0 END), 0) AS trackers,
                COALESCE(SUM(CASE WHEN blocked = 1 AND timestamp >= ? THEN 1 ELSE 0 END), 0) AS today_blocked
            FROM traffic_events
            """.trimIndent()
        return helper.readableDatabase.rawQuery(sql, arrayOf(startOfDay.toString())).use { cursor ->
            cursor.moveToFirst()
            OverallStatistics(
                totalConnections = cursor.getLong(0),
                blockedConnections = cursor.getLong(1),
                allowedConnections = cursor.getLong(2),
                advertisements = cursor.getLong(3),
                trackers = cursor.getLong(4),
                todayBlocked = cursor.getLong(5),
            )
        }
    }

    @Synchronized
    fun getAppStats(limit: Int = 200): List<AppStats> {
        val sql =
            """
            SELECT package_name, MAX(app_name), COUNT(*), COALESCE(SUM(blocked), 0),
                   COALESCE(SUM(CASE WHEN blocked = 0 THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN blocked = 1 AND category = 'advertisement' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN blocked = 1 AND category = 'tracker' THEN 1 ELSE 0 END), 0)
            FROM traffic_events
            GROUP BY package_name
            ORDER BY COUNT(*) DESC
            LIMIT ?
            """.trimIndent()
        return helper.readableDatabase.rawQuery(sql, arrayOf(limit.coerceIn(1, 500).toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AppStats(
                            packageName = cursor.nullableString(0),
                            appName = cursor.nullableString(1),
                            totalConnections = cursor.getLong(2),
                            blockedConnections = cursor.getLong(3),
                            allowedConnections = cursor.getLong(4),
                            advertisements = cursor.getLong(5),
                            trackers = cursor.getLong(6),
                        ),
                    )
                }
            }
        }
    }

    fun getAppStats(packageName: String?): AppStats? =
        getAppStats().firstOrNull { it.packageName == packageName }

    @Synchronized
    fun getDomainStats(packageName: String? = null, unknownApp: Boolean = false): List<DomainStats> {
        val where = when {
            unknownApp -> "WHERE package_name IS NULL"
            packageName != null -> "WHERE package_name = ?"
            else -> ""
        }
        val args = if (packageName != null && !unknownApp) arrayOf(packageName) else emptyArray()
        val sql =
            """
            SELECT domain, MAX(network_name), MAX(category), COUNT(*), COALESCE(SUM(blocked), 0),
                   COALESCE(SUM(CASE WHEN blocked = 0 THEN 1 ELSE 0 END), 0)
            FROM traffic_events
            $where
            GROUP BY domain
            ORDER BY COUNT(*) DESC
            LIMIT 500
            """.trimIndent()
        return helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DomainStats(
                            domain = cursor.getString(0),
                            networkName = cursor.nullableString(1),
                            category = cursor.getString(2),
                            totalConnections = cursor.getLong(3),
                            blockedConnections = cursor.getLong(4),
                            allowedConnections = cursor.getLong(5),
                        ),
                    )
                }
            }
        }
    }

    @Synchronized
    fun clearHistory() {
        helper.writableDatabase.delete("traffic_events", null, null)
    }

    @Synchronized
    fun addAllowlistDomain(rawDomain: String): String {
        val domain = DomainNormalizer.normalize(rawDomain)
            ?: throw IllegalArgumentException("Invalid allowlist domain")
        val values = ContentValues().apply {
            put("domain", domain)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            "allowlist",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
        )
        return domain
    }

    @Synchronized
    fun removeAllowlistDomain(rawDomain: String): Boolean {
        val domain = DomainNormalizer.normalize(rawDomain) ?: return false
        return helper.writableDatabase.delete("allowlist", "domain = ?", arrayOf(domain)) > 0
    }

    @Synchronized
    fun getAllowlist(): List<String> =
        helper.readableDatabase.rawQuery(
            "SELECT domain FROM allowlist ORDER BY domain ASC",
            emptyArray(),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    @Synchronized
    fun addCustomBlocklistDomain(rawDomain: String): String {
        val domain = DomainNormalizer.normalize(rawDomain)
            ?: throw IllegalArgumentException("Invalid custom blocklist domain")
        val values = ContentValues().apply {
            put("domain", domain)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            "custom_blocklist",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
        )
        return domain
    }

    @Synchronized
    fun removeCustomBlocklistDomain(rawDomain: String): Boolean {
        val domain = DomainNormalizer.normalize(rawDomain) ?: return false
        return helper.writableDatabase.delete(
            "custom_blocklist",
            "domain = ?",
            arrayOf(domain),
        ) > 0
    }

    @Synchronized
    fun getCustomBlocklist(): List<String> =
        helper.readableDatabase.rawQuery(
            "SELECT domain FROM custom_blocklist ORDER BY domain ASC",
            emptyArray(),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun TrafficEvent.toValues(): ContentValues = ContentValues().apply {
        uid?.let { put("uid", it) }
        put("package_name", packageName)
        put("app_name", appName)
        put("domain", domain)
        put("destination_ip", destinationIp)
        destinationPort?.let { put("destination_port", it) }
        put("protocol", protocol)
        put("network_name", networkName)
        put("category", category)
        put("blocked", if (blocked) 1 else 0)
        put("timestamp", timestamp)
        put("attribution_confidence", attributionConfidence.wireValue)
    }

    private fun Cursor.toTrafficEvent(): TrafficEvent = TrafficEvent(
        id = getLong(getColumnIndexOrThrow("id")),
        uid = nullableInt(getColumnIndexOrThrow("uid")),
        packageName = nullableString(getColumnIndexOrThrow("package_name")),
        appName = nullableString(getColumnIndexOrThrow("app_name")),
        domain = getString(getColumnIndexOrThrow("domain")),
        destinationIp = nullableString(getColumnIndexOrThrow("destination_ip")),
        destinationPort = nullableInt(getColumnIndexOrThrow("destination_port")),
        protocol = getString(getColumnIndexOrThrow("protocol")),
        networkName = nullableString(getColumnIndexOrThrow("network_name")),
        category = getString(getColumnIndexOrThrow("category")),
        blocked = getInt(getColumnIndexOrThrow("blocked")) == 1,
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        attributionConfidence = AttributionConfidence.fromWireValue(
            nullableString(getColumnIndexOrThrow("attribution_confidence")),
        ),
    )

    private fun Cursor.nullableString(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun Cursor.nullableInt(index: Int): Int? = if (isNull(index)) null else getInt(index)

    companion object {
        private const val PRUNE_INTERVAL = 100L

        @Volatile
        private var instance: TrafficRepository? = null

        fun getInstance(context: Context): TrafficRepository =
            instance ?: synchronized(this) {
                instance ?: TrafficRepository(context).also { instance = it }
            }
    }
}
