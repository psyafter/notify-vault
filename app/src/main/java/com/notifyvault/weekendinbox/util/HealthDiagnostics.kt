package com.notifyvault.weekendinbox.util

fun formatDiagnosticsReport(
    listenerEnabled: Boolean,
    batteryExempt: Boolean,
    postNotificationsGranted: Boolean,
    manufacturer: String,
    model: String,
    sdk: Int,
    ruleCount: Int,
    selectedAppsCount: Int
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
        appendLine("selected_apps_count=$selectedAppsCount")
    }
}
