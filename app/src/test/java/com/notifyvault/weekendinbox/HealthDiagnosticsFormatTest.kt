package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.util.formatDiagnosticsReport
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthDiagnosticsFormatTest {

    @Test
    fun diagnosticsReportIncludesRequiredFields() {
        val report = formatDiagnosticsReport(
            listenerEnabled = true,
            batteryExempt = false,
            postNotificationsGranted = true,
            manufacturer = "Samsung",
            model = "SM-S901B",
            sdk = 34,
            ruleCount = 3,
            selectedAppsCount = 7
        )

        assertTrue(report.contains("listener_enabled=true"))
        assertTrue(report.contains("ignoring_battery_optimizations=false"))
        assertTrue(report.contains("post_notifications_granted=true"))
        assertTrue(report.contains("manufacturer=Samsung"))
        assertTrue(report.contains("model=SM-S901B"))
        assertTrue(report.contains("sdk=34"))
        assertTrue(report.contains("rules_count=3"))
        assertTrue(report.contains("selected_apps_count=7"))
    }
}
