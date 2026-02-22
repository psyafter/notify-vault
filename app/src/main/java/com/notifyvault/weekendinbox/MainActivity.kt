package com.notifyvault.weekendinbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notifyvault.weekendinbox.data.AppContainer
import com.notifyvault.weekendinbox.data.CapturedNotificationEntity
import com.notifyvault.weekendinbox.data.CaptureMode
import com.notifyvault.weekendinbox.data.RuleEntity
import com.notifyvault.weekendinbox.data.RuleType
import com.notifyvault.weekendinbox.data.SwipeActionMode
import com.notifyvault.weekendinbox.domain.OpenPath
import com.notifyvault.weekendinbox.util.formatDiagnosticsReport
import com.notifyvault.weekendinbox.util.hasNotificationAccess
import com.notifyvault.weekendinbox.util.hasPostNotificationsPermission
import com.notifyvault.weekendinbox.util.isIgnoringBatteryOptimizations
import com.notifyvault.weekendinbox.util.manufacturerTips
import com.notifyvault.weekendinbox.util.openNotificationListenerSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NotifyVaultApp
        val vm = ViewModelProvider(this, MainVmFactory(app.container, app))[MainViewModel::class.java]
        app.container.billingRepository.connect()
        setContent { MaterialTheme { NotifyVaultAppUi(vm) } }
    }
}

class MainVmFactory(private val container: AppContainer, private val app: NotifyVaultApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app, container) as T
}

private fun CapturedNotificationEntity.uiStableKey(): String =
    "$notificationKey|$capturedAt|$contentHash"

data class InstalledAppUi(val packageName: String, val label: String, val icon: Drawable?)

class MainViewModel(app: NotifyVaultApp, private val container: AppContainer) : AndroidViewModel(app) {
    val rules = container.ruleRepository.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notifications: StateFlow<List<CapturedNotificationEntity>> =
        container.notificationRepository.observeVault(null, null, null, null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedApps = container.selectedAppsRepository.observeSelectedPackages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val billingState = container.billingRepository.state

    var onboardingDone by mutableStateOf(false)
    var canCapture by mutableStateOf(true)
    var trialDaysLeft by mutableStateOf(14L)
    var showSetupTip by mutableStateOf(false)
    var swipeActionMode by mutableStateOf(SwipeActionMode.SWIPE_REVEAL_DELETE)

    init {
        viewModelScope.launch {
            container.prefs.ensureFirstLaunch()
            onboardingDone = container.prefs.hasCompletedOnboarding()
            canCapture = container.prefs.canCaptureNewNotifications()
            trialDaysLeft = container.prefs.trialDaysLeft()
            showSetupTip = container.prefs.shouldShowSetupTip()
            swipeActionMode = container.prefs.swipeActionMode()
            ensureDefaultWeekendsRule()
            container.billingRepository.refreshPurchases()
        }
    }

    private suspend fun ensureDefaultWeekendsRule() {
        if (!onboardingDone) return
        if (rules.value.none { it.name == "Weekends" }) {
            container.ruleRepository.upsert(
                RuleEntity(name = "Weekends", type = RuleType.WEEKEND_REPEAT, isActive = true, weekendDaysCsv = "6,7")
            )
        }
    }

    fun refreshPaywallState() = viewModelScope.launch {
        canCapture = container.prefs.canCaptureNewNotifications()
        trialDaysLeft = container.prefs.trialDaysLeft()
    }

    fun completeOnboarding() = viewModelScope.launch {
        container.prefs.setOnboardingDone()
        onboardingDone = true
        ensureDefaultWeekendsRule()
    }

    fun dismissSetupTip() = viewModelScope.launch {
        container.prefs.markSetupTipShown()
        showSetupTip = false
    }

    fun launchPurchase(activity: ComponentActivity) = container.billingRepository.launchPurchase(activity)
    fun restorePurchases() = container.billingRepository.restorePurchases()

    fun isAccessEnabled(): Boolean = hasNotificationAccess(getApplication())
    fun deleteNotification(item: CapturedNotificationEntity) = viewModelScope.launch { container.notificationRepository.delete(item.id) }
    fun restoreNotification(item: CapturedNotificationEntity) = viewModelScope.launch { container.notificationRepository.restore(item) }
    fun setAppSelected(packageName: String, selected: Boolean) = viewModelScope.launch { container.selectedAppsRepository.setSelected(packageName, selected) }

    fun setSwipeActionMode(mode: SwipeActionMode) = viewModelScope.launch {
        container.prefs.setSwipeActionMode(mode)
        swipeActionMode = mode
    }
    fun simulateCapture() = viewModelScope.launch {
        container.notificationRepository.restore(
            CapturedNotificationEntity(
                packageName = "debug.simulated",
                appName = "Simulated",
                title = "Debug notification",
                text = "Inserted from diagnostics",
                subText = null,
                postTime = System.currentTimeMillis(),
                notificationKey = "debug-${System.currentTimeMillis()}",
                hasContentIntent = false,
                isOngoing = false,
                isClearable = true,
                contentHash = "debug-${System.nanoTime()}"
            )
        )
    }

    fun loadLaunchableApps(query: String): List<InstalledAppUi> {
        val pm = getApplication<NotifyVaultApp>().packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { InstalledAppUi(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun openSavedNotification(notification: CapturedNotificationEntity): OpenPath {
        return com.notifyvault.weekendinbox.service.VaultNotificationListenerService.openSavedNotification(
            getApplication(), notification.notificationKey, notification.packageName
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifyVaultAppUi(vm: MainViewModel) {
    val notifications by vm.notifications.collectAsState()
    val selectedApps by vm.selectedApps.collectAsState()
    val rules by vm.rules.collectAsState()
    val billing by vm.billingState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshPaywallState() }

    if (!vm.onboardingDone) {
        OnboardingFlow(vm, selectedApps)
        return
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var expandedStableKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showSelectApps by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == 0) "Vault" else if (tab == 1) "Settings" else "Fix setup") }) },
        floatingActionButton = {
            if (tab == 1) FloatingActionButton(onClick = { showSelectApps = true }) { Icon(Icons.Default.Add, null) }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!vm.isAccessEnabled()) {
                Card { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Capture is disabled. Fix setup to continue.", modifier = Modifier.weight(1f))
                    TextButton(onClick = { tab = 2 }) { Text("Fix setup") }
                } }
            }
            if (vm.showSetupTip) {
                Card { Row(Modifier.padding(12.dp)) {
                    Text("You’re set: Weekend rule ON + selected apps. Notifications will appear in Vault.", modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::dismissSetupTip) { Text("Got it") }
                } }
            }
            if (!vm.canCapture && !billing.isPro) {
                Card { Row(Modifier.padding(12.dp)) {
                    Text("Trial ended — Unlock Pro to keep capturing", modifier = Modifier.weight(1f))
                    TextButton(onClick = { tab = 1 }) { Text("Unlock") }
                } }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { tab = 0 }, label = { Text("Vault") })
                AssistChip(onClick = { tab = 1 }, label = { Text("Settings") })
                AssistChip(onClick = { tab = 2 }, label = { Text("Fix setup") })
            }

