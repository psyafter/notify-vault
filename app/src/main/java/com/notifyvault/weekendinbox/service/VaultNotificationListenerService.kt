package com.notifyvault.weekendinbox.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notifyvault.weekendinbox.NotifyVaultApp
import com.notifyvault.weekendinbox.data.CapturedNotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

class VaultNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = application as? NotifyVaultApp ?: return

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        val subText = extras?.getCharSequence("android.subText")?.toString()
        val postTime = sbn.postTime
        val hash = sha256("${sbn.packageName}|$title|$text|$subText|$postTime")

        val entity = CapturedNotificationEntity(
            packageName = sbn.packageName,
            appName = null,
            title = title,
            text = text,
            subText = subText,
            postTime = postTime,
            notificationKey = sbn.key,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            contentHash = hash,
            capturedAt = System.currentTimeMillis()
        )

        scope.launch {
            app.container.notificationRepository.tryCapture(entity)
        }
    }

    private fun sha256(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
