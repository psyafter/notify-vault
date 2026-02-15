package com.notifyvault.weekendinbox.domain

class OpenNotificationResolver {
    fun decide(hasActiveIntent: Boolean, hasCachedIntent: Boolean): OpenPath {
        return when {
            hasActiveIntent -> OpenPath.ACTIVE_NOTIFICATION
            hasCachedIntent -> OpenPath.CACHED_INTENT
            else -> OpenPath.APP_LAUNCH_FALLBACK
        }
    }
}

enum class OpenPath {
    ACTIVE_NOTIFICATION,
    CACHED_INTENT,
    APP_LAUNCH_FALLBACK
}