            when (tab) {
                0 -> {
                    if (notifications.isEmpty()) {
                        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nothing captured yet")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showSelectApps = true }) { Text("Select apps") }
                                Button(onClick = { /* weekends already auto-created */ }) { Text("Add rule / enable Weekends") }
                            }
                        } }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(notifications, key = { it.uiStableKey() }) { item ->
                                VaultNotificationRow(
                                    item = item,
                                    expanded = expandedStableKey == item.uiStableKey(),
                                    formatter = formatter,
                                    swipeActionMode = vm.swipeActionMode,
                                    onToggleExpanded = {
                                        val itemStableKey = item.uiStableKey()
                                        expandedStableKey = if (expandedStableKey == itemStableKey) null else itemStableKey
                                    },
                                    onOpenSource = {
                                        if (vm.openSavedNotification(item) == OpenPath.APP_LAUNCH_FALLBACK) {
                                            scope.launch { snackbar.showSnackbar("Opened app fallback.") }
                                        }
                                    },
                                    onDelete = {
                                        if (expandedStableKey == item.uiStableKey()) {
                                            expandedStableKey = null
                                        }
                                        vm.deleteNotification(item)
                                        scope.launch {
                                            if (snackbar.showSnackbar("Deleted", actionLabel = "Undo") == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                vm.restoreNotification(item)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> SettingsSection(vm, billing)
                else -> DiagnosticsSection(vm, rules.size, rules.count { it.isActive }, selectedApps.size, billing.isPro)
            }
        }
    }

    if (showSelectApps) SelectAppsDialog(vm, selectedApps) { showSelectApps = false }
}

@Composable
private fun SettingsSection(vm: MainViewModel, billing: com.notifyvault.weekendinbox.data.BillingState) {
    val activity = LocalContext.current as ComponentActivity
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Swipe action", fontWeight = FontWeight.Bold)
                SwipeActionMode.entries.forEach { mode ->
                    val label = when (mode) {
                        SwipeActionMode.SWIPE_REVEAL_DELETE -> "Reveal delete (safe)"
                        SwipeActionMode.SWIPE_IMMEDIATE_DELETE -> "Swipe deletes immediately (fast)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setSwipeActionMode(mode) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = vm.swipeActionMode == mode, onClick = { vm.setSwipeActionMode(mode) })
                        Text(label)
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Unlock NotifyVault Pro", fontWeight = FontWeight.Bold)
                Text("Benefits: unlimited capturing and unlimited rules")
                Text(if (billing.isPro) "Pro active" else "14-day trial: ${vm.trialDaysLeft} day(s) left")
                Button(onClick = { vm.launchPurchase(activity) }, enabled = !billing.isPro) {
                    Text("Buy Pro")
                }
                TextButton(onClick = vm::restorePurchases) { Text("Restore purchases") }
            }
        }
    }
}


@Composable
private fun VaultNotificationRow(
    item: CapturedNotificationEntity,
    expanded: Boolean,
    formatter: SimpleDateFormat,
    swipeActionMode: SwipeActionMode,
    onToggleExpanded: () -> Unit,
    onOpenSource: () -> Unit,
    onDelete: () -> Unit
) {
    val mode by rememberUpdatedState(swipeActionMode)
    val scope = rememberCoroutineScope()
    var deleteTriggered by remember(item.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.EndToStart || deleteTriggered) return@rememberSwipeToDismissBoxState false
            if (mode == SwipeActionMode.SWIPE_IMMEDIATE_DELETE) {
                deleteTriggered = true
                onDelete()
                false
            } else {
                true
            }
        },
        positionalThreshold = { totalDistance ->
            totalDistance * if (mode == SwipeActionMode.SWIPE_IMMEDIATE_DELETE) 0.75f else 0.60f
        }
    )

    LaunchedEffect(mode) {
        if (mode == SwipeActionMode.SWIPE_IMMEDIATE_DELETE && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp)
                    .clip(CardDefaults.shape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (mode == SwipeActionMode.SWIPE_REVEAL_DELETE) {
                    TextButton(
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            deleteTriggered = true
                            onDelete()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete notification action",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    ) {
        Card(Modifier.fillMaxWidth().clickable {
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                scope.launch { dismissState.reset() }
            } else {
                onToggleExpanded()
            }
        }) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.appName ?: item.packageName, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.rotate(if (expanded) 180f else 0f))
                }
                Text(item.title ?: "(no title)")
                if (expanded) {
                    Text(item.text ?: "")
                    Text("Captured: ${formatter.format(Date(item.capturedAt))}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onOpenSource) { Text("Open source app") }
                        IconButton(onClick = {
                            deleteTriggered = true
                            onDelete()
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete notification") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(vm: MainViewModel, ruleCount: Int, activeRuleCount: Int, selectedAppsCount: Int, isPro: Boolean) {
    val context = LocalContext.current
    val tips = remember { manufacturerTips() }
    val listenerEnabled = hasNotificationAccess(context)
    val batteryExempt = isIgnoringBatteryOptimizations(context)
    val postPermission = hasPostNotificationsPermission(context)

    fun diagnostics(): String = formatDiagnosticsReport(
        listenerEnabled = listenerEnabled,
        batteryExempt = batteryExempt,
        postNotificationsGranted = postPermission,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        sdk = Build.VERSION.SDK_INT,
        ruleCount = ruleCount,
        activeRuleCount = activeRuleCount,
        selectedAppsCount = selectedAppsCount,
        proStatus = isPro,
        trialDaysLeft = vm.trialDaysLeft
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { openNotificationListenerSettings(context) }) { Text("Fix setup") }
        Button(onClick = {
            val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("NotifyVault diagnostics", diagnostics()))
        }) { Text("Copy diagnostics") }
        Button(onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, diagnostics())
            }, "Share diagnostics"))
        }) { Text("Share diagnostics") }
        if (BuildConfig.DEBUG) {
            Button(onClick = vm::simulateCapture) { Text("Simulate capture") }
        }
        Text(tips.title)
    }
}

