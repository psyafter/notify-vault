package com.notifyvault.weekendinbox.util

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

private const val PACKAGE_SCHEME = "package"

enum class OemFamily {
    XIAOMI,
    SAMSUNG,
    HUAWEI_HONOR,
    BBK_PLUS_ONEPLUS,
    OTHER
}

data class OemIntentSpec(
    val packageName: String,
    val componentName: String? = null,
    val action: String? = null
)

data class ManufacturerTips(
    val title: String,
    val steps: List<String>,
    val intents: List<Intent>
)

data class IntentLaunchPlan(
    val action: String,
    val packageData: Boolean = false,
    val extras: Map<String, String> = emptyMap()
)

fun detectOemFamily(manufacturerRaw: String): OemFamily {
    val maker = manufacturerRaw.lowercase()
    return when {
        maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> OemFamily.XIAOMI
        maker.contains("samsung") -> OemFamily.SAMSUNG
        maker.contains("huawei") || maker.contains("honor") -> OemFamily.HUAWEI_HONOR
        maker.contains("oneplus") || maker.contains("oppo") || maker.contains("realme") || maker.contains("vivo") -> OemFamily.BBK_PLUS_ONEPLUS
        else -> OemFamily.OTHER
    }
}

fun resolveLaunchPlan(
    plans: List<IntentLaunchPlan>,
    canLaunch: (IntentLaunchPlan) -> Boolean,
    fallback: IntentLaunchPlan
): IntentLaunchPlan {
    return plans.firstOrNull(canLaunch) ?: fallback
}

fun hasNotificationAccess(context: Context): Boolean {
    val cn = ComponentName(context, com.notifyvault.weekendinbox.service.VaultNotificationListenerService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(cn.flattenToString())
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun isBackgroundRestricted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return activityManager.isBackgroundRestricted
}

fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun openNotificationListenerSettings(context: Context): Boolean {
    return launchPlansWithFallback(
        context,
        listOf(IntentLaunchPlan(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    )
}

fun requestIgnoreBatteryOptimization(context: Context): Boolean {
    return launchPlansWithFallback(
        context,
        listOf(IntentLaunchPlan(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageData = true))
    )
}

fun openBatteryOptimizationSettings(context: Context): Boolean {
    return launchPlansWithFallback(
        context,
        listOf(IntentLaunchPlan(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    )
}

fun openAppNotificationSettings(context: Context): Boolean {
    val plans = mutableListOf<IntentLaunchPlan>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        plans += IntentLaunchPlan(
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            extras = mapOf(Settings.EXTRA_APP_PACKAGE to context.packageName)
        )
    }
    @Suppress("DEPRECATION")
    plans += IntentLaunchPlan(
        action = "android.settings.APP_NOTIFICATION_SETTINGS",
        extras = mapOf("app_package" to context.packageName, "app_uid" to context.applicationInfo.uid.toString())
    )
    return launchPlansWithFallback(context, plans)
}

fun openAppInfoSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)
    }
    return launchIntentSafely(context, intent)
}

fun openManufacturerSettings(context: Context): Boolean {
    val intents = manufacturerTips().intents
    intents.forEach { candidate ->
        if (launchIntentSafely(context, candidate)) return true
    }
    return openAppInfoSettings(context)
}

fun manufacturerTips(manufacturerRaw: String = Build.MANUFACTURER): ManufacturerTips {
    return when (detectOemFamily(manufacturerRaw)) {
        OemFamily.XIAOMI -> ManufacturerTips(
            title = "Xiaomi / Redmi / POCO (MIUI/HyperOS)",
            steps = listOf(
                "Open App info > Battery saver and set NotifyVault to No restrictions.",
                "Enable Autostart for NotifyVault.",
                "Lock NotifyVault in recent apps to reduce process kills."
            ),
            intents = oemIntents(
                OemIntentSpec("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                OemIntentSpec("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                OemIntentSpec("com.miui.securitycenter", action = "miui.intent.action.OP_AUTO_START")
            )
        )

        OemFamily.SAMSUNG -> ManufacturerTips(
            title = "Samsung (One UI)",
            steps = listOf(
                "Set Battery usage to Unrestricted for NotifyVault.",
                "In Background usage limits, ensure NotifyVault is not sleeping.",
                "Disable adaptive battery restrictions for NotifyVault if present."
            ),
            intents = oemIntents(
                OemIntentSpec("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                OemIntentSpec("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity")
            )
        )

        OemFamily.HUAWEI_HONOR -> ManufacturerTips(
            title = "Huawei / Honor",
            steps = listOf(
                "Allow Auto-launch, Secondary launch, and Run in background.",
                "Disable automatic battery management for NotifyVault.",
                "Whitelist NotifyVault in Phone Manager power settings."
            ),
            intents = oemIntents(
                OemIntentSpec("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                OemIntentSpec("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
        )

        OemFamily.BBK_PLUS_ONEPLUS -> ManufacturerTips(
            title = "OnePlus / Oppo / Realme / Vivo",
            steps = listOf(
                "Allow Auto-start/background activity for NotifyVault.",
                "Set battery mode to Unrestricted/No restrictions.",
                "Disable app freezing or deep optimization for NotifyVault."
            ),
            intents = oemIntents(
                OemIntentSpec("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                OemIntentSpec("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                OemIntentSpec("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                OemIntentSpec("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                OemIntentSpec("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
        )

        OemFamily.OTHER -> ManufacturerTips(
            title = "Generic Android guidance",
            steps = listOf(
                "Disable battery optimization for NotifyVault.",
                "Allow notifications and keep Notification access enabled.",
                "If capture stops, open App info and allow background usage."
            ),
            intents = emptyList()
        )
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

private fun launchPlansWithFallback(context: Context, primaryPlans: List<IntentLaunchPlan>): Boolean {
    val fallback = IntentLaunchPlan(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageData = true)
    val selected = resolveLaunchPlan(primaryPlans, { plan -> canResolve(context, toIntent(context, plan)) }, fallback)
    val launched = launchIntentSafely(context, toIntent(context, selected))
    if (launched || selected == fallback) return launched
    return launchIntentSafely(context, toIntent(context, fallback))
}

private fun toIntent(context: Context, plan: IntentLaunchPlan): Intent {
    return Intent(plan.action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (plan.packageData) {
            data = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)
        }
        plan.extras.forEach { (k, v) -> putExtra(k, v) }
    }
}

private fun canResolve(context: Context, intent: Intent): Boolean {
    return intent.resolveActivity(context.packageManager) != null
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
