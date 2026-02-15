package com.notifyvault.weekendinbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.notifyvault.weekendinbox.data.AppContainer
import com.notifyvault.weekendinbox.data.AppFilterMode
import com.notifyvault.weekendinbox.data.CaptureMode
import com.notifyvault.weekendinbox.data.CapturedNotificationEntity
import com.notifyvault.weekendinbox.data.RuleEntity
import com.notifyvault.weekendinbox.data.RuleType
import com.notifyvault.weekendinbox.data.SwipeActionMode
import com.notifyvault.weekendinbox.domain.OpenPath
import com.notifyvault.weekendinbox.service.VaultNotificationListenerService
import com.notifyvault.weekendinbox.util.hasNotificationAccess
import com.notifyvault.weekendinbox.util.formatDiagnosticsReport
import com.notifyvault.weekendinbox.util.hasPostNotificationsPermission
import com.notifyvault.weekendinbox.util.isBackgroundRestricted
import com.notifyvault.weekendinbox.util.isIgnoringBatteryOptimizations
import com.notifyvault.weekendinbox.util.manufacturerTips
import com.notifyvault.weekendinbox.util.openAppInfoSettings
import com.notifyvault.weekendinbox.util.openAppNotificationSettings
import com.notifyvault.weekendinbox.util.openBatteryOptimizationSettings
import com.notifyvault.weekendinbox.util.openManufacturerSettings
import com.notifyvault.weekendinbox.util.openNotificationListenerSettings
import com.notifyvault.weekendinbox.util.requestIgnoreBatteryOptimization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotifyVaultAppUi(vm) {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
        }
    }
}

class MainVmFactory(private val container: AppContainer, private val app: NotifyVaultApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app, container) as T
}

data class InstalledAppUi(val packageName: String, val label: String, val icon: Drawable?)

class MainViewModel(app: NotifyVaultApp, private val container: AppContainer) : AndroidViewModel(app) {
    private val selectedPackage = MutableStateFlow<String?>(null)
    private val fromDate = MutableStateFlow<Long?>(null)
    private val toDate = MutableStateFlow<Long?>(null)
    private val search = MutableStateFlow("")