@Composable
private fun OnboardingFlow(vm: MainViewModel, selectedApps: List<String>) {
    var step by rememberSaveable { mutableStateOf(0) }
    var showSelectApps by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (step) {
            0 -> {
                Text("Welcome to NotifyVault", style = MaterialTheme.typography.titleLarge)
                Text("Capture selected app notifications offline so you never lose them.")
                Button(onClick = { step = 1 }) { Text("Continue") }
            }
            1 -> {
                Text("Choose what to capture", style = MaterialTheme.typography.titleLarge)
                Text("Select at least one app to continue.")
                Button(onClick = { showSelectApps = true }) { Text("Select apps") }
                Button(onClick = { if (selectedApps.isNotEmpty()) step = 2 }, enabled = selectedApps.isNotEmpty()) { Text("Continue") }
            }
            else -> {
                Text("Enable capture", style = MaterialTheme.typography.titleLarge)
                Text("Turn on Notification Access for NotifyVault.")
                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Open notification access")
                }
                Button(onClick = {
                    if (vm.isAccessEnabled()) vm.completeOnboarding()
                }) { Text("I enabled it") }
                if (!vm.isAccessEnabled()) Text("Fix setup: access is still disabled.")
            }
        }
    }

    if (showSelectApps) SelectAppsDialog(vm, selectedApps) { showSelectApps = false }
}

@Composable
private fun SelectAppsDialog(vm: MainViewModel, selectedApps: List<String>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val apps = remember(query) { vm.loadLaunchableApps(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select apps") },
        text = {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedApps
                    Row(Modifier.fillMaxWidth().clickable { vm.setAppSelected(app.packageName, !checked) }.padding(6.dp)) {
                        app.icon?.let { Image(it.toBitmap(), null, Modifier.size(24.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(app.label)
                            Text(app.packageName)
                        }
                        Checkbox(checked = checked, onCheckedChange = { vm.setAppSelected(app.packageName, it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun Drawable.toBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
