package com.notifyvault.weekendinbox

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notifyvault.weekendinbox.data.AppDatabase
import com.notifyvault.weekendinbox.data.AppPrefs
import com.notifyvault.weekendinbox.data.NotificationRepository
import com.notifyvault.weekendinbox.data.RuleRepository
import com.notifyvault.weekendinbox.domain.RuleEngine
import com.notifyvault.weekendinbox.worker.AccessHealthWorker
import java.util.concurrent.TimeUnit

class NotifyVaultApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleHealthChecks()
    }

    private fun scheduleHealthChecks() {
        val work = PeriodicWorkRequestBuilder<AccessHealthWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AccessHealthWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}

class AppContainer(application: Application) {
    private val db = AppDatabase.get(application)
    val prefs = AppPrefs(application)
    private val ruleEngine = RuleEngine()
    val ruleRepository = RuleRepository(db.ruleDao())
    val notificationRepository = NotificationRepository(db.notificationDao(), ruleRepository, ruleEngine, prefs)
}
