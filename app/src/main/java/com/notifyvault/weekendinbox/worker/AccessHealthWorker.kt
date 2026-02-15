package com.notifyvault.weekendinbox.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notifyvault.weekendinbox.NotifyVaultApp
import com.notifyvault.weekendinbox.R
import com.notifyvault.weekendinbox.util.hasNotificationAccess

class AccessHealthWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NotifyVaultApp
        val disabled = !hasNotificationAccess(applicationContext)
        app.container.prefs.setAccessDisabled(disabled)
        if (disabled) {
            showWarningNotification()
        }
        return Result.success()
    }

    private fun showWarningNotification() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NotifyVault Health", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Notification access disabled")
            .setContentText("Open NotifyVault and re-enable notification access.")
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }

    companion object {
        const val WORK_NAME = "access_health_check"
        private const val CHANNEL_ID = "notifyvault_health"
    }
}
