package com.notifyvault.weekendinbox.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notifyvault.weekendinbox.NotifyVaultApp
import com.notifyvault.weekendinbox.data.CapturedNotificationEntity
import com.notifyvault.weekendinbox.domain.OpenNotificationResolver
import com.notifyvault.weekendinbox.domain.OpenPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class VaultNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolver = OpenNotificationResolver()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val app = application as? NotifyVaultApp ?: return

        sbn.notification.contentIntent?.let { cachePendingIntent(sbn.key, it) }

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
            hasContentIntent = sbn.notification.contentIntent != null,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            contentHash = hash,
            capturedAt = System.currentTimeMillis()
        )

        scope.launch {
            app.container.notificationRepository.tryCapture(entity)
        }
    }

    fun openByKey(notificationKey: String, packageName: String): OpenPath {
        val activeIntent = activeNotifications
            ?.firstOrNull { it.key == notificationKey }
            ?.notification
            ?.contentIntent

        val cachedIntent = contentIntentCache[notificationKey]
        return when (resolver.decide(activeIntent != null, cachedIntent != null)) {
            OpenPath.ACTIVE_NOTIFICATION -> sendIntentOrFallback(activeIntent, packageName, OpenPath.ACTIVE_NOTIFICATION)
            OpenPath.CACHED_INTENT -> sendIntentOrFallback(cachedIntent, packageName, OpenPath.CACHED_INTENT)
            OpenPath.APP_LAUNCH_FALLBACK -> {
                launchPackage(packageName)
                OpenPath.APP_LAUNCH_FALLBACK
            }
        }
    }

    private fun sendIntentOrFallback(pendingIntent: PendingIntent?, packageName: String, successPath: OpenPath): OpenPath {
        if (pendingIntent == null) {
            launchPackage(packageName)
            return OpenPath.APP_LAUNCH_FALLBACK
        }
        return try {
            pendingIntent.send()
            successPath
        } catch (_: PendingIntent.CanceledException) {
            launchPackage(packageName)
            OpenPath.APP_LAUNCH_FALLBACK
        }
    }

    private fun launchPackage(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    private fun cachePendingIntent(key: String, pendingIntent: PendingIntent) {
        contentIntentCache[key] = pendingIntent
        cacheOrder.add(key)
        while (cacheOrder.size > CACHE_MAX_SIZE) {
            val oldest = cacheOrder.poll() ?: break
            contentIntentCache.remove(oldest)
        }
    }

    private fun sha256(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_MAX_SIZE = 200
        private var instance: VaultNotificationListenerService? = null
        private val contentIntentCache = ConcurrentHashMap<String, PendingIntent>()
        private val cacheOrder = ConcurrentLinkedQueue<String>()


        fun cancelActiveNotificationBestEffort(notificationKey: String): Boolean {
            val service = instance ?: return false
            return try {
                service.cancelNotification(notificationKey)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun openSavedNotification(context: Context, notificationKey: String, packageName: String): OpenPath {
            val service = instance
            if (service != null) {
                return service.openByKey(notificationKey, packageName)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
            return OpenPath.APP_LAUNCH_FALLBACK
        }
    }
}
