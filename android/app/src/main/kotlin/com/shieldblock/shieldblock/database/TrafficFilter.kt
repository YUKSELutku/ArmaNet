package com.shieldblock.shieldblock.database

data class TrafficFilter(
    val packageName: String? = null,
    val unknownApp: Boolean = false,
    val domainQuery: String? = null,
    val blocked: Boolean? = null,
    val category: String? = null,
    val sinceTimestamp: Long? = null,
    val limit: Int = 200,
)
