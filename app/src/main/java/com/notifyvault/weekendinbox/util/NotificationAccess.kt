package com.notifyvault.weekendinbox.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

fun hasNotificationAccess(context: Context): Boolean {
    val cn = ComponentName(context, com.notifyvault.weekendinbox.service.VaultNotificationListenerService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(cn.flattenToString())
}
