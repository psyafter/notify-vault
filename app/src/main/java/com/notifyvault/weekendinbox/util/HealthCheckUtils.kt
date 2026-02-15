package com.notifyvault.weekendinbox.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

private data class OemIntentSpec(
    val packageName: String,
    val componentName: String? = null,
    val action: String? = null
)

data class ManufacturerTips(
    val title: String,
    val steps: List<String>,
    val intents: List<Intent>
)

fun hasNotificationAccess(context: Context): Boolean {
    val cn = ComponentName(context, com.notifyvault.weekendinbox.service.VaultNotificationListenerService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(cn.flattenToString())
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun openNotificationListenerSettings(context: Context): Boolean {
    return launchIntentSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

fun requestIgnoreBatteryOptimization(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    return launchIntentSafely(context, intent)
}

fun openBatteryOptimizationSettings(context: Context): Boolean {
    return launchIntentSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

fun openAppInfoSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    return launchIntentSafely(context, intent)
}

fun openManufacturerSettings(context: Context): Boolean {
    val intents = manufacturerTips().intents
    intents.forEach { candidate ->
        if (launchIntentSafely(context, candidate)) return true
    }
    return false
}

fun manufacturerTips(): ManufacturerTips {
    val maker = Build.MANUFACTURER.lowercase()
    return when {
        maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> {
            ManufacturerTips(
                title = "Xiaomi / Redmi / POCO (MIUI/HyperOS)",
                steps = listOf(
                    "Enable Autostart for NotifyVault.",
                    "Set Battery saver mode to No restrictions for NotifyVault."
                ),
                intents = oemIntents(
                    OemIntentSpec("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    OemIntentSpec("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                    OemIntentSpec("com.miui.securitycenter", action = "miui.intent.action.OP_AUTO_START")
                )
            )
        }
        maker.contains("samsung") -> {
            ManufacturerTips(
                title = "Samsung (One UI)",
                steps = listOf(
                    "Set Battery usage to Unrestricted for NotifyVault.",
                    "Disable Put unused apps to sleep for NotifyVault."
                ),
                intents = oemIntents(
                    OemIntentSpec("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    OemIntentSpec("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity")
                )
            )
        }
        maker.contains("huawei") || maker.contains("honor") -> {
            ManufacturerTips(
                title = "Huawei / Honor",
                steps = listOf(
                    "Allow Auto-launch for NotifyVault.",
                    "Disable automatic battery management for NotifyVault."
                ),
                intents = oemIntents(
                    OemIntentSpec("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    OemIntentSpec("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                )
            )
        }
        maker.contains("oneplus") || maker.contains("oppo") || maker.contains("realme") || maker.contains("vivo") -> {
            ManufacturerTips(
                title = "OnePlus / Oppo / Realme / Vivo",
                steps = listOf(
                    "Allow Auto-start or background activity for NotifyVault.",
                    "Disable battery optimization for NotifyVault."
                ),
                intents = oemIntents(
                    OemIntentSpec("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    OemIntentSpec("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                    OemIntentSpec("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                    OemIntentSpec("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    OemIntentSpec("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                )
            )
        }
        else -> {
            ManufacturerTips(
                title = "Generic Android guidance",
                steps = listOf(
                    "Disable battery optimization for NotifyVault.",
                    "Allow notifications and keep notification access enabled."
                ),
                intents = emptyList()
            )
        }
    }
}

private fun oemIntents(vararg specs: OemIntentSpec): List<Intent> {
    return specs.map {
        Intent(it.action ?: Intent.ACTION_MAIN).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (it.componentName != null) {
                component = ComponentName(it.packageName, it.componentName)
            } else {
                `package` = it.packageName
            }
        }
    }
}

private fun launchIntentSafely(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
