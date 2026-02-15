package com.notifyvault.weekendinbox.data

import android.content.Context
import androidx.core.content.edit
import com.notifyvault.weekendinbox.domain.CapturePolicy
import com.notifyvault.weekendinbox.domain.RuleEngine
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val dao: RuleDao) {
    fun observeRules(): Flow<List<RuleEntity>> = dao.observeAll()
    suspend fun upsert(ruleEntity: RuleEntity) = dao.upsert(ruleEntity)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun activeRules(): List<RuleEntity> = dao.activeRules()
    suspend fun byId(id: Long): RuleEntity? = dao.byId(id)
}

class SelectedAppsRepository(private val dao: SelectedAppDao) {
    fun observeSelectedPackages(): Flow<List<String>> = dao.observeSelectedPackages()
    suspend fun setSelected(packageName: String, selected: Boolean) {
        if (selected) dao.upsert(SelectedAppEntity(packageName = packageName)) else dao.delete(packageName)
    }

    suspend fun isSelected(packageName: String): Boolean = dao.isSelected(packageName)
}

class NotificationRepository(
    private val dao: NotificationDao,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val prefs: AppPrefs,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val capturePolicy: CapturePolicy = CapturePolicy()
) {
    fun observeVault(packageFilter: String?, fromDate: Long?, toDate: Long?, search: String?): Flow<List<CapturedNotificationEntity>> {
        return dao.observeAll(packageFilter, fromDate, toDate, search)
    }

    fun observeKnownPackages(): Flow<List<String>> = dao.observeKnownPackages()

    suspend fun shouldCapturePackage(packageName: String): Boolean {
        val mode = prefs.captureMode()
        val selected = if (mode == CaptureMode.ONLY_SELECTED_APPS) {
            selectedAppsRepository.isSelected(packageName)
        } else {
            true
        }
        return capturePolicy.shouldCapturePackage(mode, selected)
    }

    suspend fun tryCapture(entity: CapturedNotificationEntity): Boolean {
        if (!prefs.canCaptureNewNotifications()) return false
        if (!shouldCapturePackage(entity.packageName)) return false

        val rules = ruleRepository.activeRules()
        if (!ruleEngine.shouldCapture(entity.capturedAt, entity.packageName, rules)) return false

        val last = dao.latest()
        if (last != null && last.contentHash == entity.contentHash && last.packageName == entity.packageName) {
            return false
        }
        dao.insert(entity)
        return true
    }

    suspend fun markHandled(id: Long) = dao.markHandled(id)
    suspend fun delete(id: Long) = dao.deleteById(id)
}

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("notifyvault_prefs", Context.MODE_PRIVATE)

    fun ensureFirstLaunch(): Long {
        val existing = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
        if (existing > 0) return existing
        val now = System.currentTimeMillis()
        prefs.edit { putLong(KEY_FIRST_LAUNCH, now) }
        return now
    }

    fun isPro(): Boolean = prefs.getBoolean(KEY_PRO, false)

    fun setPro(value: Boolean) {
        prefs.edit { putBoolean(KEY_PRO, value) }
    }

    fun setAccessDisabled(disabled: Boolean) {
        prefs.edit { putBoolean(KEY_ACCESS_DISABLED, disabled) }
    }

    fun isAccessDisabled(): Boolean = prefs.getBoolean(KEY_ACCESS_DISABLED, false)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun setOnboardingDone() {
        prefs.edit { putBoolean(KEY_ONBOARDED, true) }
    }

    fun captureMode(): CaptureMode {
        val raw = prefs.getString(KEY_CAPTURE_MODE, CaptureMode.ONLY_SELECTED_APPS.name)
        return CaptureMode.entries.firstOrNull { it.name == raw } ?: CaptureMode.ONLY_SELECTED_APPS
    }

    fun setCaptureMode(mode: CaptureMode) {
        prefs.edit { putString(KEY_CAPTURE_MODE, mode.name) }
    }

    fun canCaptureNewNotifications(now: Long = System.currentTimeMillis()): Boolean {
        if (isPro()) return true
        val first = ensureFirstLaunch()
        val graceMillis = 14L * 24L * 60L * 60L * 1000L
        return now - first <= graceMillis
    }

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PRO = "is_pro"
        private const val KEY_ACCESS_DISABLED = "access_disabled"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_CAPTURE_MODE = "capture_mode"
    }
}
