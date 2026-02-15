package com.notifyvault.weekendinbox

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notifyvault.weekendinbox.data.AppContainer
import com.notifyvault.weekendinbox.data.AppFilterMode
import com.notifyvault.weekendinbox.data.CapturedNotificationEntity
import com.notifyvault.weekendinbox.data.RuleEntity
import com.notifyvault.weekendinbox.data.RuleType
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

class MainViewModel(app: NotifyVaultApp, private val container: AppContainer) : AndroidViewModel(app) {
    private val selectedPackage = MutableStateFlow<String?>(null)
    private val fromDate = MutableStateFlow<Long?>(null)
    private val toDate = MutableStateFlow<Long?>(null)
    private val search = MutableStateFlow("")

    val rules = container.ruleRepository.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val packages = container.notificationRepository.observeKnownPackages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<CapturedNotificationEntity>> = combine(selectedPackage, fromDate, toDate, search) { pkg, from, to, q ->
        FilterState(pkg, from, to, q)
    }.flatMapLatest {
        container.notificationRepository.observeVault(it.pkg, it.from, it.to, it.search)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onboardingDone: Boolean get() = container.prefs.hasCompletedOnboarding()

    fun isAccessEnabled(): Boolean = hasNotificationAccess(getApplication())
    fun canCapture(): Boolean = container.prefs.canCaptureNewNotifications()
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
    var search by remember { mutableStateOf("") }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(0L) }
    var pkg by remember { mutableStateOf<String?>(null) }
    val rules by vm.rules.collectAsState()
    val notifications by vm.notifications.collectAsState()
    val packages by vm.packages.collectAsState()
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(search) { vm.setSearch(search) }
    LaunchedEffect(from) { vm.setFromDate(from.takeIf { it > 0 }) }
    LaunchedEffect(to) { vm.setToDate(to.takeIf { it > 0 }) }
    LaunchedEffect(pkg) { vm.setPackageFilter(pkg) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (tab == 0) "Vault" else "Rules") }, actions = {
                if (!vm.isAccessEnabled()) TextButton(onClick = openAccessSettings) { Text("Enable access") }
            })
        },
        floatingActionButton = {
            if (tab == 1) FloatingActionButton(onClick = { showRuleDialog = true }) { Icon(Icons.Default.Add, null) }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(12.dp)) {
            if (!vm.onboardingDone) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Welcome to NotifyVault", fontWeight = FontWeight.Bold)
                        Text("Grant notification access so app can capture notifications only during your rules.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = openAccessSettings) { Text("Open settings") }
                            TextButton(onClick = { vm.completeOnboarding() }) { Text("Continue") }
                        }
                    }
                }
            }
            if (!vm.isAccessEnabled()) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("Notification access is disabled.", modifier = Modifier.padding(12.dp))
                }
            }
            if (!vm.canCapture()) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Capture blocked: trial expired")
                        Button(onClick = { vm.setPro(true) }) { Text("Unlock Pro (stub)") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { tab = 0 }, label = { Text("Vault") })
                AssistChip(onClick = { tab = 1 }, label = { Text("Rules") })
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
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
            } else {
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
            }
        }

        if (showRuleDialog) AddRuleDialog(initialRule = null, onDismiss = { showRuleDialog = false }) { vm.saveRule(it); showRuleDialog = false }
        editingRule?.let { rule ->
            AddRuleDialog(initialRule = rule, onDismiss = { editingRule = null }) { vm.saveRule(it.copy(id = rule.id)); editingRule = null }
        }
    }
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
