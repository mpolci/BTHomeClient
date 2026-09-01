package io.github.mpolci.bthomeclient

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mpolci.bthomeclient.ui.main.CapabilityFacts
import io.github.mpolci.bthomeclient.ui.main.MainScanViewModel
import io.github.mpolci.bthomeclient.ui.main.MainScreen
import io.github.mpolci.bthomeclient.ui.main.resolveCapability
import io.github.mpolci.bthomeclient.app.AppRoutes
import io.github.mpolci.bthomeclient.app.AppContainer
import io.github.mpolci.bthomeclient.app.simpleViewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mpolci.bthomeclient.ui.registration.RegistrationScreen
import io.github.mpolci.bthomeclient.ui.registration.RegistrationViewModel
import io.github.mpolci.bthomeclient.ui.detail.DetailScreen
import io.github.mpolci.bthomeclient.ui.detail.DetailViewModel
import io.github.mpolci.bthomeclient.ui.settings.SettingsScreen
import io.github.mpolci.bthomeclient.ui.settings.SettingsViewModel
import io.github.mpolci.bthomeclient.ui.history.HistoryPlaceholderScreen
import kotlinx.coroutines.flow.collect

@Composable
fun BTHomeClientApp() {
    val context = LocalContext.current
    val app = context.applicationContext as BTHomeApplication
    val model: MainScanViewModel = viewModel(
        factory = simpleViewModelFactory { app.appContainer.createMainScanViewModel() },
    )
    var requested by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        val missing = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        val rationale = (context as? android.app.Activity)?.let { activity -> missing.map(activity::shouldShowRequestPermissionRationale) }.orEmpty()
        val enabled = try { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true } catch (_: SecurityException) { false }
        model.refreshCapability(resolveCapability(CapabilityFacts(context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE), rationale, requested, enabled)))
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { requested = true; refresh() }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val navController = rememberNavController()
    NavHost(navController, AppRoutes.Main) {
        composable(AppRoutes.Main) {
            LifecycleStartEffect(Unit) { model.screenStarted(); onStopOrDispose { model.screenStopped() } }
            MainScreen(state, { requested = true; permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) }, { try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { refresh() } }, { try { enableBluetooth.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: Exception) { refresh() } }, { refresh(); model.retry() }, { navController.navigate(AppRoutes.Settings) }, { navController.navigate(AppRoutes.registration(it.address)) }, { navController.navigate(AppRoutes.detail(it.address)) })
        }
        composable(AppRoutes.Registration) { entry ->
            val address = AppRoutes.address(entry.arguments?.getString("address"))
            if (address == null) InvalidRoute { navController.popBackStack() }
            else RegistrationRoute(address, app.appContainer, { navController.popBackStack() })
        }
        composable(AppRoutes.Detail) { entry ->
            val address = AppRoutes.address(entry.arguments?.getString("address"))
            if (address == null) InvalidRoute { navController.popBackStack() }
            else DetailRoute(address, app.appContainer, { navController.popBackStack() }, { navController.navigate(AppRoutes.history(address)) })
        }
        composable(AppRoutes.Settings) { SettingsRoute(app.appContainer) { navController.popBackStack() } }
        composable(AppRoutes.History) { entry ->
            val address = AppRoutes.address(entry.arguments?.getString("address"))
            if (address == null) InvalidRoute { navController.popBackStack() }
            else HistoryPlaceholderScreen(address.value) { navController.popBackStack() }
        }
    }
}

@Composable
private fun InvalidRoute(onBack: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text("Invalid sensor route.")
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun RegistrationRoute(
    address: io.github.mpolci.bthomeclient.domain.model.SensorAddress,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: RegistrationViewModel = viewModel(
        key = "registration-${address.value}",
        factory = simpleViewModelFactory { container.createRegistrationViewModel(address) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.edit(imageUri = it.toString())
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Saved) onBack()
        }
    }
    RegistrationScreen(
        state = state,
        onBack = onBack,
        onNameChange = { viewModel.edit(displayName = it) },
        onRoomChange = { viewModel.edit(room = it) },
        onChooseImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveImage = { viewModel.edit(imageUri = null) },
        onSave = viewModel::save,
    )
}

@Composable
private fun DetailRoute(
    address: io.github.mpolci.bthomeclient.domain.model.SensorAddress,
    container: AppContainer,
    onBack: () -> Unit,
    onHistory: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = "detail-${address.value}",
        factory = simpleViewModelFactory { container.createDetailViewModel(address) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.edit(imageUri = it.toString())
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Saved ||
                event is io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Deleted) {
                onBack()
            }
        }
    }
    DetailScreen(
        state = state,
        onBack = onBack,
        onNameChange = { viewModel.edit(name = it) },
        onRoomChange = { viewModel.edit(room = it) },
        onChooseImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveImage = { viewModel.edit(imageUri = null) },
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onHistory = onHistory,
    )
}

@Composable
private fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        key = "settings",
        factory = simpleViewModelFactory { container.createSettingsViewModel() },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is io.github.mpolci.bthomeclient.ui.editor.EditorEvent.Saved) onBack()
        }
    }
    SettingsScreen(
        state = state,
        intervals = SettingsViewModel.intervals,
        retentions = SettingsViewModel.retentions,
        onBack = onBack,
        onInterval = { viewModel.edit(interval = it) },
        onRetention = { viewModel.edit(retention = it) },
        onSave = viewModel::save,
    )
}
