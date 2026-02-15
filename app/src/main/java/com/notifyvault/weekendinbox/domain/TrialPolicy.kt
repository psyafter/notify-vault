package com.notifyvault.weekendinbox.domain

private const val TRIAL_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1000L

data class TrialStatus(
    val isPro: Boolean,
    val trialActive: Boolean,
    val daysLeft: Long
)

object TrialPolicy {
    fun isTrialActive(firstLaunchEpochMillis: Long, nowEpochMillis: Long): Boolean {
        if (firstLaunchEpochMillis <= 0L) return true
        return nowEpochMillis - firstLaunchEpochMillis <= TRIAL_WINDOW_MILLIS
    }

    fun canCaptureNewNotifications(isPro: Boolean, firstLaunchEpochMillis: Long, nowEpochMillis: Long): Boolean {
        return isPro || isTrialActive(firstLaunchEpochMillis, nowEpochMillis)
    }

    fun daysLeft(firstLaunchEpochMillis: Long, nowEpochMillis: Long): Long {
        if (firstLaunchEpochMillis <= 0L) return 14
        val elapsed = nowEpochMillis - firstLaunchEpochMillis
        val remaining = (TRIAL_WINDOW_MILLIS - elapsed).coerceAtLeast(0L)
        return kotlin.math.ceil(remaining / (24.0 * 60.0 * 60.0 * 1000.0)).toLong()
    }
}
