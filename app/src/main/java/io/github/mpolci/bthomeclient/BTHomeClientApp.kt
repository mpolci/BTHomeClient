package io.github.mpolci.bthomeclient

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.mpolci.bthomeclient.ui.history.HistorySelection
import io.github.mpolci.bthomeclient.ui.main.ScanCapability
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import android.content.pm.ActivityInfo
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
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.mpolci.bthomeclient.ui.detail.DetailScreen
import io.github.mpolci.bthomeclient.ui.detail.DetailViewModel
import io.github.mpolci.bthomeclient.ui.settings.SettingsScreen
import io.github.mpolci.bthomeclient.ui.settings.SettingsViewModel
import io.github.mpolci.bthomeclient.ui.history.HistoryScreen
import io.github.mpolci.bthomeclient.ui.history.HistoryViewModel
import kotlinx.coroutines.flow.collect

@Composable
fun BTHomeClientApp() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val app = context.applicationContext as BTHomeApplication
    val model: MainScanViewModel = viewModel(
        factory = simpleViewModelFactory { app.appContainer.createMainScanViewModel() },
    )
    var requested by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        val missing = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        val rationale = activity?.let { activity -> missing.map(activity::shouldShowRequestPermissionRationale) }.orEmpty()
        val enabled = try { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true } catch (_: SecurityException) { false }
        model.refreshCapability(resolveCapability(CapabilityFacts(context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE), rationale, requested, enabled)))
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { requested = true; refresh() }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    var historySelection by remember { mutableStateOf<HistorySelection?>(null) }
    val inHistory = currentRoute == AppRoutes.History || currentRoute == AppRoutes.HistoryOverview
    var historyPreviousOrientation by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentRoute) {
        // Keep the effect key and route classification from the same composition during navigation restoration.
        val route = currentRoute ?: return@LaunchedEffect
        if (inHistory) {
            if (historyPreviousOrientation == null) historyPreviousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = if (route == AppRoutes.HistoryOverview) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            app.appContainer.historySession.end()
            historyPreviousOrientation?.let { previous ->
                historyPreviousOrientation = null
                activity?.requestedOrientation = previous
            }
        }
    }
    LifecycleStartEffect(inHistory) {
        if (inHistory) app.appContainer.historySession.begin()
        onStopOrDispose {
            if (inHistory && activity?.isChangingConfigurations != true) app.appContainer.historySession.end()
        }
    }
    val openAppSettings: () -> Unit = {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))) }
        refresh()
    }
    val enable: () -> Unit = {
        runCatching { enableBluetooth.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        refresh()
    }
    val resolveHistoryPrerequisite: () -> Boolean = {
        val missing = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            if (state.capability == ScanCapability.PermissionPermanentlyDenied) openAppSettings()
            else { requested = true; permissions.launch(missing.toTypedArray()) }
            false
        } else if (runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrDefault(false) != true) {
            enable(); false
        } else { refresh(); true }
    }
    val scanningRoute = currentRoute == AppRoutes.Main || currentRoute == AppRoutes.Detail
    LifecycleStartEffect(scanningRoute) {
        if (scanningRoute) model.screenStarted()
        onStopOrDispose { if (scanningRoute) model.screenStopped() }
    }
    NavHost(navController, AppRoutes.Main) {
        composable(AppRoutes.Main) {
            MainScreen(state, { requested = true; permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) }, { try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { refresh() } }, { try { enableBluetooth.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: Exception) { refresh() } }, { refresh(); model.retry() }, { navController.navigate(AppRoutes.Settings) }, { navController.navigate(AppRoutes.detail(it.address)) }, { navController.navigate(AppRoutes.detail(it.address)) }, onHistory = { navController.navigate(AppRoutes.HistoryOverview) })
        }
        composable(AppRoutes.Detail) { entry ->
            val address = AppRoutes.address(entry.arguments?.getString("address"))
            if (address == null) InvalidRoute { navController.popBackStack() }
            else DetailRoute(address, app.appContainer, { navController.popBackStack() }, { historySelection = null; navController.navigate(AppRoutes.history(address)) })
        }
        composable(AppRoutes.Settings) { SettingsRoute(app.appContainer) { navController.popBackStack() } }
        composable(AppRoutes.HistoryOverview) {
            HistoryRoute(null, app.appContainer, null, state.capability, resolveHistoryPrerequisite, { navController.popBackStack() }) { address, selection ->
                historySelection = selection
                navController.navigate(AppRoutes.history(address))
            }
        }
        composable(AppRoutes.History) { entry ->
            val address = AppRoutes.address(entry.arguments?.getString("address"))
            if (address == null) InvalidRoute { navController.popBackStack() }
            else HistoryRoute(address, app.appContainer, historySelection, state.capability, resolveHistoryPrerequisite, { navController.popBackStack() }) { _, _ -> }
        }
    }
}

@Composable
private fun HistoryRoute(
    address: SensorAddress?,
    container: AppContainer,
    initialSelection: HistorySelection?,
    capability: ScanCapability,
    onResolvePrerequisite: () -> Boolean,
    onBack: () -> Unit,
    onSensor: (SensorAddress, HistorySelection) -> Unit,
) {
    val model: HistoryViewModel = viewModel(key = "history-${address?.value ?: "overview"}", factory = simpleViewModelFactory { container.createHistoryViewModel(address, initialSelection) })
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.screenStarted()
        onStopOrDispose { model.screenStopped() }
    }
    var previousCapability by remember { mutableStateOf(capability) }
    LaunchedEffect(capability) {
        if (capability == ScanCapability.Ready && previousCapability != ScanCapability.Ready) model.retry()
        previousCapability = capability
    }
    HistoryScreen(state, onBack, { model.retry(it) }, { onSensor(it, model.selection()) }, model::setMeasurement, model::setViewport, model::setComparison, model::showAllHistory, capability, { if (onResolvePrerequisite()) model.retry() })
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
    var imageRequestIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var imageError by rememberSaveable(state.sensor?.registeredSensor?.registrationId) { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val requestIdentity = imageRequestIdentity
        imageRequestIdentity = null
        uri?.takeIf { requestIdentity != null && requestIdentity == viewModel.state.value.sensor?.registeredSensor?.registrationId }?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess { _ -> viewModel.saveImage(it.toString()) }
                .onFailure { imageError = "Unable to keep access to this photo. Your previous photo is unchanged. Choose a photo again." }
        }
    }
    DetailScreen(
        state = state.copy(error = imageError ?: state.error),
        onBack = onBack,
        onSaveName = viewModel::saveName,
        onSaveRoom = viewModel::saveRoom,
        onChooseImage = {
            imageError = null
            imageRequestIdentity = state.sensor?.registeredSensor?.registrationId
            runCatching { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .onFailure { imageRequestIdentity = null; imageError = "Unable to open photo selection. Try again." }
        },
        onRemoveImage = { imageError = null; viewModel.saveImage(null) },
        onRegister = viewModel::register,
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
        retentions = SettingsViewModel.retentions,
        onBack = onBack,
        onRetention = { viewModel.edit(retention = it) },
        onSave = viewModel::save,
        onThemeMode = { viewModel.edit(themeMode = it) },
    )
}
