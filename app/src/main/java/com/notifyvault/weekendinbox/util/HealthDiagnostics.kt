package com.notifyvault.weekendinbox.util

fun formatDiagnosticsReport(
    listenerEnabled: Boolean,
    batteryExempt: Boolean,
    postNotificationsGranted: Boolean,
    manufacturer: String,
    model: String,
    sdk: Int,
    ruleCount: Int,
    activeRuleCount: Int,
    selectedAppsCount: Int,
    proStatus: Boolean,
    trialDaysLeft: Long
): String {
    return buildString {
        appendLine("NotifyVault diagnostics")
        appendLine("listener_enabled=$listenerEnabled")
        appendLine("ignoring_battery_optimizations=$batteryExempt")
        appendLine("post_notifications_granted=$postNotificationsGranted")
        appendLine("manufacturer=$manufacturer")
        appendLine("model=$model")
        appendLine("sdk=$sdk")
        appendLine("rules_count=$ruleCount")
        appendLine("active_rules_count=$activeRuleCount")
        appendLine("selected_apps_count=$selectedAppsCount")
        appendLine("pro_status=$proStatus")
        appendLine("trial_days_left=$trialDaysLeft")
    }
}
