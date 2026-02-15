package com.notifyvault.weekendinbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_notifications")
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String?,
    val title: String?,
    val text: String?,
    val subText: String?,
    val postTime: Long,
    val notificationKey: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val contentHash: String,
    val handled: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis()
)

enum class RuleType {
    DATE_RANGE,
    WEEKEND_REPEAT
}

enum class AppFilterMode {
    ALL_EXCEPT,
    ONLY_SELECTED
}

@Entity(tableName = "capture_rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: RuleType,
    val isActive: Boolean = true,
    val appFilterMode: AppFilterMode = AppFilterMode.ALL_EXCEPT,
    val selectedPackagesCsv: String = "",
    val startDateTimeMillis: Long? = null,
    val endDateTimeMillis: Long? = null,
    val weekendDaysCsv: String = "6,7"
)
