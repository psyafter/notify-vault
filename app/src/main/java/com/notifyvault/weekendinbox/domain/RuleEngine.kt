package com.notifyvault.weekendinbox.domain

import com.notifyvault.weekendinbox.data.AppFilterMode
import com.notifyvault.weekendinbox.data.RuleEntity
import com.notifyvault.weekendinbox.data.RuleType
import java.time.Instant
import java.time.ZoneId

class RuleEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun shouldCapture(nowMillis: Long, packageName: String, rules: List<RuleEntity>): Boolean {
        return rules.any { rule ->
            rule.isActive && isTimeMatch(rule, nowMillis) && isPackageMatch(rule, packageName)
        }
    }

    private fun isTimeMatch(rule: RuleEntity, nowMillis: Long): Boolean {
        return when (rule.type) {
            RuleType.DATE_RANGE -> {
                val start = rule.startDateTimeMillis ?: return false
                val end = rule.endDateTimeMillis ?: return false
                nowMillis in start..end
            }
            RuleType.WEEKEND_REPEAT -> {
                val day = Instant.ofEpochMilli(nowMillis).atZone(zoneId).dayOfWeek.value
                parseDays(rule.weekendDaysCsv).contains(day)
            }
        }
    }

    private fun isPackageMatch(rule: RuleEntity, packageName: String): Boolean {
        val selected = parsePackages(rule.selectedPackagesCsv)
        return when (rule.appFilterMode) {
            AppFilterMode.ALL_EXCEPT -> packageName !in selected
            AppFilterMode.ONLY_SELECTED -> packageName in selected
        }
    }

    private fun parseDays(daysCsv: String): Set<Int> {
        return daysCsv.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    private fun parsePackages(csv: String): Set<String> {
        if (csv.isBlank()) return emptySet()
        return csv.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
}
