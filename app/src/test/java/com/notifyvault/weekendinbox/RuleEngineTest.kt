package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.data.AppFilterMode
import com.notifyvault.weekendinbox.data.RuleEntity
import com.notifyvault.weekendinbox.data.RuleType
import com.notifyvault.weekendinbox.domain.RuleEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RuleEngineTest {
    @Test
    fun dateRange_matches_inside_range() {
        val zone = ZoneId.of("UTC")
        val engine = RuleEngine(zone)
        val start = LocalDateTime.of(2026, 1, 1, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 1, 1, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 1, 1, 11, 0).atZone(zone).toInstant().toEpochMilli()
        val rule = RuleEntity(name = "range", type = RuleType.DATE_RANGE, startDateTimeMillis = start, endDateTimeMillis = end)

        assertTrue(engine.shouldCapture(now, "com.chat", listOf(rule)))
    }

    @Test
    fun weekend_rule_respects_selected_days() {
        val zone = ZoneId.of("UTC")
        val engine = RuleEngine(zone)
        val saturday = LocalDateTime.of(2026, 1, 3, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val monday = LocalDateTime.of(2026, 1, 5, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val rule = RuleEntity(name = "weekend", type = RuleType.WEEKEND_REPEAT, weekendDaysCsv = "6,7")

        assertTrue(engine.shouldCapture(saturday, "com.chat", listOf(rule)))
        assertFalse(engine.shouldCapture(monday, "com.chat", listOf(rule)))
    }

    @Test
    fun timezone_changes_day_resolution() {
        val utc = RuleEngine(ZoneId.of("UTC"))
        val tokyo = RuleEngine(ZoneId.of("Asia/Tokyo"))
        val instant = LocalDateTime.of(2026, 1, 2, 23, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val sundayOnly = RuleEntity(name = "sun", type = RuleType.WEEKEND_REPEAT, weekendDaysCsv = "7")

        assertFalse(utc.shouldCapture(instant, "com.chat", listOf(sundayOnly)))
        assertTrue(tokyo.shouldCapture(instant, "com.chat", listOf(sundayOnly)))
    }

    @Test
    fun app_filter_mode_only_selected_blocks_non_selected() {
        val engine = RuleEngine(ZoneId.of("UTC"))
        val rule = RuleEntity(
            name = "weekend",
            type = RuleType.WEEKEND_REPEAT,
            appFilterMode = AppFilterMode.ONLY_SELECTED,
            selectedPackagesCsv = "com.allowed",
            weekendDaysCsv = "6,7"
        )
        val saturday = LocalDateTime.of(2026, 1, 3, 9, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()

        assertTrue(engine.shouldCapture(saturday, "com.allowed", listOf(rule)))
        assertFalse(engine.shouldCapture(saturday, "com.other", listOf(rule)))
    }
}
