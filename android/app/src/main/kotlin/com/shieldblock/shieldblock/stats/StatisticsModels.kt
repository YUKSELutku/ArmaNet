package com.shieldblock.shieldblock.stats

data class OverallStatistics(
    val totalConnections: Long,
    val blockedConnections: Long,
    val allowedConnections: Long,
    val advertisements: Long,
    val trackers: Long,
    val todayBlocked: Long,
)

data class AppStats(
    val packageName: String?,
    val appName: String?,
    val totalConnections: Long,
    val blockedConnections: Long,
    val allowedConnections: Long,
    val advertisements: Long,
    val trackers: Long,
)

data class DomainStats(
    val domain: String,
    val networkName: String?,
    val category: String,
    val totalConnections: Long,
    val blockedConnections: Long,
    val allowedConnections: Long,
)
