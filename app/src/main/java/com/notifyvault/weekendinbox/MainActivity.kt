package com.notifyvault.weekendinbox

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notifyvault.weekendinbox.data.AppContainer
import com.notifyvault.weekendinbox.data.AppFilterMode
import com.notifyvault.weekendinbox.data.CaptureMode
import com.notifyvault.weekendinbox.data.CapturedNotificationEntity
import com.notifyvault.weekendinbox.data.RuleEntity
import com.notifyvault.weekendinbox.data.RuleType
import com.notifyvault.weekendinbox.domain.OpenPath
import com.notifyvault.weekendinbox.service.VaultNotificationListenerService
import com.notifyvault.weekendinbox.util.hasNotificationAccess
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
    fun deleteNotification(id: Long) = viewModelScope.launch { container.notificationRepository.delete(id) }

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
            TopAppBar(title = { Text(if (tab == 0) "Vault" else if (tab == 1) "Rules" else "Settings") }, actions = {
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
            }
            if (tab == 0) {
                OutlinedTextField(search, { search = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(from.takeIf { it > 0 }?.toString() ?: "", { from = it.toLongOrNull() ?: 0L }, label = { Text("From epoch millis") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(to.takeIf { it > 0 }?.toString() ?: "", { to = it.toLongOrNull() ?: 0L }, label = { Text("To epoch millis") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pkg ?: "", { pkg = it.ifBlank { null } }, label = { Text("Package filter") }, modifier = Modifier.fillMaxWidth())
                Text("Known packages: ${packages.joinToString()}")

                LazyColumn {
                    items(notifications, key = { it.id }) { item ->
                        val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(confirmValueChange = {
                            when (it) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    vm.markHandled(item.id)
                                    scope.launch { snackbar.showSnackbar("Marked handled") }
                                    true
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    vm.deleteNotification(item.id)
                                    scope.launch { snackbar.showSnackbar("Deleted") }
                                    true
                                }
                                else -> false
                            }
                        })
                        SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                when (vm.openSavedNotification(item)) {
                                    OpenPath.APP_LAUNCH_FALLBACK -> scope.launch {
                                        snackbar.showSnackbar("Opened app (original notification link no longer available).")
                                    }
                                    else -> Unit
                                }
                            }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.packageName, fontWeight = FontWeight.Bold)
                                    Text(item.title ?: "(no title)")
                                    Text(item.text ?: "")
                                    Text(formatter.format(Date(item.capturedAt)))
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
            } else {
                Text("Capture mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { vm.setCaptureMode(CaptureMode.ONLY_SELECTED_APPS) }, label = { Text("Only selected apps") })
                    AssistChip(onClick = { vm.setCaptureMode(CaptureMode.ALL_APPS) }, label = { Text("All apps") })
                }
                Text("Current mode: ${vm.captureMode().name}")
                Button(onClick = { showSelectApps = true }) { Text("Select apps") }
                Text("Selected: ${selectedApps.size}")
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