    val rules = container.ruleRepository.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val packages = container.notificationRepository.observeKnownPackages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedApps = container.selectedAppsRepository.observeSelectedPackages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<CapturedNotificationEntity>> = combine(selectedPackage, fromDate, toDate, search) { pkg, from, to, q ->
        FilterState(pkg, from, to, q)
    }.flatMapLatest {
        container.notificationRepository.observeVault(it.pkg, it.from, it.to, it.search)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onboardingDone: Boolean get() = container.prefs.hasCompletedOnboarding()

    fun isAccessEnabled(): Boolean = hasNotificationAccess(getApplication())
    fun canCapture(): Boolean = container.prefs.canCaptureNewNotifications()
    fun captureMode(): CaptureMode = container.prefs.captureMode()
    fun setCaptureMode(mode: CaptureMode) = container.prefs.setCaptureMode(mode)
    fun setSearch(value: String) { search.value = value }
    fun setPackageFilter(value: String?) { selectedPackage.value = value }
    fun setFromDate(value: Long?) { fromDate.value = value }
    fun setToDate(value: Long?) { toDate.value = value }
    fun completeOnboarding() = container.prefs.setOnboardingDone()
    fun setPro(value: Boolean) = container.prefs.setPro(value)
    fun saveRule(rule: RuleEntity) = viewModelScope.launch { container.ruleRepository.upsert(rule) }
    fun toggleRule(rule: RuleEntity, active: Boolean) = viewModelScope.launch { container.ruleRepository.upsert(rule.copy(isActive = active)) }
    fun deleteRule(id: Long) = viewModelScope.launch { container.ruleRepository.delete(id) }
    fun markHandled(id: Long) = viewModelScope.launch { container.notificationRepository.markHandled(id) }

    fun deleteNotification(item: CapturedNotificationEntity) = viewModelScope.launch {
        container.notificationRepository.delete(item.id)
    }

    fun restoreNotification(item: CapturedNotificationEntity) = viewModelScope.launch {
        container.notificationRepository.restore(item)
    }

    fun swipeActionMode(): SwipeActionMode = container.prefs.swipeActionMode()
    fun setSwipeActionMode(mode: SwipeActionMode) = container.prefs.setSwipeActionMode(mode)
    fun setAppSelected(packageName: String, selected: Boolean) = viewModelScope.launch {
        container.selectedAppsRepository.setSelected(packageName, selected)
    }

    fun loadLaunchableApps(query: String): List<InstalledAppUi> {
        val pm = getApplication<NotifyVaultApp>().packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .map {
                val pkg = it.activityInfo.packageName
                InstalledAppUi(
                    packageName = pkg,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .filter {
                query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun openSavedNotification(notification: CapturedNotificationEntity): OpenPath {
        return VaultNotificationListenerService.openSavedNotification(getApplication(), notification.notificationKey, notification.packageName)
    }
}

data class FilterState(val pkg: String?, val from: Long?, val to: Long?, val search: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifyVaultAppUi(vm: MainViewModel, openAccessSettings: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }
    var showSelectApps by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(0L) }
    var pkg by remember { mutableStateOf<String?>(null) }
    val rules by vm.rules.collectAsState()
    val notifications by vm.notifications.collectAsState()
    val packages by vm.packages.collectAsState()
    val selectedApps by vm.selectedApps.collectAsState()
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(search) { vm.setSearch(search) }
    LaunchedEffect(from) { vm.setFromDate(from.takeIf { it > 0 }) }
    LaunchedEffect(to) { vm.setToDate(to.takeIf { it > 0 }) }
    LaunchedEffect(pkg) { vm.setPackageFilter(pkg) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(when (tab) { 0 -> "Vault"; 1 -> "Rules"; 2 -> "Settings"; else -> "Fix setup" }) }, actions = {
                if (!vm.isAccessEnabled()) TextButton(onClick = openAccessSettings) { Text("Enable access") }
            })
        },
        floatingActionButton = {
            if (tab == 1) FloatingActionButton(onClick = { showRuleDialog = true }) { Icon(Icons.Default.Add, null) }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { tab = 0 }, label = { Text("Vault") })
                AssistChip(onClick = { tab = 1 }, label = { Text("Rules") })
                AssistChip(onClick = { tab = 2 }, label = { Text("Settings") })
                AssistChip(onClick = { tab = 3 }, label = { Text("Fix setup") })
            }
            if (tab == 0) {
                OutlinedTextField(search, { search = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(from.takeIf { it > 0 }?.toString() ?: "", { from = it.toLongOrNull() ?: 0L }, label = { Text("From epoch millis") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(to.takeIf { it > 0 }?.toString() ?: "", { to = it.toLongOrNull() ?: 0L }, label = { Text("To epoch millis") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pkg ?: "", { pkg = it.ifBlank { null } }, label = { Text("Package filter") }, modifier = Modifier.fillMaxWidth())
                Text("Known packages: ${packages.joinToString()}")

                var expandedNotificationId by remember { mutableStateOf<Long?>(null) }
                val swipeMode = vm.swipeActionMode()

                LazyColumn {
                    items(notifications, key = { it.id }) { item ->
                        val isExpanded = expandedNotificationId == item.id
                        val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                            positionalThreshold = { distance -> distance * 0.45f },
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart && swipeMode == SwipeActionMode.SWIPE_IMMEDIATE_DELETE) {
                                    vm.deleteNotification(item)
                                    scope.launch {
                                        val result = snackbar.showSnackbar("Deleted", actionLabel = "Undo")
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            vm.restoreNotification(item)
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                if (swipeMode == SwipeActionMode.SWIPE_REVEAL_DELETE) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(onClick = {
                                            vm.deleteNotification(item)
                                            scope.launch {
                                                val result = snackbar.showSnackbar("Deleted", actionLabel = "Undo")
                                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                    vm.restoreNotification(item)
                                                }
                                            }
                                        }) { Text("Delete") }
                                    }
                                }
                            }
                        ) {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                expandedNotificationId = if (isExpanded) null else item.id
                            }) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.packageName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (isExpanded) "Collapse" else "Expand", modifier = Modifier.rotate(if (isExpanded) 180f else 0f))
                                    }
                                    if (isExpanded) {
                                        SelectionContainer {
                                            Column {
                                                Text(item.title ?: "(no title)")
                                                Text(item.text ?: "")
                                            }
                                        }
                                    } else {
                                        Text(item.title ?: "(no title)", maxLines = 1)
                                        Text(item.text ?: "", maxLines = 1)
                                    }
                                    if (isExpanded) {
                                        Text("Captured: ${formatter.format(Date(item.capturedAt))}")
                                        Text("App: ${item.appName ?: item.packageName}")
                                        TextButton(onClick = {
                                            when (vm.openSavedNotification(item)) {
                                                OpenPath.APP_LAUNCH_FALLBACK -> scope.launch {
                                                    snackbar.showSnackbar("Opened app (original notification link no longer available).")
                                                }
                                                else -> Unit
                                            }
                                        }) { Text("Open source app") }
                                    }
                                    if (item.handled) Text("Handled", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            } else if (tab == 1) {
                LazyColumn {
                    items(rules, key = { it.id }) { rule ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(rule.name, fontWeight = FontWeight.Bold)
                                Text("Type: ${rule.type}")
                                Text("Active: ${rule.isActive}")
                                Text("Mode: ${rule.appFilterMode} / ${rule.selectedPackagesCsv}")
                                Text("Days: ${rule.weekendDaysCsv}")
                                Text("Range: ${rule.startDateTimeMillis ?: "-"} to ${rule.endDateTimeMillis ?: "-"}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { editingRule = rule }) { Icon(Icons.Default.Edit, null) }
                                    IconButton(onClick = { vm.toggleRule(rule, !rule.isActive) }) { Icon(Icons.Default.Done, null) }
                                    IconButton(onClick = { vm.deleteRule(rule.id) }) { Icon(Icons.Default.Delete, null) }
                                }
                            }
                        }
                    }
                }
            } else if (tab == 2) {
                var swipeActionMode by remember { mutableStateOf(vm.swipeActionMode()) }

                Text("Capture mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { vm.setCaptureMode(CaptureMode.ONLY_SELECTED_APPS) }, label = { Text("Only selected apps") })
                    AssistChip(onClick = { vm.setCaptureMode(CaptureMode.ALL_APPS) }, label = { Text("All apps") })
                }
                Text("Current mode: ${vm.captureMode().name}")
                Button(onClick = { showSelectApps = true }) { Text("Select apps") }
                Text("Selected: ${selectedApps.size}")

                Text("Swipe action", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            swipeActionMode = SwipeActionMode.SWIPE_IMMEDIATE_DELETE
                            vm.setSwipeActionMode(swipeActionMode)
                        },
                        label = { Text("Immediate delete") }
                    )
                    AssistChip(
                        onClick = {
                            swipeActionMode = SwipeActionMode.SWIPE_REVEAL_DELETE
                            vm.setSwipeActionMode(swipeActionMode)
                        },
                        label = { Text("Reveal delete") }
                    )
                }
                Text("Current swipe mode: ${swipeActionMode.name}")
            } else {
                HealthCheckTab(ruleCount = rules.size, selectedAppsCount = selectedApps.size)
            }
        }

        if (showRuleDialog) AddRuleDialog(initialRule = null, onDismiss = { showRuleDialog = false }) { vm.saveRule(it); showRuleDialog = false }
        editingRule?.let { rule ->
            AddRuleDialog(initialRule = rule, onDismiss = { editingRule = null }) { vm.saveRule(it.copy(id = rule.id)); editingRule = null }
        }
        if (showSelectApps) {
            SelectAppsDialog(vm = vm, selectedApps = selectedApps, onDismiss = { showSelectApps = false })
        }
    }
}

