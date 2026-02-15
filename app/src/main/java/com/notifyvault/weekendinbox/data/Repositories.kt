package com.notifyvault.weekendinbox.data

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
    suspend fun restore(entity: CapturedNotificationEntity) = dao.insert(entity)
}
