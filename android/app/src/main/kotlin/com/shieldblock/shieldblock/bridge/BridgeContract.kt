package com.shieldblock.shieldblock.bridge

object BridgeContract {
    const val METHOD_CHANNEL = "com.shieldblock/native"
    const val EVENT_CHANNEL = "com.shieldblock/events"

    object Method {
        const val REQUEST_VPN_PERMISSION = "requestVpnPermission"
        const val START_VPN = "startVpn"
        const val STOP_VPN = "stopVpn"
        const val IS_VPN_RUNNING = "isVpnRunning"
        const val GET_VPN_STATUS = "getVpnStatus"
        const val GET_FILTER_INFO = "getFilterInfo"
        const val GET_FILTER_RULES = "getFilterRules"
        const val EVALUATE_DOMAIN = "evaluateDomain"
        const val GET_STATISTICS = "getStatistics"
        const val GET_APPS = "getApps"
        const val GET_APP_STATS = "getAppStats"
        const val GET_RECENT_TRAFFIC = "getRecentTraffic"
        const val GET_TRAFFIC_FOR_APP = "getTrafficForApp"
        const val ADD_ALLOWLIST_DOMAIN = "addAllowlistDomain"
        const val REMOVE_ALLOWLIST_DOMAIN = "removeAllowlistDomain"
        const val CLEAR_HISTORY = "clearHistory"
        const val CLEAR_STATISTICS = "clearStatistics"
        const val GET_ALLOWLIST = "getAllowlist"
        const val ADD_CUSTOM_BLOCKLIST_DOMAIN = "addCustomBlocklistDomain"
        const val REMOVE_CUSTOM_BLOCKLIST_DOMAIN = "removeCustomBlocklistDomain"
        const val GET_CUSTOM_BLOCKLIST = "getCustomBlocklist"
        const val GET_SETTINGS = "getSettings"
        const val UPDATE_SETTINGS = "updateSettings"
    }

    object ErrorCode {
        const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
        const val FILTER_LOAD_FAILED = "FILTER_LOAD_FAILED"
        const val FEATURE_NOT_IMPLEMENTED = "FEATURE_NOT_IMPLEMENTED"
        const val PERMISSION_REQUEST_ACTIVE = "PERMISSION_REQUEST_ACTIVE"
        const val VPN_PERMISSION_REQUIRED = "VPN_PERMISSION_REQUIRED"
    }
}