@Composable
private fun HealthCheckTab(ruleCount: Int, selectedAppsCount: Int) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationAccessEnabled by remember { mutableStateOf(hasNotificationAccess(context)) }
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var notificationsPermissionGranted by remember { mutableStateOf(hasPostNotificationsPermission(context)) }
    var backgroundRestricted by remember { mutableStateOf(isBackgroundRestricted(context)) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val tips = remember { manufacturerTips() }
    val requestNotificationsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsPermissionGranted = hasPostNotificationsPermission(context)
    }

    fun refreshState() {
        notificationAccessEnabled = hasNotificationAccess(context)
        batteryExempt = isIgnoringBatteryOptimizations(context)
        notificationsPermissionGranted = hasPostNotificationsPermission(context)
        backgroundRestricted = isBackgroundRestricted(context)
    }

    fun diagnosticsText(): String {
        return formatDiagnosticsReport(
            listenerEnabled = notificationAccessEnabled,
            batteryExempt = batteryExempt,
            postNotificationsGranted = notificationsPermissionGranted,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            sdk = Build.VERSION.SDK_INT,
            ruleCount = ruleCount,
            selectedAppsCount = selectedAppsCount
        )
    }

    fun shareDiagnostics() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NotifyVault diagnostics")
            putExtra(Intent.EXTRA_TEXT, diagnosticsText())
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            actionMessage = "Unable to open share menu on this device."
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Health / Fix setup", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("A) Notification Listener", fontWeight = FontWeight.Bold)
                HealthStatusChip(ok = notificationAccessEnabled)
                Button(onClick = {
                    if (!openNotificationListenerSettings(context)) {
                        actionMessage = "Could not open Notification Listener settings. Opened App info instead."
                    }
                }) { Text("Open notification listener settings") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("B) Notifications permission (Android 13+)", fontWeight = FontWeight.Bold)
                HealthStatusChip(ok = notificationsPermissionGranted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openAppNotificationSettings(context)
                        }
                    }) { Text("Request permission") }
                    TextButton(onClick = {
                        openAppNotificationSettings(context)
                    }) { Text("Open app notification settings") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("C) Battery optimization", fontWeight = FontWeight.Bold)
                HealthStatusChip(ok = batteryExempt)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!requestIgnoreBatteryOptimization(context)) {
                            actionMessage = "Direct exemption screen unavailable. Opened fallback settings."
                        }
                    }) { Text("Request ignore optimization") }
                    TextButton(onClick = {
                        if (!openBatteryOptimizationSettings(context)) {
                            actionMessage = "Battery optimization list unavailable. Opened App info instead."
                        }
                    }) { Text("Open optimization list") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("D) App details", fontWeight = FontWeight.Bold)
                HealthStatusChip(ok = !backgroundRestricted, okLabel = "Not restricted", warnLabel = "Restricted on this device")
                Text("Always available fallback.")
                Button(onClick = {
                    openAppInfoSettings(context)
                }) { Text("Open app details") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("OEM tips", fontWeight = FontWeight.Bold)
                Text(tips.title)
                tips.steps.forEach { step -> Text("• $step") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (openManufacturerSettings(context)) {
                            actionMessage = "Opened manufacturer/app settings."
                        } else {
                            actionMessage = "Opened App info fallback."
                        }
                    }) { Text("Try open OEM settings") }
                    TextButton(onClick = {
                        val text = (listOf(tips.title) + tips.steps.map { "- $it" }).joinToString("\n")
                        val clipboard = ContextCompat.getSystemService(context, android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("NotifyVault OEM instructions", text))
                        actionMessage = "Instructions copied."
                    }) { Text("Copy instructions") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quick diagnostics", fontWeight = FontWeight.Bold)
                Text("Create a text snapshot and share with support.")
                Button(onClick = { shareDiagnostics() }) { Text("Share diagnostics") }
            }
        }

        actionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}


@Composable
private fun HealthStatusChip(ok: Boolean, okLabel: String = "OK", warnLabel: String = "Needs attention") {
    AssistChip(onClick = {}, enabled = false, label = {
        Text(if (ok) "🟢 $okLabel" else "🔴 $warnLabel")
    })
}

@Composable
private fun SelectAppsDialog(vm: MainViewModel, selectedApps: List<String>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val apps = remember(query) { vm.loadLaunchableApps(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select apps") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
                LazyColumn {
                    items(apps, key = { it.packageName }) { app ->
                        val checked = app.packageName in selectedApps
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { vm.setAppSelected(app.packageName, !checked) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val bitmap = app.icon?.toBitmap()
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label)
                                Text(app.packageName)
                            }
                            Checkbox(checked = checked, onCheckedChange = { vm.setAppSelected(app.packageName, it) })
                        }
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

@Composable
private fun AddRuleDialog(initialRule: RuleEntity?, onDismiss: () -> Unit, onSave: (RuleEntity) -> Unit) {
    var name by remember { mutableStateOf(initialRule?.name ?: "Weekend") }
    var type by remember { mutableStateOf(initialRule?.type ?: RuleType.WEEKEND_REPEAT) }
    var active by remember { mutableStateOf(initialRule?.isActive ?: true) }
    var filterMode by remember { mutableStateOf(initialRule?.appFilterMode ?: AppFilterMode.ALL_EXCEPT) }
    var packages by remember { mutableStateOf(initialRule?.selectedPackagesCsv ?: "") }
    var start by remember { mutableStateOf(initialRule?.startDateTimeMillis?.toString() ?: "") }
    var end by remember { mutableStateOf(initialRule?.endDateTimeMillis?.toString() ?: "") }
    var days by remember { mutableStateOf(initialRule?.weekendDaysCsv ?: "6,7") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "Add Rule" else "Edit Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(type.name, {
                    type = if (it == RuleType.DATE_RANGE.name) RuleType.DATE_RANGE else RuleType.WEEKEND_REPEAT
                }, label = { Text("Type: DATE_RANGE/WEEKEND_REPEAT") })
                OutlinedTextField(filterMode.name, { filterMode = if (it == AppFilterMode.ONLY_SELECTED.name) AppFilterMode.ONLY_SELECTED else AppFilterMode.ALL_EXCEPT }, label = { Text("Filter mode") })
                OutlinedTextField(packages, { packages = it }, label = { Text("Selected packages CSV") })
                OutlinedTextField(start, { start = it }, label = { Text("Start millis") })
                OutlinedTextField(end, { end = it }, label = { Text("End millis") })
                OutlinedTextField(days, { days = it }, label = { Text("Weekend days CSV") })
                TextButton(onClick = { active = !active }) { Text("Active: $active") }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    RuleEntity(
                        name = name,
                        type = type,
                        isActive = active,
                        appFilterMode = filterMode,
                        selectedPackagesCsv = packages,
                        startDateTimeMillis = start.toLongOrNull(),
                        endDateTimeMillis = end.toLongOrNull(),
                        weekendDaysCsv = days
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
