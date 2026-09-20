package dev.lightcopy.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import android.os.Bundle
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContract
import dev.lightcopy.browser.browser.BrowserController
import dev.lightcopy.browser.browser.PageUpdate
import dev.lightcopy.browser.browser.WebViewHost
import dev.lightcopy.browser.browser.UserAgentMode
import dev.lightcopy.browser.copy.AndroidClipboard
import dev.lightcopy.browser.copy.ExtractionKind
import dev.lightcopy.browser.copy.ExtractionCopyFormatter
import dev.lightcopy.browser.console.ConsoleEntry
import dev.lightcopy.browser.console.ConsoleFilter
import dev.lightcopy.browser.console.ConsoleLevel
import dev.lightcopy.browser.console.ConsoleLogFormatter
import dev.lightcopy.browser.console.TabConsoleLogs
import dev.lightcopy.browser.fullscreen.ExitControlPosition
import dev.lightcopy.browser.fullscreen.FullscreenPreferences
import dev.lightcopy.browser.fullscreen.LocalFullscreenPreferencesStore
import dev.lightcopy.browser.fullscreen.toPercentLabel
import dev.lightcopy.browser.pageinfo.*
import dev.lightcopy.browser.network.TabNetworkLogs
import dev.lightcopy.browser.tabs.IncognitoPolicy
import dev.lightcopy.browser.tabs.MAX_TABS
import dev.lightcopy.browser.tabs.TabRegistry
import dev.lightcopy.browser.copy.PageExportKind
import dev.lightcopy.browser.copy.ExportFileNames
import java.io.OutputStreamWriter
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.lightcopy.browser.data.*
import dev.lightcopy.browser.downloads.AndroidDownloader
import dev.lightcopy.browser.share.sharePage
import dev.lightcopy.browser.qr.QrScanner
import dev.lightcopy.browser.settings.*

@Composable
fun BrowserApp(
    controller: BrowserController,
    restoredState: Bundle?,
    onFullscreenChanged: (Boolean) -> Unit = {},
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
) {
    val hostActivity = LocalContext.current as? Activity
    val applicationContext = LocalContext.current.applicationContext
    val fullscreenStore = remember(applicationContext) { LocalFullscreenPreferencesStore(applicationContext) }
    val appSettingsStore = remember(applicationContext) { LocalAppSettingsStore(applicationContext) }
    val savedAppSettings = remember(applicationContext) { appSettingsStore.load() }
    val startingUrl = remember(savedAppSettings) { normalizeAddress(savedAppSettings.startingPageUrl, savedAppSettings.searchEngine) }
    val tabs = remember { TabRegistry() }
    val tabStates = remember { mutableMapOf<String, BrowserShellState>() }
    val webStates = remember { mutableMapOf<String, Bundle>() }
    var state by remember {
        mutableStateOf(BrowserShellState(
            address = savedAppSettings.startingPageUrl,
            currentUrl = startingUrl,
            fullscreenPreferences = fullscreenStore.load(),
            appSettings = savedAppSettings,
            appSettingsDraft = savedAppSettings,
            consoleTimestamps = savedAppSettings.consoleTimestamps,
            consoleFilter = runCatching { ConsoleFilter.valueOf(savedAppSettings.consoleLevel) }.getOrDefault(ConsoleFilter.ALL),
            developerSettings = defaultDeveloperSettings(savedAppSettings),
            developerDraft = defaultDeveloperSettings(savedAppSettings),
        ).copy(tabs = tabs.all(), activeTabId = tabs.activeId))
    }
    val clipboard = remember(applicationContext) { AndroidClipboard(applicationContext) }
    val consoleLogs = remember { TabConsoleLogs() }
    val networkLogs = remember { TabNetworkLogs() }
    val records = remember(applicationContext) { FileLocalRecords(applicationContext) }
    val downloader = remember(applicationContext) { AndroidDownloader(applicationContext) }
    var pendingExport by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val result = runCatching {
                applicationContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
                    ?: error("Unable to open destination")
            }
            state = reduce(state, BrowserAction.ConsoleExportCompleted(
                if (result.isSuccess) "Console exported" else "Export failed",
            ))
        }
    }
    var pendingSettingsJson by remember { mutableStateOf("") }
    val settingsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val result = uri != null && runCatching {
            applicationContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingSettingsJson) }
                ?: error("Unable to open destination")
        }.isSuccess
        state = state.copy(feedback = if (result) "Settings exported" else "Settings export cancelled or failed")
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val result = runCatching {
            requireNotNull(uri) { "No settings file selected." }
            applicationContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { AppSettingsJson.decode(it.readText()).getOrThrow() }
                ?: error("Unable to open settings file.")
        }
        state = reduce(state, BrowserAction.SettingsImported(result.getOrNull(), result.exceptionOrNull()?.message))
    }
    val downloadFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { applicationContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            state = reduce(state, BrowserAction.DownloadFolderSelected(uri.toString()))
        }
    }
    var pendingPageText by remember { mutableStateOf("") }
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    fun exportFolder(): android.net.Uri? = state.appSettings.downloadFolderUri
        .takeIf { it.startsWith("content://") }
        ?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
    fun writePage(uri: android.net.Uri?): Boolean = uri != null && runCatching {
        applicationContext.contentResolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { it.write(pendingPageText) }
        } ?: error("Unable to open destination")
    }.isSuccess
    val htmlLauncher = rememberLauncherForActivityResult(ExportDocumentContract("text/html")) { uri ->
        state = reduce(state, BrowserAction.PageSaved(writePage(uri)))
    }
    val textLauncher = rememberLauncherForActivityResult(ExportDocumentContract("text/plain")) { uri ->
        state = reduce(state, BrowserAction.PageSaved(writePage(uri)))
    }
    val screenshotLauncher = rememberLauncherForActivityResult(ExportDocumentContract("image/png")) { uri ->
        val bitmap = pendingBitmap
        val success = uri != null && bitmap != null && runCatching {
            applicationContext.contentResolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } ?: error("Unable to open destination")
        }.isSuccess
        pendingBitmap = null
        state = reduce(state, BrowserAction.PageSaved(success))
    }
    var pendingArchive by remember { mutableStateOf<File?>(null) }
    val archiveLauncher = rememberLauncherForActivityResult(ExportDocumentContract("multipart/related")) { uri ->
        val archive = pendingArchive
        val success = uri != null && archive != null && runCatching {
            applicationContext.contentResolver.openOutputStream(uri)?.use { output -> archive.inputStream().use { it.copyTo(output) } }
                ?: error("Unable to open destination")
        }.isSuccess
        archive?.delete()
        pendingArchive = null
        state = reduce(state, BrowserAction.PageSaved(success))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state = if (granted) state.copy(isQrScannerOpen = true, showCameraRationale = false)
        else state.copy(showCameraRationale = false, feedback = "Camera permission denied")
    }
    fun dispatch(action: BrowserAction) {
        when (action) {
            BrowserAction.ToggleSelectionMode -> { state = reduce(state, action); appSettingsStore.save(state.appSettings) }
            BrowserAction.SubmitAddress -> {
                state = reduce(state, action)
                controller.load(state.currentUrl, state.appSettings.doNotTrack)
            }
            BrowserAction.StopLoading -> { controller.stop(); state = reduce(state, action) }
            BrowserAction.NavigateBack -> controller.back()
            BrowserAction.NavigateForward -> controller.forward()
            BrowserAction.Reload, BrowserAction.Retry -> controller.reload()
            BrowserAction.OpenFind -> state = reduce(state, action)
            BrowserAction.CloseFind -> { controller.clearFind(); state = reduce(state, action) }
            is BrowserAction.EditFind -> {
                state = reduce(state, action)
                controller.find(action.value)
            }
            is BrowserAction.FindNext -> controller.findNext(action.forward)
            BrowserAction.OpenReader -> {
                state = reduce(state, action)
                controller.reader { result -> state = reduce(state, BrowserAction.ReaderCompleted(result.content, result.error)) }
            }
            BrowserAction.TogglePageDark -> {
                state = reduce(state, action)
                controller.setPageDark(state.isPageDark)
            }
            BrowserAction.CapturePage -> {
                state = reduce(state, action)
                controller.captureFullPage { result ->
                    if (result.bitmap != null) {
                        pendingBitmap = result.bitmap
                        screenshotLauncher.launch(ExportDocumentInput(
                            ExportFileNames.page(state.title, PageExportKind.RenderedHtml).substringBeforeLast('.') + ".png",
                            exportFolder(),
                        ))
                    } else state = state.copy(feedback = result.error ?: "Capture failed")
                }
            }
            is BrowserAction.OpenLibrary -> state = reduce(state, action).copy(history = records.history(), bookmarks = records.bookmarks())
            is BrowserAction.OpenRecord -> { state = reduce(state, action); controller.load(action.url, state.appSettings.doNotTrack) }
            BrowserAction.ClearHistory -> { records.clearHistory(); state = reduce(state, action) }
            BrowserAction.ToggleBookmark -> {
                val incognito = tabs.active().incognito
                if (incognito) state = state.copy(feedback = "Bookmarks are unavailable in private tabs")
                else {
                    val added = records.toggleBookmark(state.currentUrl, state.title, false)
                    state = state.copy(bookmarks = records.bookmarks(), feedback = if (added) "Bookmark added" else "Bookmark removed")
                }
            }
            BrowserAction.SharePage -> state = reduce(state, action).copy(feedback = if (sharePage(applicationContext, state.title, state.currentUrl)) null else "This page cannot be shared")
            BrowserAction.OpenQr -> {
                state = reduce(state, action)
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) state = state.copy(isQrScannerOpen = true)
                else state = state.copy(showCameraRationale = true)
            }
            BrowserAction.ConfirmCameraPermission -> { state = reduce(state, action); cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            is BrowserAction.QrScanned -> { state = reduce(state, action); controller.load(action.url) }
            BrowserAction.ConfirmDownload -> {
                val input = state.pendingDownload
                state = state.copy(pendingDownload = null)
                if (input != null) downloader.enqueue(input).fold(
                    onSuccess = { state = state.copy(feedback = "Download started") },
                    onFailure = { state = state.copy(feedback = it.message ?: "Download rejected") },
                )
            }
            is BrowserAction.SavePage -> {
                state = reduce(state, action)
                controller.extract(action.kind.extractionKind) { result ->
                    if (result.content == null) state = state.copy(feedback = result.error ?: "Save failed")
                    else {
                        pendingPageText = result.content
                        val input = ExportDocumentInput(ExportFileNames.page(state.title, action.kind), exportFolder())
                        if (action.kind.mimeType == "text/html") htmlLauncher.launch(input) else textLauncher.launch(input)
                    }
                }
            }
            BrowserAction.SavePageArchive -> {
                state = reduce(state, action)
                val archive = File(applicationContext.cacheDir, "lightcopy-page-${System.nanoTime()}.mht")
                controller.saveWebArchive(archive.absolutePath) { savedPath ->
                    if (savedPath == null) state = state.copy(feedback = "Page archive failed")
                    else {
                        pendingArchive = File(savedPath)
                        archiveLauncher.launch(ExportDocumentInput(
                            ExportFileNames.safeTitle(state.title) + "-complete.mht",
                            exportFolder(),
                        ))
                    }
                }
            }
            is BrowserAction.CreateTab -> {
                val outgoing = tabs.active()
                val saved = Bundle().also(controller::saveState)
                webStates[outgoing.id] = saved
                tabStates[outgoing.id] = state
                val created = tabs.create(action.incognito || state.appSettings.defaultIncognito)
                if (created == null) state = state.copy(feedback = "Maximum of $MAX_TABS tabs")
                else {
                    if (IncognitoPolicy.requiresSharedDataClear(outgoing.incognito, created.incognito)) controller.clearSharedBrowsingData()
                    state = BrowserShellState(
                        address = state.appSettings.startingPageUrl,
                        currentUrl = normalizeAddress(state.appSettings.startingPageUrl, state.appSettings.searchEngine),
                        fullscreenPreferences = state.fullscreenPreferences,
                        appSettings = state.appSettings,
                        appSettingsDraft = state.appSettings,
                        consoleTimestamps = state.appSettings.consoleTimestamps,
                        consoleFilter = runCatching { ConsoleFilter.valueOf(state.appSettings.consoleLevel) }.getOrDefault(ConsoleFilter.ALL),
                        developerSettings = defaultDeveloperSettings(state.appSettings),
                        developerDraft = defaultDeveloperSettings(state.appSettings),
                        tabs = tabs.all(), activeTabId = created.id,
                        feedback = if (created.incognito) "Incognito tab: shared WebView data cleared" else null,
                    )
                }
            }
            is BrowserAction.SelectTab -> {
                if (action.id != tabs.activeId) {
                    val outgoing = tabs.active()
                    webStates[outgoing.id] = Bundle().also(controller::saveState)
                    tabStates[outgoing.id] = state
                    if (tabs.select(action.id)) {
                        val incoming = tabs.active()
                        if (IncognitoPolicy.requiresSharedDataClear(outgoing.incognito, incoming.incognito)) controller.clearSharedBrowsingData()
                        state = (tabStates[incoming.id] ?: BrowserShellState(fullscreenPreferences = state.fullscreenPreferences)).copy(
                            appSettings = state.appSettings, appSettingsDraft = state.appSettings,
                            tabs = tabs.all(), activeTabId = incoming.id, isTabSwitcherOpen = false,
                        )
                    }
                } else state = state.copy(isTabSwitcherOpen = false)
            }
            is BrowserAction.CloseTab -> {
                val wasActive = action.id == tabs.activeId
                val outgoingMode = tabs.all().firstOrNull { it.id == action.id }?.incognito ?: false
                val closed = tabs.close(action.id)
                if (closed != null) {
                    tabStates.remove(action.id); webStates.remove(action.id)
                    consoleLogs.removeTab(action.id); networkLogs.removeTab(action.id)
                    val incoming = tabs.active()
                    if ((wasActive && IncognitoPolicy.requiresSharedDataClear(outgoingMode, incoming.incognito)) ||
                        (closed.incognito && tabs.all().none { it.incognito })) controller.clearSharedBrowsingData()
                    state = if (wasActive) (tabStates[incoming.id] ?: BrowserShellState(fullscreenPreferences = state.fullscreenPreferences)).copy(
                        appSettings = state.appSettings, appSettingsDraft = state.appSettings,
                        tabs = tabs.all(), activeTabId = incoming.id, isTabSwitcherOpen = true,
                    ) else state.copy(tabs = tabs.all())
                }
            }
            BrowserAction.ClearNetwork -> {
                networkLogs.clear(tabs.activeId)
                state = reduce(state, action)
            }
            BrowserAction.ApplyDeveloperSettings -> {
                val updated = reduce(state, action)
                state = updated
                if (updated.userAgentError == null && updated.developerSettings == updated.developerDraft) {
                    appSettingsStore.save(updated.appSettings)
                    controller.applyDeveloperSettings(updated.developerSettings)
                    controller.reload()
                }
            }
            BrowserAction.ExportSettings -> {
                pendingSettingsJson = AppSettingsJson.encode(state.appSettingsDraft)
                settingsExportLauncher.launch("lightcopy-settings.json")
            }
            BrowserAction.ImportSettings -> settingsImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            BrowserAction.PickDownloadFolder -> downloadFolderLauncher.launch(null)
            BrowserAction.RunConsoleInput -> {
                val code = state.consoleInput.trim()
                if (code.isNotEmpty()) controller.runJavaScript(code) { result ->
                    consoleLogs.append(tabs.activeId, ConsoleLevel.LOG, result, "Console input", 0)
                    state = reduce(state.copy(consoleInput = ""), BrowserAction.ConsoleCaptured(consoleLogs.entries(tabs.activeId)))
                }
            }
            is BrowserAction.SelectTool -> when (action.tool) {
                QuickTool.Source -> dispatch(BrowserAction.RequestExtraction(ExtractionKind.OriginalHtml))
                QuickTool.Text -> dispatch(BrowserAction.RequestExtraction(ExtractionKind.VisibleText))
                QuickTool.Info -> dispatch(BrowserAction.RequestPageInfo)
                else -> state = reduce(state, action)
            }
            BrowserAction.RequestPageInfo -> {
                state = reduce(state, action)
                controller.pageInfo(state.sslState, state.loadTimeMillis) { result ->
                    state = reduce(state, BrowserAction.PageInfoCompleted(result))
                }
            }
            BrowserAction.CopyPageInfo -> state.pageInfo?.info?.let {
                clipboard.copy("Page info", PageInfoFormatter.format(it)); state = reduce(state, action)
            }
            BrowserAction.ConfirmStorageClear -> {
                val kind = state.pendingStorageClear
                val origin = state.pageInfo?.info?.origin
                if (kind != null && origin != null) controller.clearStorage(kind, origin) { result ->
                    state = reduce(state, BrowserAction.StorageClearCompleted(result))
                    if (result.success) dispatch(BrowserAction.RequestPageInfo)
                }
            }
            is BrowserAction.RequestExtraction -> {
                state = reduce(state, action)
                controller.extract(action.kind) { result ->
                    state = reduce(state, BrowserAction.ExtractionCompleted(result))
                }
            }
            BrowserAction.CopyExtraction -> state.extraction?.content?.let { content ->
                val view = state.extraction!!
                clipboard.copy(view.kind.title, ExtractionCopyFormatter.render(content, view.kind, state.appSettings.preferredCopyFormat))
                state = reduce(state, action)
            }
            is BrowserAction.CopyInspectorValue -> {
                clipboard.copy(action.label, action.value)
                state = reduce(state, action)
            }
            BrowserAction.DismissInspector -> {
                controller.clearInspection()
                state = reduce(state, action)
            }
            BrowserAction.ClearConsole -> {
                consoleLogs.clear(tabs.activeId)
                state = reduce(state, action)
            }
            BrowserAction.CopyConsole -> {
                val text = formattedConsole(state)
                if (text.isNotEmpty()) clipboard.copy("JavaScript console", text)
                state = reduce(state, action)
            }
            BrowserAction.ExportConsole -> {
                pendingExport = formattedConsole(state)
                exportLauncher.launch("lightcopy-console.txt")
            }
            else -> state = reduce(state, action)
        }
    }
    LaunchedEffect(state.isFullscreen, state.isLiveConsole) { onFullscreenChanged(state.isFullscreen || state.isLiveConsole) }
    DisposableEffect(Unit) { onDispose { onFullscreenChanged(false) } }
    LaunchedEffect(state.fullscreenPreferences) { fullscreenStore.save(state.fullscreenPreferences) }
    LaunchedEffect(state.appSettings.appearance) {
        hostActivity?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = state.appSettings.appearance == AppearanceMode.Light
                isAppearanceLightNavigationBars = state.appSettings.appearance == AppearanceMode.Light
            }
        }
    }
    LaunchedEffect(state.appSettings.copyVibration) { clipboard.vibrationEnabled = state.appSettings.copyVibration }
    LaunchedEffect(state.appSettings.keepScreenOn) { onKeepScreenOnChanged(state.appSettings.keepScreenOn) }
    DisposableEffect(Unit) { onDispose { onKeepScreenOnChanged(false) } }
    BackHandler(enabled = state.pendingDownload != null || state.isQrScannerOpen || state.showCameraRationale || state.libraryView != null || state.isFullscreen || state.isTabSwitcherOpen || state.isFindOpen || state.isSaveMenuOpen || state.readerContent != null || state.readerError != null || state.isReaderLoading || state.isNetworkOpen || state.isDeveloperSettingsOpen || state.extraction != null || state.selectedTool == QuickTool.Console || state.selectedTool == QuickTool.Info || state.canGoBack) {
        when {
            state.pendingDownload != null -> dispatch(BrowserAction.CancelDownload)
            state.isQrScannerOpen || state.showCameraRationale -> dispatch(BrowserAction.CancelQr)
            state.libraryView != null -> dispatch(BrowserAction.CloseLibrary)
            state.isLiveNetworkOpen -> dispatch(BrowserAction.CloseLiveNetwork)
            state.isLiveConsole -> dispatch(BrowserAction.DismissConsole)
            state.isFullscreen -> dispatch(BrowserAction.ExitFullscreen)
            state.isTabSwitcherOpen -> dispatch(BrowserAction.CloseTabs)
            state.isFindOpen -> dispatch(BrowserAction.CloseFind)
            state.isSaveMenuOpen -> dispatch(BrowserAction.CloseSaveMenu)
            state.readerContent != null || state.readerError != null || state.isReaderLoading -> dispatch(BrowserAction.CloseReader)
            state.isNetworkOpen -> dispatch(BrowserAction.CloseNetwork)
            state.isDeveloperSettingsOpen -> dispatch(BrowserAction.CloseDeveloperSettings)
            state.extraction != null -> dispatch(BrowserAction.DismissExtraction)
            state.selectedTool == QuickTool.Console -> dispatch(BrowserAction.DismissConsole)
            state.selectedTool == QuickTool.Info -> dispatch(BrowserAction.DismissInspector)
            else -> controller.back()
        }
    }
    LightCopyTheme(state.appSettings.appearance, state.appSettings.primaryHex, state.appSettings.secondaryHex) { BrowserShell(
        state = state,
        onAction = ::dispatch,
        page = {
            val activeTabId = tabs.activeId
            key(activeTabId) { WebViewHost(
                url = state.currentUrl,
                controller = controller,
                restoredState = webStates[activeTabId] ?: if (activeTabId == "tab-1") restoredState else null,
                onUpdate = { update ->
                    state = state.with(update)
                    tabs.update(activeTabId, update.title, update.url)
                    state = state.copy(tabs = tabs.all())
                    if (update.loading == true && (state.appSettings.autoClearConsole || !state.appSettings.preserveConsoleOnNavigation)) {
                        consoleLogs.clear(activeTabId)
                        state = reduce(state, BrowserAction.ConsoleCaptured(emptyList()))
                    }
                    if (update.loading == false && state.selectedTool == QuickTool.Info) {
                        controller.pageInfo(state.sslState, state.loadTimeMillis) { result -> state = reduce(state, BrowserAction.PageInfoCompleted(result)) }
                    }
                    if (update.loading == false && state.isPageDark) controller.setPageDark(true)
                    if (update.loading == false) {
                        records.recordVisit(state.currentUrl, state.title, tabs.active().incognito)
                        if (!tabs.active().incognito) state = state.copy(history = records.history())
                    }
                },
                onConsoleMessage = { update ->
                    consoleLogs.append(activeTabId, update.level, update.message, update.source, update.line)
                    state = reduce(state, BrowserAction.ConsoleCaptured(consoleLogs.entries(activeTabId)))
                },
                onInspection = { result ->
                    dispatch(BrowserAction.InspectionCompleted(result))
                    if (state.pageInfo == null) dispatch(BrowserAction.RequestPageInfo)
                },
                developerSettings = state.developerSettings,
                appSettings = state.appSettings,
                onNetworkObservation = { observation ->
                    if (observation.status == null) networkLogs.record(activeTabId, observation.url, observation.method, observation.blocked)
                    else networkLogs.observeStatus(activeTabId, observation.url, observation.method, observation.status)
                    state = reduce(state, BrowserAction.NetworkCaptured(networkLogs.entries(activeTabId)))
                },
                incognito = tabs.active().incognito,
                onFindResult = { active, count, done ->
                    if (done) state = reduce(state, BrowserAction.FindResult(if (count == 0) 0 else active + 1, count))
                },
                onSaveState = { saved ->
                    if (tabs.all().any { it.id == activeTabId }) webStates[activeTabId] = saved
                },
                onDownload = { input ->
                    state = reduce(state, BrowserAction.RequestDownload(input))
                },
                modifier = Modifier.fillMaxSize(),
            ) }
        },
    ) }
}

private fun defaultDeveloperSettings(settings: AppSettings) = dev.lightcopy.browser.browser.DeveloperSettings(
    imagesEnabled = !settings.blockImagesByDefault,
    userAgentMode = if (settings.desktopByDefault) UserAgentMode.Desktop else UserAgentMode.Mobile,
)

@Composable
fun BrowserShell(
    state: BrowserShellState,
    onAction: (BrowserAction) -> Unit,
    page: @Composable () -> Unit = { PagePreview(Modifier.fillMaxSize()) },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            onAction(BrowserAction.FeedbackShown)
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Ink,
        contentWindowInsets = if (state.isFullscreen) WindowInsets(0) else WindowInsets.safeDrawing,
        topBar = {
            if (!state.isFullscreen && !state.isLiveConsole) {
                Column(Modifier.statusBarsPadding()) {
                    if (state.appSettings.showStatusStrip) StatusStrip(state)
                    BrowserDock(state, onAction)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PageConsoleLayout(state, onAction, page)
            if (state.isLoading && !state.isLiveConsole) LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = Cyan,
                trackColor = Divider,
            )
            state.error?.takeIf { !state.isLiveConsole }?.let { error ->
                ErrorPanel(error, { onAction(BrowserAction.Retry) }, Modifier.align(Alignment.Center))
            }
            if (!state.isFullscreen && !state.isLiveConsole && !state.isNetworkOpen && !state.isDeveloperSettingsOpen && state.extraction == null && state.selectedTool != QuickTool.Console && state.selectedTool != QuickTool.Info) {
                QuickActionRail(
                    settings = state.appSettings,
                    selected = state.selectedTool,
                    onSelect = { onAction(BrowserAction.SelectTool(it)) },
                    modifier = Modifier.align(if (state.appSettings.actionPlacement == ActionPlacement.Bottom) Alignment.BottomCenter else Alignment.TopCenter).padding(vertical = 10.dp),
                )
                if (state.appSettings.showConsoleBadge) SmallQuickButton(
                    onClick = { onAction(BrowserAction.SelectTool(QuickTool.Console)) },
                    modifier = Modifier.align(when (state.appSettings.badgeCorner) {
                        BadgeCorner.BottomRight -> Alignment.BottomEnd
                        BadgeCorner.BottomLeft -> Alignment.BottomStart
                        BadgeCorner.TopRight -> Alignment.TopEnd
                        BadgeCorner.TopLeft -> Alignment.TopStart
                    }).padding(horizontal = 16.dp, vertical = (state.appSettings.actionSizeDp + 32).dp).size(state.appSettings.consoleBadgeSizeDp.dp)
                        .semantics { contentDescription = "Open console, ${state.consoleEntries.size} messages" },
                    background = Cyan,
                ) {
                    val label = when (state.appSettings.consoleBadgeContent) {
                        ActionContent.Both -> ">_ ${state.consoleEntries.size}"
                        ActionContent.Icons -> ">_"
                        ActionContent.Labels -> "${state.consoleEntries.size}"
                    }
                    val fontSize = minOf(14f, state.appSettings.consoleBadgeSizeDp / (label.length * .65f + .5f))
                    Text(label, fontFamily = FontFamily.Monospace, color = Ink, fontSize = fontSize.sp,
                        lineHeight = (fontSize + 1).sp, maxLines = 1, fontWeight = FontWeight.Bold)
                }
                if (state.appSettings.showSelectionToggle) SmallQuickButton(
                    onClick = { onAction(BrowserAction.ToggleSelectionMode) },
                    modifier = Modifier.align(quickButtonAlignment(state.appSettings.selectionButtonPosition))
                        .padding(horizontal = 8.dp, vertical = selectionButtonMargin(state.appSettings).dp)
                        .size(state.appSettings.selectionButtonSizeDp.dp)
                        .semantics { contentDescription = "Toggle long press between text selection and element inspection" },
                ) { Text(if (state.appSettings.nativeTextSelection) "Aa" else "</>", color = Cyan,
                    fontSize = minOf(16f, state.appSettings.selectionButtonSizeDp / 2f).sp, lineHeight = minOf(18f, state.appSettings.selectionButtonSizeDp / 2f + 1).sp, maxLines = 1) }
            } else if (!state.isLiveConsole && !state.isFullscreen && state.extraction != null) ExtractionViewer(state.extraction, state.appSettings, onAction)
            else if (!state.isLiveConsole && !state.isFullscreen && !state.isDeveloperSettingsOpen && state.selectedTool == QuickTool.Console) ConsoleViewer(state, onAction)
            else if (!state.isFullscreen && state.selectedTool == QuickTool.Info) InspectorViewer(
                state, onAction,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 520.dp),
            )
            else if (!state.isLiveConsole && !state.isFullscreen && state.isNetworkOpen) NetworkViewer(state, onAction)
            else if (!state.isFullscreen && state.isDeveloperSettingsOpen) DeveloperControls(state, onAction)
            if (state.isMenuOpen) OverflowPanel(
                onTabs = { onAction(BrowserAction.OpenTabs) },
                onNewTab = { onAction(BrowserAction.CreateTab(false)) },
                onFind = { onAction(BrowserAction.OpenFind) },
                onFullscreen = { onAction(BrowserAction.OpenFullscreenSetup) },
                onNetwork = { onAction(BrowserAction.OpenNetwork) },
                onDeveloperControls = { onAction(BrowserAction.OpenDeveloperSettings) },
                onReader = { onAction(BrowserAction.OpenReader) },
                onDark = { onAction(BrowserAction.TogglePageDark) },
                onCapture = { onAction(BrowserAction.CapturePage) },
                onSave = { onAction(BrowserAction.OpenSaveMenu) },
                onHistory = { onAction(BrowserAction.OpenLibrary(LibraryView.History)) },
                onBookmarks = { onAction(BrowserAction.OpenLibrary(LibraryView.Bookmarks)) },
                onBookmark = { onAction(BrowserAction.ToggleBookmark) },
                onShare = { onAction(BrowserAction.SharePage) },
                onQr = { onAction(BrowserAction.OpenQr) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp),
            )
            if (state.isTabSwitcherOpen) TabSwitcher(state, onAction)
            if (state.isFindOpen) FindBar(state, onAction, Modifier.align(Alignment.TopCenter))
            if (state.readerContent != null || state.readerError != null || state.isReaderLoading) ReaderViewer(state, onAction)
            if (state.isSaveMenuOpen) SavePageDialog(onAction)
            state.libraryView?.let { LibraryViewer(state, it, onAction) }
            if (state.isQrScannerOpen) QrScanner(
                onResult = { onAction(BrowserAction.QrScanned(it)) },
                onInvalid = { onAction(BrowserAction.CancelQr); onAction(BrowserAction.ConsoleExportCompleted("QR code does not contain a valid web URL")) },
                onCancel = { onAction(BrowserAction.CancelQr) },
            )
            if (state.showCameraRationale) CameraRationale(onAction)
            state.pendingDownload?.let { DownloadConfirmation(it, onAction) }
            if (state.isFullscreenSetupOpen) FullscreenSetup(state.fullscreenPreferences, onAction)
            state.pendingStorageClear?.let { StorageClearConfirmation(it, state.pageInfo?.info?.origin.orEmpty(), onAction) }
            if (state.isFullscreen) FullscreenExitControl(state.fullscreenPreferences, onAction)
        }
    }
}

@Composable
private fun FullscreenSetup(preferences: FullscreenPreferences, onAction: (BrowserAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(BrowserAction.CloseFullscreenSetup) },
        title = { Text("Fullscreen exit control") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Position", color = TextSecondary, fontSize = 14.sp)
                ExitControlPosition.entries.forEach { position ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onAction(BrowserAction.SetExitPosition(position)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(preferences.position == position, { onAction(BrowserAction.SetExitPosition(position)) })
                        Text(position.label, fontSize = 14.sp)
                    }
                }
                Text("Size: ${preferences.sizeDp}dp", fontSize = 14.sp)
                Slider(preferences.sizeDp.toFloat(), { onAction(BrowserAction.SetExitSize(it.roundToInt())) }, valueRange = 5f..80f)
                Text("Opacity: ${preferences.opacity.toPercentLabel()}", fontSize = 14.sp)
                Slider(preferences.opacity, { onAction(BrowserAction.SetExitOpacity(it)) }, valueRange = .35f..1f)
                Text("Auto-hide", fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "Off", 3_000L to "3s", 5_000L to "5s", 10_000L to "10s").forEach { (delay, label) ->
                        FilterChip(
                            selected = preferences.autoHideMillis == delay,
                            onClick = { onAction(BrowserAction.SetExitAutoHide(delay)) },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Text("Free drag becomes active when you drag the exit control.", color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { onAction(BrowserAction.EnterFullscreen) }) { Text("Enter fullscreen") } },
        dismissButton = { TextButton(onClick = { onAction(BrowserAction.CloseFullscreenSetup) }) { Text("Cancel") } },
        containerColor = Raised,
    )
}

@Composable
private fun FullscreenExitControl(preferences: FullscreenPreferences, onAction: (BrowserAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val sizePx = with(density) { preferences.sizeDp.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val maxX = (widthPx - sizePx).coerceAtLeast(0f)
        val maxY = (heightPx - sizePx).coerceAtLeast(0f)
        fun presetOffset(): IntOffset {
            val left = marginPx.coerceAtMost(maxX)
            val right = (maxX - marginPx).coerceAtLeast(0f)
            val top = marginPx.coerceAtMost(maxY)
            val bottom = (maxY - marginPx).coerceAtLeast(0f)
            val middle = maxX / 2f
            val (x, y) = when (preferences.position) {
                ExitControlPosition.BottomMiddle -> middle to bottom
                ExitControlPosition.BottomLeft -> left to bottom
                ExitControlPosition.BottomRight -> right to bottom
                ExitControlPosition.TopLeft -> left to top
                ExitControlPosition.TopMiddle -> middle to top
                ExitControlPosition.TopRight -> right to top
                ExitControlPosition.FreeDrag -> maxX * preferences.freeX to maxY * preferences.freeY
            }
            return IntOffset(x.roundToInt(), y.roundToInt())
        }
        var offset by remember(preferences.position, preferences.freeX, preferences.freeY, widthPx, heightPx) {
            mutableStateOf(presetOffset())
        }
        var faded by remember { mutableStateOf(false) }
        LaunchedEffect(preferences.autoHideMillis, offset) {
            faded = false
            if (preferences.autoHideMillis > 0) {
                delay(preferences.autoHideMillis)
                faded = true
            }
        }
        Surface(
            modifier = Modifier.clickable(role = Role.Button) { onAction(BrowserAction.ExitFullscreen) }
                .offset { offset }
                .size(preferences.sizeDp.dp)
                .alpha(if (faded) .18f else preferences.opacity)
                .semantics { contentDescription = "Exit fullscreen" }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragStart = { faded = false },
                        onDragEnd = {
                            onAction(BrowserAction.SaveFreeDrag(
                                if (maxX == 0f) .5f else offset.x / maxX,
                                if (maxY == 0f) .5f else offset.y / maxY,
                            ))
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offset = IntOffset(
                            (offset.x + dragAmount.x).roundToInt().coerceIn(0, maxX.roundToInt()),
                            (offset.y + dragAmount.y).roundToInt().coerceIn(0, maxY.roundToInt()),
                        )
                    }
                },
            shape = CircleShape,
            color = Raised,
            tonalElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("↙", color = Cyan, fontSize = (preferences.sizeDp * .42f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun filteredConsole(state: BrowserShellState): List<ConsoleEntry> =
    (state.pausedConsoleEntries ?: state.consoleEntries).filter {
        state.consoleFilter.accepts(it) && (it.message.contains(state.consoleQuery, ignoreCase = true) || it.source.contains(state.consoleQuery, ignoreCase = true))
    }

private fun formattedConsole(state: BrowserShellState): String =
    ConsoleLogFormatter.format(filteredConsole(state), state.consoleTimestamps)

@Composable
private fun InspectorViewer(state: BrowserShellState, onAction: (BrowserAction) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, color = Ink, tonalElevation = 8.dp, shadowElevation = 12.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("COPY & INSPECT", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAction(BrowserAction.DismissInspector) }, Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PageInfoContent(state, onAction)
                HorizontalDivider(color = Divider)
                Text("COPY PAGE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                val copyKinds = listOf(
                    ExtractionKind.Url, ExtractionKind.Title, ExtractionKind.Links,
                    ExtractionKind.Images, ExtractionKind.Markdown, ExtractionKind.SelectedHtml,
                )
                copyKinds.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { kind ->
                            OutlinedButton(
                                onClick = { onAction(BrowserAction.RequestExtraction(kind)) },
                                enabled = kind != ExtractionKind.SelectedHtml || state.inspection != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text(kind.title, fontSize = 13.sp) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = Divider)
                Text("ELEMENT", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    if (state.inspection == null) "Long-press any page element to select and highlight it."
                    else "Long-press another element to change the selection.",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
                state.inspection?.let { inspection ->
                    InspectorField("Tag", inspection.tag, onAction)
                    InspectorField("ID", inspection.id.ifEmpty { "—" }, onAction, inspection.id.isNotEmpty())
                    InspectorField("Class", inspection.classes.ifEmpty { "—" }, onAction, inspection.classes.isNotEmpty())
                    InspectorField("Selector", inspection.selector, onAction)
                    Text("HTML", color = TextSecondary, fontSize = 12.sp)
                    SelectionContainer {
                        Text(
                            inspection.html,
                            modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(10.dp),
                            color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
                        )
                    }
                    Button(
                        onClick = { onAction(BrowserAction.RequestExtraction(ExtractionKind.SelectedHtml)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Open selected HTML") }
                }
            }
        }
    }
}

@Composable
private fun PageInfoContent(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    Text("PAGE INFO", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    when {
        state.pageInfo?.isLoading == true -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), color = Cyan); Spacer(Modifier.width(10.dp)); Text("Reading active page…", fontSize = 14.sp) }
        state.pageInfo?.error != null -> Text(state.pageInfo.error, color = Color(0xFFFF7B8B), fontSize = 14.sp)
        state.pageInfo?.info != null -> {
            val info = state.pageInfo.info
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.favicon?.let { Image(it.asImageBitmap(), "Page favicon", Modifier.size(32.dp)) }
                Spacer(Modifier.width(if(state.favicon != null) 10.dp else 0.dp))
                Column(Modifier.weight(1f)) { Text(info.title.ifEmpty { "Untitled" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(info.url, color = TextSecondary, fontSize = 12.sp) }
            }
            InfoLine("SSL", info.sslState.label); InfoLine("Load", info.loadTimeMillis?.let { "$it ms" } ?: "Unavailable")
            InfoLine("Viewport", info.viewport); InfoLine("Favicon", info.faviconUrl.ifEmpty { "Not declared" })
            InfoSection("META", info.meta); InfoSection("OPEN GRAPH", info.openGraph)
            StorageSection(StorageKind.Cookies, info.cookies, onAction)
            StorageSection(StorageKind.LocalStorage, info.localStorage, onAction)
            StorageSection(StorageKind.SessionStorage, info.sessionStorage, onAction)
            Button(onClick={onAction(BrowserAction.CopyPageInfo)},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Copy page info")}
        }
        else -> TextButton(onClick={onAction(BrowserAction.RequestPageInfo)},Modifier.heightIn(min=48.dp)){Text("Load page info")}
    }
}

@Composable private fun InfoLine(label:String,value:String){Column(Modifier.fillMaxWidth().background(Surface,RoundedCornerShape(8.dp)).padding(10.dp)){Text(label,color=TextSecondary,fontSize=11.sp);SelectionContainer{Text(value,fontFamily=FontFamily.Monospace,fontSize=14.sp)}}}
@Composable private fun InfoSection(label:String,values:List<NamedValue>){Text(label,color=TextSecondary,fontSize=12.sp,fontWeight=FontWeight.Bold);if(values.isEmpty())Text("None",color=TextSecondary,fontSize=14.sp) else values.forEach{InfoLine(it.name,it.value)}}
@Composable private fun StorageSection(kind:StorageKind,values:List<NamedValue>,onAction:(BrowserAction)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(kind.label.uppercase(),color=TextSecondary,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={onAction(BrowserAction.RequestStorageClear(kind))},enabled=values.isNotEmpty(),modifier=Modifier.heightIn(min=48.dp)){Text("Clear")}};if(values.isEmpty())Text("Empty",color=TextSecondary,fontSize=14.sp)else values.forEach{InfoLine(it.name,it.value)}}

@Composable private fun StorageClearConfirmation(kind:StorageKind,origin:String,onAction:(BrowserAction)->Unit){AlertDialog(onDismissRequest={onAction(BrowserAction.CancelStorageClear)},title={Text("Clear ${kind.label}?")},text={Text("This permanently clears ${kind.label} only for $origin. Other sites are not affected.")},confirmButton={Button(onClick={onAction(BrowserAction.ConfirmStorageClear)},Modifier.heightIn(min=48.dp)){Text("Clear for this origin")}},dismissButton={TextButton(onClick={onAction(BrowserAction.CancelStorageClear)},Modifier.heightIn(min=48.dp)){Text("Cancel")}},containerColor=Raised)}

@Composable
private fun InspectorField(
    label: String,
    value: String,
    onAction: (BrowserAction) -> Unit,
    copyEnabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            SelectionContainer { Text(value, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp) }
        }
        TextButton(
            onClick = { onAction(BrowserAction.CopyInspectorValue(label.lowercase(), value)) },
            enabled = copyEnabled,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Copy") }
    }
}

@Composable
private fun ConsoleViewer(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    val entries = filteredConsole(state)
    val scroll = rememberScrollState()
    LaunchedEffect(entries, scroll.maxValue, state.appSettings.consoleAutoScroll) {
        if (state.appSettings.consoleAutoScroll && state.pausedConsoleEntries == null) scroll.animateScrollTo(scroll.maxValue)
    }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CONSOLE", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onAction(BrowserAction.ToggleLiveConsole) }, modifier = Modifier.semantics { contentDescription = "Open live page and console" }) { Text("+", fontSize = 24.sp) }
                IconButton(onClick = { onAction(BrowserAction.OpenDeveloperSettings) }, modifier = Modifier.semantics { contentDescription = "Console settings" }) { Text("⚙") }
                TextButton(onClick = { onAction(BrowserAction.ToggleConsoleTimestamps) }, Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.consoleTimestamps) "Time on" else "Time off")
                }
                IconButton(onClick = { onAction(BrowserAction.DismissConsole) }, Modifier.semantics { contentDescription = "Close console" }) { Text("×", fontSize = 24.sp) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ConsoleFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.consoleFilter == filter,
                        onClick = { onAction(BrowserAction.SetConsoleFilter(filter)) },
                        label = { Text(filter.label, fontSize = 12.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(state.consoleQuery, { onAction(BrowserAction.SetConsoleQuery(it)) }, label = { Text("Search console") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(BrowserAction.ToggleConsolePause) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.pausedConsoleEntries != null) "Resume" else "Pause") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    state.consoleInput,
                    { onAction(BrowserAction.SetConsoleInput(it)) },
                    label = { Text("JavaScript input") },
                    placeholder = { Text("document.title") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onAction(BrowserAction.RunConsoleInput) }),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onAction(BrowserAction.RunConsoleInput) }, enabled = state.consoleInput.isNotBlank(), modifier = Modifier.padding(start = 8.dp).heightIn(min = 48.dp)) { Text("Run") }
            }
            if (entries.isEmpty()) {
                Text(
                    if (state.consoleEntries.isEmpty()) "No console messages yet" else "No messages match this filter",
                    color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally).padding(top = 48.dp),
                )
            } else {
                SelectionContainer(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) {
                        entries.forEach { ConsoleRow(it, state.consoleTimestamps, state.appSettings) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Raised).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onAction(BrowserAction.ClearConsole) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = state.consoleEntries.isNotEmpty()) { Text("Clear") }
                OutlinedButton(onClick = { onAction(BrowserAction.CopyConsole) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = entries.isNotEmpty()) { Text("Copy") }
                Button(onClick = { onAction(BrowserAction.ExportConsole) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = entries.isNotEmpty()) { Text("Export") }
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleEntry, timestamps: Boolean, settings: AppSettings = AppSettings()) {
    val levelColor = when (entry.level) {
        ConsoleLevel.ERROR -> hexColor(settings.consoleErrorHex)
        ConsoleLevel.WARNING -> hexColor(settings.consoleWarningHex)
        ConsoleLevel.INFO -> hexColor(settings.consoleInfoHex)
        ConsoleLevel.LOG -> hexColor(settings.consoleLogHex)
        ConsoleLevel.DEBUG -> hexColor(settings.consoleDebugHex)
    }
    val rendered = ConsoleLogFormatter.format(listOf(entry), timestamps)
    Text(rendered, color = levelColor, fontFamily = FontFamily.Monospace, fontSize = settings.consoleFontSp.sp, lineHeight = (settings.consoleFontSp + 6).sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
}

@Composable
private fun ExtractionViewer(view: ExtractionViewState, settings: AppSettings, onAction: (BrowserAction) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (view.kind != ExtractionKind.VisibleText) {
                    FilterChip(
                        selected = view.kind == ExtractionKind.OriginalHtml,
                        onClick = { onAction(BrowserAction.RequestExtraction(ExtractionKind.OriginalHtml)) },
                        label = { Text("Original", fontSize = 13.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = view.kind == ExtractionKind.RenderedDom,
                        onClick = { onAction(BrowserAction.RequestExtraction(ExtractionKind.RenderedDom)) },
                        label = { Text("Rendered", fontSize = 13.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                } else Text(view.kind.title, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onAction(BrowserAction.DismissExtraction) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Close") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    view.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Cyan)
                    view.error != null -> Text(
                        view.error,
                        color = Color(0xFFFF7B8B),
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> SelectionContainer {
                        when (view.kind) {
                            ExtractionKind.VisibleText -> Text(view.content.orEmpty(), color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp))
                            ExtractionKind.Links, ExtractionKind.Images -> Text(
                                ExtractionCopyFormatter.render(view.content.orEmpty(), view.kind, CopyFormat.PlainText),
                                color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp,
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            )
                            else -> SourceContent(view.content.orEmpty(), settings, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Button(
                onClick = { onAction(BrowserAction.CopyExtraction) },
                enabled = view.content != null,
                modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp),
            ) { Text("Copy ${view.kind.title}") }
        }
    }
}

@Composable
private fun SourceContent(content: String, settings: AppSettings, modifier: Modifier = Modifier) {
    val sourceColor = when (settings.syntaxTheme) {
        SyntaxTheme.Cyan -> Cyan
        SyntaxTheme.Solarized -> Color(0xFFB8C97A)
        SyntaxTheme.Mono -> TextPrimary
    }
    val horizontal = rememberScrollState()
    Row(modifier.verticalScroll(rememberScrollState()).then(if (settings.wrapSourceLines) Modifier else Modifier.horizontalScroll(horizontal)).padding(16.dp)) {
        if (settings.showSourceLineNumbers) Text(content.lines().indices.joinToString("\n") { (it + 1).toString() }, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = settings.sourceFontSizeSp.sp, lineHeight = (settings.sourceFontSizeSp + 6).sp, modifier = Modifier.padding(end = 12.dp))
        Text(content, color = sourceColor, fontFamily = FontFamily.Monospace, fontSize = settings.sourceFontSizeSp.sp, lineHeight = (settings.sourceFontSizeSp + 6).sp, softWrap = settings.wrapSourceLines)
    }
}

@Composable
private fun StatusStrip(state: BrowserShellState) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("LIGHTCOPY", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(5.dp).background(Mint, CircleShape))
        Text("  ON-DEVICE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(state.title.take(24), color = TextSecondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (state.tabs.firstOrNull { it.id == state.activeTabId }?.incognito == true) Text(" PRIVATE", color = Color(0xFFFFCA70), fontSize = 11.sp)
        Text("  ${state.tabs.size} TAB${if (state.tabs.size == 1) "" else "S"}", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PagePreview(modifier: Modifier = Modifier) {
    Column(modifier.background(Surface).padding(horizontal = 22.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Cyan, shape = RoundedCornerShape(7.dp)) {
                Text("A", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Ink, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Android Developers", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("developer.android.com", fontSize = 12.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("Build for every\nAndroid device.", fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "Modern tools, platform guidance, and APIs for creating useful Android experiences.",
            color = TextSecondary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(30.dp))
        SourceCard()
    }
}

@Composable
private fun SourceCard() {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(14.dp))
            .background(Ink, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PAGE SIGNAL", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.weight(1f))
            Text("0.42s", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("<main class=\"hero\">", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text("  1,284 nodes · 34 requests", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text("</main>", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}

@Composable
private fun QuickActionRail(settings: AppSettings, selected: QuickTool?, onSelect: (QuickTool) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        color = Raised.copy(alpha = .97f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(horizontal = minOf(6, settings.actionSizeDp / 8).dp, vertical = minOf(5, settings.actionSizeDp / 10).dp)) {
            QuickTool.entries.forEach { tool ->
                val active = selected == tool
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .height(settings.actionSizeDp.dp).semantics { contentDescription = tool.label }.clickable(role = Role.Button) { onSelect(tool) }.padding(vertical = minOf(8, settings.actionSizeDp / 8).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (settings.actionContent != ActionContent.Labels) Text(tool.glyph, color = if (active) Ink else Cyan, fontFamily = FontFamily.Monospace,
                        fontSize = minOf(14f, settings.actionSizeDp / 2.8f).sp, lineHeight = minOf(16f, settings.actionSizeDp / 2.8f + 1).sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(if (active) Cyan else Color.Transparent, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp))
                    if (settings.actionContent != ActionContent.Icons) Text(tool.label, color = if (active) TextPrimary else TextSecondary, fontSize = minOf(14f, settings.actionSizeDp / 2.8f).sp, lineHeight = minOf(16f, settings.actionSizeDp / 2.8f + 1).sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BrowserDock(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    val height = state.appSettings.addressHeightDp
    val buttonSize = minOf(48, height)
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 12.dp, vertical = minOf(10, height / 6).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(minOf(5, height / 6).dp)) {
            DockButton("‹", "Back", state.canGoBack, buttonSize) { onAction(BrowserAction.NavigateBack) }
            DockButton("›", "Forward", state.canGoForward, buttonSize) { onAction(BrowserAction.NavigateForward) }
            BasicTextField(
                value = state.address,
                onValueChange = { onAction(BrowserAction.EditAddress(it)) },
                modifier = Modifier.weight(1f).height(height.dp).testTag("address-field")
                    .background(Surface, RoundedCornerShape(minOf(18, height / 3).dp))
                    .border(1.dp, Divider, RoundedCornerShape(minOf(18, height / 3).dp)),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = state.appSettings.addressFontSp.sp, lineHeight = (state.appSettings.addressFontSp + 2).sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onAction(BrowserAction.SubmitAddress) }),
                decorationBox = { input ->
                    Row(Modifier.fillMaxSize().padding(start = minOf(16, height / 3).dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { input() }
                        DockButton("×", "Clear address", true, buttonSize) { onAction(BrowserAction.EditAddress("")) }
                    }
                },
            )
            DockButton("↻", "Reload", sizeDp = buttonSize) { onAction(BrowserAction.Reload) }
            DockButton("⋮", "More", sizeDp = buttonSize) { onAction(BrowserAction.ToggleMenu) }
        }
    }
}

@Composable
private fun DockButton(glyph: String, description: String, enabled: Boolean = true, sizeDp: Int = 48, onClick: () -> Unit) {
    Box(Modifier.size(sizeDp.dp).semantics { contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, color = if (enabled) TextPrimary else TextSecondary, fontSize = minOf(26f, sizeDp * .55f).sp, lineHeight = minOf(28f, sizeDp * .6f).sp, maxLines = 1)
    }
}

@Composable
private fun ErrorPanel(error: BrowserError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(24.dp), color = Raised, shape = RoundedCornerShape(18.dp), shadowElevation = 10.dp) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (error.isOffline) "YOU'RE OFFLINE" else "PAGE UNAVAILABLE", color = Color(0xFFFF7B8B),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))
            Text(error.description, color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
        }
    }
}

private fun BrowserShellState.with(update: PageUpdate) = copy(
    address = update.url ?: address,
    currentUrl = update.url ?: currentUrl,
    title = update.title ?: title,
    progress = update.progress ?: progress,
    isLoading = update.loading ?: isLoading,
    canGoBack = update.canGoBack ?: canGoBack,
    canGoForward = update.canGoForward ?: canGoForward,
    error = if (update.clearError) null else update.error ?: error,
    inspection = if (update.clearInspection) null else inspection,
    pageInfo = if (update.url != null && update.url != currentUrl) null else pageInfo,
    sslState = update.sslState ?: sslState,
    loadTimeMillis = update.loadTimeMillis ?: loadTimeMillis,
    favicon = if (update.clearFavicon) null else update.favicon ?: favicon,
)

@Composable
private fun NetworkViewer(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NETWORK · ${state.networkEntries.size}", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAction(BrowserAction.CloseNetwork) }, Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
            Text(
                "WebView exposes requested URLs and methods, but not successful response status codes or full response bodies. HTTP error status is shown when WebView reports it; blocked requests show 204.",
                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().background(Surface).padding(12.dp),
            )
            if (state.networkEntries.isEmpty()) Text("No observable requests yet", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally).padding(top = 48.dp))
            else SelectionContainer {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.networkEntries.asReversed().forEach { entry ->
                        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Row { Text(entry.method, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.width(10.dp)); Text(entry.status?.toString() ?: "Status unavailable", color = if (entry.blocked) Color(0xFFFFCA70) else TextSecondary, fontSize = 13.sp) }
                            Text(entry.url, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                            if (entry.blocked) Text("Blocked locally", color = Color(0xFFFFCA70), fontSize = 12.sp)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { onAction(BrowserAction.ClearNetwork) }, enabled = state.networkEntries.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp)) { Text("Clear network log") }
        }
    }
}

@Composable
private fun DeveloperControls(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    var userAgentEntry by remember { mutableStateOf("") }
    fun update(transform: (AppSettings) -> AppSettings) = onAction(BrowserAction.UpdateAppSettingsDraft(transform(state.appSettingsDraft)))
    AlertDialog(
        onDismissRequest = { onAction(BrowserAction.CloseDeveloperSettings) },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection("LAYOUT & COLORS")
                EnumSetting("Preset", "Custom", themePresets.map { it.name }) { name ->
                    val preset = themePresets.first { it.name == name }
                    update { it.copy(primaryHex = preset.primary, secondaryHex = preset.secondary) }
                }
                ColorSetting("Primary", state.appSettingsDraft.primaryHex) { value -> update { it.copy(primaryHex = value) } }
                ColorSetting("Secondary", state.appSettingsDraft.secondaryHex) { value -> update { it.copy(secondaryHex = value) } }
                EnumSetting("Action placement", state.appSettingsDraft.actionPlacement, ActionPlacement.entries) { value -> update { it.copy(actionPlacement = value) } }
                EnumSetting("Action content", state.appSettingsDraft.actionContent, ActionContent.entries) { value -> update { it.copy(actionContent = value) } }
                SizeSetting("Action size", state.appSettingsDraft.actionSizeDp, 5f..80f) { value -> update { it.copy(actionSizeDp = value) } }
                EnumSetting("Console button corner", state.appSettingsDraft.badgeCorner, BadgeCorner.entries) { value -> update { it.copy(badgeCorner = value) } }
                SizeSetting("Console message button size", state.appSettingsDraft.consoleBadgeSizeDp, 5f..80f) { value -> update { it.copy(consoleBadgeSizeDp = value) } }
                EnumSetting("Console message button content", badgeContentLabel(state.appSettingsDraft.consoleBadgeContent), ActionContent.entries.map(::badgeContentLabel)) { label -> update { it.copy(consoleBadgeContent = ActionContent.entries.first { value -> badgeContentLabel(value) == label }) } }
                ControlSwitch("Show console message button", state.appSettingsDraft.showConsoleBadge) { value -> update { it.copy(showConsoleBadge = value) } }
                SizeSetting("Address height", state.appSettingsDraft.addressHeightDp, 5f..88f) { value -> update { it.copy(addressHeightDp = value) } }
                SizeSetting("Address font", state.appSettingsDraft.addressFontSp, 5f..24f) { value -> update { it.copy(addressFontSp = value) } }
                ControlSwitch("Long press selects text", state.appSettingsDraft.nativeTextSelection) { value -> update { it.copy(nativeTextSelection = value) } }
                EnumSetting("Selection button position", state.appSettingsDraft.selectionButtonPosition, QuickButtonPosition.entries) { value -> update { it.copy(selectionButtonPosition = value) } }
                SizeSetting("Selection button size", state.appSettingsDraft.selectionButtonSizeDp, 5f..80f) { value -> update { it.copy(selectionButtonSizeDp = value) } }
                ControlSwitch("Show page status row", state.appSettingsDraft.showStatusStrip) { value -> update { it.copy(showStatusStrip = value) } }
                ControlSwitch("Show selection mode button", state.appSettingsDraft.showSelectionToggle) { value -> update { it.copy(showSelectionToggle = value) } }
                OutlinedButton(onClick = { update { current -> current.copy(primaryHex = "5DD6FF", secondaryHex = "8DF5C4", actionSizeDp = 56, actionPlacement = ActionPlacement.Bottom, actionContent = ActionContent.Both, consoleBadgeSizeDp = 56, consoleBadgeContent = ActionContent.Both, showConsoleBadge = true, addressHeightDp = 56, addressFontSp = 14, selectionButtonPosition = QuickButtonPosition.BottomCenter, selectionButtonSizeDp = 48, showStatusStrip = true) } }) { Text("Reset appearance controls") }
                SettingsSection("APPEARANCE")
                EnumSetting("Theme", state.appSettingsDraft.appearance, AppearanceMode.entries) { update { s -> s.copy(appearance = it) } }
                Text("Source font: ${state.appSettingsDraft.sourceFontSizeSp}sp", color = TextSecondary, fontSize = 14.sp)
                Slider(state.appSettingsDraft.sourceFontSizeSp.toFloat(), { update { s -> s.copy(sourceFontSizeSp = it.roundToInt()) } }, valueRange = 5f..24f)
                ControlSwitch("Show source line numbers", state.appSettingsDraft.showSourceLineNumbers) { update { s -> s.copy(showSourceLineNumbers = it) } }
                ControlSwitch("Wrap source lines", state.appSettingsDraft.wrapSourceLines) { update { s -> s.copy(wrapSourceLines = it) } }
                EnumSetting("Syntax theme", state.appSettingsDraft.syntaxTheme, SyntaxTheme.entries) { update { s -> s.copy(syntaxTheme = it) } }

                SettingsSection("LIVE DEBUGGING CONTROLS")
                ControlSwitch("Show live navigation buttons", state.appSettingsDraft.showLiveNavigation) { value -> update { it.copy(showLiveNavigation = value) } }
                EnumSetting("Live navigation position", state.appSettingsDraft.liveNavigationPosition, ActionPlacement.entries) { value -> update { it.copy(liveNavigationPosition = value) } }
                SizeSetting("Live navigation button size", state.appSettingsDraft.liveNavigationSizeDp, 5f..80f) { value -> update { it.copy(liveNavigationSizeDp = value) } }
                ControlSwitch("Show live Back", state.appSettingsDraft.showLiveBack) { value -> update { it.copy(showLiveBack = value) } }
                ControlSwitch("Show live Forward", state.appSettingsDraft.showLiveForward) { value -> update { it.copy(showLiveForward = value) } }
                ControlSwitch("Show live Stop", state.appSettingsDraft.showLiveStop) { value -> update { it.copy(showLiveStop = value) } }
                ControlSwitch("Show live Refresh", state.appSettingsDraft.showLiveRefresh) { value -> update { it.copy(showLiveRefresh = value) } }
                ControlSwitch("Show live Network", state.appSettingsDraft.showLiveNetwork) { value -> update { it.copy(showLiveNetwork = value) } }
                SettingsSection("CONSOLE")
                EnumSetting("Live console placement", state.appSettingsDraft.consolePlacement, ConsolePlacement.entries) { value -> update { it.copy(consolePlacement = value) } }
                SizeSetting("Console screen %", state.appSettingsDraft.consolePercent, 5f..95f) { value -> update { it.copy(consolePercent = value) } }
                SizeSetting("Console font", state.appSettingsDraft.consoleFontSp, 5f..24f) { value -> update { it.copy(consoleFontSp = value) } }
                ControlSwitch("Console auto-scroll", state.appSettingsDraft.consoleAutoScroll) { value -> update { it.copy(consoleAutoScroll = value) } }
                ColorSetting("Errors", state.appSettingsDraft.consoleErrorHex) { value -> update { it.copy(consoleErrorHex = value) } }
                ColorSetting("Warnings", state.appSettingsDraft.consoleWarningHex) { value -> update { it.copy(consoleWarningHex = value) } }
                ColorSetting("Info", state.appSettingsDraft.consoleInfoHex) { value -> update { it.copy(consoleInfoHex = value) } }
                ColorSetting("Logs", state.appSettingsDraft.consoleLogHex) { value -> update { it.copy(consoleLogHex = value) } }
                ColorSetting("Debug", state.appSettingsDraft.consoleDebugHex) { value -> update { it.copy(consoleDebugHex = value) } }

                ControlSwitch("Show timestamps", state.appSettingsDraft.consoleTimestamps) { update { s -> s.copy(consoleTimestamps = it) } }
                ControlSwitch("Preserve on navigation", state.appSettingsDraft.preserveConsoleOnNavigation) { update { s -> s.copy(preserveConsoleOnNavigation = it) } }
                ControlSwitch("Auto-clear on navigation", state.appSettingsDraft.autoClearConsole) { update { s -> s.copy(autoClearConsole = it) } }
                EnumSetting("Default level", state.appSettingsDraft.consoleLevel, ConsoleFilter.entries.map { it.name }) { update { s -> s.copy(consoleLevel = it) } }

                SettingsSection("BROWSING")
                EnumSetting("Search engine", state.appSettingsDraft.searchEngine, SearchEngine.entries) { update { s -> s.copy(searchEngine = it) } }
                OutlinedTextField(value = state.appSettingsDraft.startingPageUrl, onValueChange = { value -> update { it.copy(startingPageUrl = value) } }, label = { Text("Starting page") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onAction(BrowserAction.UseCurrentPageAsStart) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use current page") }
                ControlSwitch("New tabs private by default", state.appSettingsDraft.defaultIncognito) { update { s -> s.copy(defaultIncognito = it) } }
                ControlSwitch("Keep screen on", state.appSettingsDraft.keepScreenOn) { update { s -> s.copy(keepScreenOn = it) } }
                ControlSwitch("Desktop sites by default", state.appSettingsDraft.desktopByDefault) { update { s -> s.copy(desktopByDefault = it) } }
                ControlSwitch("Block images by default", state.appSettingsDraft.blockImagesByDefault) { update { s -> s.copy(blockImagesByDefault = it) } }
                Text("Defaults are used for newly created tabs. The controls below apply to this tab.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

                SettingsSection("COPY & PRIVACY")
                EnumSetting("Preferred copy format", state.appSettingsDraft.preferredCopyFormat, CopyFormat.entries) { update { s -> s.copy(preferredCopyFormat = it) } }
                ControlSwitch("Vibrate after copy", state.appSettingsDraft.copyVibration) { update { s -> s.copy(copyVibration = it) } }
                ControlSwitch("Clear history on exit", state.appSettingsDraft.clearOnExit.history) { update { s -> s.copy(clearOnExit = s.clearOnExit.copy(history = it)) } }
                ControlSwitch("Clear bookmarks on exit", state.appSettingsDraft.clearOnExit.bookmarks) { update { s -> s.copy(clearOnExit = s.clearOnExit.copy(bookmarks = it)) } }
                ControlSwitch("Clear cookies, cache & site storage on exit", state.appSettingsDraft.clearOnExit.webData) { update { s -> s.copy(clearOnExit = s.clearOnExit.copy(webData = it)) } }
                ControlSwitch("Send Do Not Track on typed URLs", state.appSettingsDraft.doNotTrack) { update { s -> s.copy(doNotTrack = it) } }
                Text("Android WebView cannot attach Do Not Track to every subresource or reload; LightCopy adds DNT: 1 to URLs you enter.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

                SettingsSection("THIS TAB")
                Text("These controls take effect after reload and do not persist as global defaults.", color = TextSecondary, fontSize = 12.sp)
                ControlSwitch("JavaScript", state.developerDraft.javaScriptEnabled) { onAction(BrowserAction.SetJavaScriptEnabled(it)) }
                ControlSwitch("Block common ad hosts", state.developerDraft.adBlockingEnabled) { onAction(BrowserAction.SetAdBlockingEnabled(it)) }
                ControlSwitch("Load images", state.developerDraft.imagesEnabled) { onAction(BrowserAction.SetImagesEnabled(it)) }
                Text("User agent", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                UserAgentMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onAction(BrowserAction.SetUserAgentMode(mode)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.developerDraft.userAgentMode == mode, { onAction(BrowserAction.SetUserAgentMode(mode)) })
                        Text(mode.label, fontSize = 14.sp)
                    }
                }
                if (state.developerDraft.userAgentMode == UserAgentMode.Custom) OutlinedTextField(
                    value = state.developerDraft.customUserAgent,
                    onValueChange = { onAction(BrowserAction.SetCustomUserAgent(it)) },
                    label = { Text("Custom user agent") },
                    supportingText = { Text(state.userAgentError ?: "1–512 characters; control characters are rejected") },
                    isError = state.userAgentError != null,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                SettingsSection("ADVANCED")
                Text("Saved user-agent list", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                state.appSettingsDraft.userAgents.forEachIndexed { index, agent ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(agent, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 2)
                        TextButton(onClick = {
                            onAction(BrowserAction.SetUserAgentMode(UserAgentMode.Custom))
                            onAction(BrowserAction.SetCustomUserAgent(agent))
                        }, Modifier.heightIn(min = 48.dp)) { Text("Use") }
                        TextButton(onClick = { update { s -> s.copy(userAgents = s.userAgents.toMutableList().also { it.removeAt(index) }) } }, Modifier.heightIn(min = 48.dp)) { Text("Remove") }
                    }
                }
                OutlinedTextField(value = userAgentEntry, onValueChange = { userAgentEntry = it }, label = { Text("Add user agent") }, supportingText = { Text(UserAgentEntryValidator.error(userAgentEntry).orEmpty()) }, isError = UserAgentEntryValidator.error(userAgentEntry) != null && userAgentEntry.isNotBlank(), modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    if (UserAgentEntryValidator.error(userAgentEntry) == null && state.appSettingsDraft.userAgents.size < AppSettings.MAX_USER_AGENTS) {
                        update { s -> s.copy(userAgents = s.userAgents + userAgentEntry.trim()) }; userAgentEntry = ""
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add saved user agent") }
                OutlinedTextField(value = state.appSettingsDraft.cssInjection, onValueChange = { update { s -> s.copy(cssInjection = it) } }, label = { Text("CSS injected after page load") }, supportingText = { Text("On-device only; applies to web pages after reload. Max ${AppSettings.MAX_INJECTION_LENGTH} characters.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.appSettingsDraft.javaScriptInjection, onValueChange = { update { s -> s.copy(javaScriptInjection = it) } }, label = { Text("JavaScript injected after page load") }, supportingText = { Text("Runs in the current page context only; never as a native bridge. Max ${AppSettings.MAX_INJECTION_LENGTH} characters.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Ready-made JavaScript snippets", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    javaScriptSnippets.forEach { snippet ->
                        OutlinedButton(onClick = { update { it.copy(javaScriptInjection = snippet.code) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(snippet.name) }
                    }
                }
                OutlinedButton(onClick = { onAction(BrowserAction.ExportSettings) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Export settings") }
                OutlinedButton(onClick = { onAction(BrowserAction.ImportSettings) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Import settings") }
                OutlinedButton(onClick = { onAction(BrowserAction.PickDownloadFolder) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose download folder") }
                Text(if (state.appSettingsDraft.downloadFolderUri.isBlank()) "No folder selected. Android DownloadManager uses the system Downloads collection." else "Selected folder is the starting location for app-created file exports (saved pages, screenshots): ${state.appSettingsDraft.downloadFolderUri}", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Text("Platform limitation: DownloadManager cannot safely write directly into a picked Storage Access Framework folder, so browser downloads remain in system Downloads.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        },
        confirmButton = { Button(onClick = { onAction(BrowserAction.ApplyDeveloperSettings) }, Modifier.heightIn(min = 48.dp)) { Text("Apply & reload") } },
        dismissButton = { TextButton(onClick = { onAction(BrowserAction.CloseDeveloperSettings) }, Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        containerColor = Raised,
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun <T> EnumSetting(label: String, selected: T, values: Iterable<T>, onSelected: (T) -> Unit) {
    Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            val display = when (value) { is Enum<*> -> value.name.replace('_', ' '); else -> value.toString().replace('_', ' ') }
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(display, fontSize = 12.sp) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun ControlSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun OverflowPanel(
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    onFind: () -> Unit,
    onFullscreen: () -> Unit,
    onNetwork: () -> Unit,
    onDeveloperControls: () -> Unit,
    onReader: () -> Unit,
    onDark: () -> Unit,
    onCapture: () -> Unit,
    onSave: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = Raised, shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp) {
        Column(Modifier.width(220.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            listOf("Tabs", "New tab", "Bookmark page", "Bookmarks", "History", "Share page", "Scan QR URL", "Find in page", "Reader mode", "Page dark mode", "Full-page screenshot", "Save page", "Network log", "Developer controls", "Fullscreen").forEach { item ->
                Text(item, color = if (item == "Developer controls") Color(0xFFFF5252) else Color.Unspecified, modifier = Modifier.fillMaxWidth().clickable { when (item) { "Tabs" -> onTabs(); "New tab" -> onNewTab(); "Bookmark page" -> onBookmark(); "Bookmarks" -> onBookmarks(); "History" -> onHistory(); "Share page" -> onShare(); "Scan QR URL" -> onQr(); "Find in page" -> onFind(); "Reader mode" -> onReader(); "Page dark mode" -> onDark(); "Full-page screenshot" -> onCapture(); "Save page" -> onSave(); "Fullscreen" -> onFullscreen(); "Network log" -> onNetwork(); "Developer controls" -> onDeveloperControls() } }
                    .padding(horizontal = 18.dp, vertical = 13.dp), fontSize = 15.sp, fontWeight = if (item == "Developer controls") FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun LibraryViewer(state: BrowserShellState, view: LibraryView, onAction: (BrowserAction) -> Unit) {
    val rows = when (view) {
        LibraryView.History -> state.history.map { Triple(it.title, it.url, it.visitedAt) }
        LibraryView.Bookmarks -> state.bookmarks.map { Triple(it.title, it.url, it.createdAt) }
    }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(view.name.uppercase(), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (view == LibraryView.History) TextButton(onClick = { onAction(BrowserAction.ClearHistory) }, enabled = rows.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear") }
                TextButton(onClick = { onAction(BrowserAction.CloseLibrary) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
            if (rows.isEmpty()) Box(Modifier.fillMaxSize()) { Text("No ${view.name.lowercase()} yet", color = TextSecondary, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center)) }
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (title, url, _) ->
                    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(10.dp)).clickable { onAction(BrowserAction.OpenRecord(url)) }.padding(14.dp)) {
                        Text(title.ifBlank { url }, fontSize = 15.sp, maxLines = 1)
                        Text(url, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRationale(onAction: (BrowserAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(BrowserAction.CancelQr) },
        title = { Text("Camera access") },
        text = { Text("LightCopy uses the camera only while this scanner is open to recognize a QR URL. No image is saved or uploaded.") },
        confirmButton = { Button(onClick = { onAction(BrowserAction.ConfirmCameraPermission) }, Modifier.heightIn(min = 48.dp)) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { onAction(BrowserAction.CancelQr) }, Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        containerColor = Raised,
    )
}

@Composable
private fun DownloadConfirmation(input: dev.lightcopy.browser.downloads.DownloadInput, onAction: (BrowserAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(BrowserAction.CancelDownload) },
        title = { Text("Download file?") },
        text = { Text(input.url, fontSize = 14.sp, color = TextSecondary) },
        confirmButton = { Button(onClick = { onAction(BrowserAction.ConfirmDownload) }, Modifier.heightIn(min = 48.dp)) { Text("Download") } },
        dismissButton = { TextButton(onClick = { onAction(BrowserAction.CancelDownload) }, Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        containerColor = Raised,
    )
}

@Composable
private fun ReaderViewer(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("READER", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAction(BrowserAction.CloseReader) }, Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
            when {
                state.isReaderLoading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = Cyan) }
                state.readerError != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.readerError, color = TextSecondary, fontSize = 16.sp, lineHeight = 24.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onAction(BrowserAction.RequestExtraction(ExtractionKind.VisibleText)) }, Modifier.heightIn(min = 48.dp)) { Text("Open visible text") }
                }
                state.readerContent != null -> SelectionContainer {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp)) {
                        Text(state.readerContent.title.ifBlank { state.title }, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                        if (state.readerContent.byline.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(state.readerContent.byline, color = Cyan, fontSize = 14.sp) }
                        Spacer(Modifier.height(22.dp))
                        Text(state.readerContent.text, fontSize = 18.sp, lineHeight = 29.sp, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavePageDialog(onAction: (BrowserAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(BrowserAction.CloseSaveMenu) },
        title = { Text("Save page") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a format, then an on-device destination.", color = TextSecondary, fontSize = 14.sp)
            PageExportKind.entries.forEach { kind ->
                OutlinedButton(onClick = { onAction(BrowserAction.SavePage(kind)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(kind.label) }
            }
            OutlinedButton(onClick = { onAction(BrowserAction.SavePageArchive) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Complete page archive (.mht)") }
            Text("The archive includes loaded page resources such as images, CSS and JavaScript when WebView can capture them. Server-side PHP source cannot be downloaded by a browser.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onAction(BrowserAction.CloseSaveMenu) }, Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        containerColor = Raised,
    )
}

@Composable
private fun TabSwitcher(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TABS · ${state.tabs.size}/$MAX_TABS", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAction(BrowserAction.CloseTabs) }, Modifier.heightIn(min = 48.dp)) { Text("Done") }
            }
            Text(
                "Only one WebView stays live. Private mode clears WebView cookies, storage, and cache at mode boundaries and is never added to app history.",
                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().background(Surface).padding(12.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tabs.forEach { tab ->
                    Row(
                        Modifier.fillMaxWidth().background(if (tab.id == state.activeTabId) Raised else Surface, RoundedCornerShape(10.dp))
                            .clickable { onAction(BrowserAction.SelectTab(tab.id)) }.padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text((if (tab.incognito) "PRIVATE · " else "") + tab.title, color = if (tab.incognito) Color(0xFFFFCA70) else TextPrimary, fontSize = 14.sp, maxLines = 1)
                            Text(tab.url, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(
                            onClick = { onAction(BrowserAction.CloseTab(tab.id)) },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Close ${tab.title}" },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("×", fontSize = 24.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAction(BrowserAction.CreateTab(true)) }, enabled = state.tabs.size < MAX_TABS, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("New private") }
                Button(onClick = { onAction(BrowserAction.CreateTab(false)) }, enabled = state.tabs.size < MAX_TABS, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("New tab") }
            }
        }
    }
}

@Composable
private fun FindBar(state: BrowserShellState, onAction: (BrowserAction) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = Raised, shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.findQuery,
                onValueChange = { onAction(BrowserAction.EditFind(it)) },
                placeholder = { Text("Find in page") }, singleLine = true,
                modifier = Modifier.weight(1f).height(56.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction(BrowserAction.FindNext(true)) }),
            )
            Text(if (state.findQuery.isBlank()) "—" else "${state.findActiveMatch}/${state.findMatchCount}", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            DockButton("↑", "Previous match", state.findMatchCount > 0) { onAction(BrowserAction.FindNext(false)) }
            DockButton("↓", "Next match", state.findMatchCount > 0) { onAction(BrowserAction.FindNext(true)) }
            DockButton("×", "Close find") { onAction(BrowserAction.CloseFind) }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun BrowserPreview() = LightCopyTheme { BrowserShell(BrowserShellState(), {}) }

data class ExportDocumentInput(val title: String, val initialFolder: android.net.Uri? = null)

/** CREATE_DOCUMENT with both a suggested title and an optional saved start folder. */
class ExportDocumentContract(private val mimeType: String) : ActivityResultContract<ExportDocumentInput, android.net.Uri?>() {
    override fun createIntent(context: Context, input: ExportDocumentInput): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.title)
            .apply { input.initialFolder?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) } }

    override fun parseResult(resultCode: Int, intent: Intent?): android.net.Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

private fun hexColor(hex: String) = Color(android.graphics.Color.parseColor("#$hex"))

@Composable
private fun ColorSetting(label: String, value: String, onValue: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    Column {
        OutlinedTextField(draft, { draft = it.removePrefix("#").take(6); if (validHex(draft)) onValue(draft) },
            label = { Text("$label RGB hex") }, singleLine = true,
            leadingIcon = { Box(Modifier.size(24.dp).background(hexColor(value), CircleShape)) },
            isError = !validHex(draft), modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            themePresets.map { it.primary }.forEach { hex ->
                IconButton(onClick = { onValue(hex) }, modifier = Modifier.semantics { contentDescription = "$label color #$hex" }) {
                    Box(Modifier.size(24.dp).background(hexColor(hex), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SizeSetting(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onValue: (Int) -> Unit) {
    Text("$label: $value", fontSize = 14.sp)
    Slider(value.toFloat(), { onValue(it.roundToInt()) }, valueRange = range)
}

@Composable
private fun PageConsoleLayout(state: BrowserShellState, onAction: (BrowserAction) -> Unit, page: @Composable () -> Unit) {
    val fraction = state.appSettings.consolePercent / 100f
    val placement = state.appSettings.consolePlacement
    val horizontal = placement == ConsolePlacement.Left || placement == ConsolePlacement.Right
    val settings = state.appSettings
    val showNavigation = state.isLiveConsole && settings.showLiveNavigation &&
        (settings.showLiveBack || settings.showLiveForward || settings.showLiveStop || settings.showLiveRefresh || settings.showLiveNetwork)
    val barHeight = if (showNavigation) settings.liveNavigationSizeDp + 4 else 0
    Box(Modifier.fillMaxSize().then(if (state.isLiveConsole) Modifier.safeDrawingPadding().imePadding() else Modifier)) {
        // Keep the page at the same composition slot, including when toolbar/panel preferences change.
        Box((if (!state.isLiveConsole) Modifier.fillMaxSize() else if (horizontal)
            Modifier.fillMaxHeight().fillMaxWidth(1f - fraction).align(if (placement == ConsolePlacement.Left) Alignment.CenterEnd else Alignment.CenterStart)
        else Modifier.fillMaxWidth().fillMaxHeight(1f - fraction).align(if (placement == ConsolePlacement.Top) Alignment.BottomCenter else Alignment.TopCenter)).testTag("live-page-area")) {
            Box(Modifier.fillMaxSize().padding(
                top = if (settings.liveNavigationPosition == ActionPlacement.Top) barHeight.dp else 0.dp,
                bottom = if (settings.liveNavigationPosition == ActionPlacement.Bottom) barHeight.dp else 0.dp,
            )) { page() }
            if (showNavigation) LiveNavigation(state, onAction,
                Modifier.align(if (settings.liveNavigationPosition == ActionPlacement.Top) Alignment.TopCenter else Alignment.BottomCenter))
            if (state.isLiveConsole) IconButton(onClick = { onAction(BrowserAction.DismissConsole) },
                modifier = Modifier.align(if (settings.liveNavigationPosition == ActionPlacement.Top) Alignment.BottomEnd else Alignment.TopEnd)
                    .size(48.dp).background(Raised, CircleShape).semantics { contentDescription = "Exit live console" }) { Text("×", fontSize = 24.sp) }
        }
        if (state.isLiveConsole) {
            Box((if (horizontal) Modifier.fillMaxHeight().fillMaxWidth(fraction).align(if (placement == ConsolePlacement.Left) Alignment.CenterStart else Alignment.CenterEnd)
            else Modifier.fillMaxWidth().fillMaxHeight(fraction).align(if (placement == ConsolePlacement.Top) Alignment.TopCenter else Alignment.BottomCenter)).testTag("live-console-area")) {
                CompactConsole(state)
                if (state.isLiveNetworkOpen) CompactNetworkPanel(state, onAction)
            }
        }
    }
}

@Composable
private fun LiveNavigation(state: BrowserShellState, onAction: (BrowserAction) -> Unit, modifier: Modifier) {
    val settings = state.appSettings
    Surface(modifier.fillMaxWidth().testTag("live-navigation"), color = Raised) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (settings.showLiveBack) DockButton("‹", "Live Back", state.canGoBack, settings.liveNavigationSizeDp) { onAction(BrowserAction.NavigateBack) }
            if (settings.showLiveForward) DockButton("›", "Live Forward", state.canGoForward, settings.liveNavigationSizeDp) { onAction(BrowserAction.NavigateForward) }
            if (settings.showLiveStop) DockButton("■", "Live Stop", state.isLoading, settings.liveNavigationSizeDp) { onAction(BrowserAction.StopLoading) }
            if (settings.showLiveRefresh) DockButton("↻", "Live Refresh", sizeDp = settings.liveNavigationSizeDp) { onAction(BrowserAction.Reload) }
            if (settings.showLiveNetwork) DockButton("⇄", "Live Network", sizeDp = settings.liveNavigationSizeDp) { onAction(if (state.isLiveNetworkOpen) BrowserAction.CloseLiveNetwork else BrowserAction.OpenLiveNetwork) }
        }
    }
}

@Composable
private fun CompactNetworkPanel(state: BrowserShellState, onAction: (BrowserAction) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.networkEntries, scroll.maxValue, state.appSettings.consoleAutoScroll) {
        if (state.appSettings.consoleAutoScroll) scroll.animateScrollTo(scroll.maxValue)
    }
    Surface(Modifier.fillMaxSize().testTag("live-network-panel"), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Raised), verticalAlignment = Alignment.CenterVertically) {
                Text("NETWORK", color = Cyan, fontSize = state.appSettings.consoleFontSp.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                DockButton("×", "Close live network", sizeDp = state.appSettings.liveNavigationSizeDp) { onAction(BrowserAction.CloseLiveNetwork) }
            }
            SelectionContainer(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(6.dp)) {
                    if (state.networkEntries.isEmpty()) Text("Waiting for network requests", color = TextSecondary, fontSize = state.appSettings.consoleFontSp.sp)
                    state.networkEntries.forEach { entry ->
                        val status = if (entry.blocked) "BLOCKED" else entry.status?.toString() ?: "status unavailable"
                        Text("${entry.method} $status\n${entry.url}", fontFamily = FontFamily.Monospace,
                            fontSize = state.appSettings.consoleFontSp.sp, lineHeight = (state.appSettings.consoleFontSp + 6).sp,
                            color = when { entry.blocked -> hexColor(state.appSettings.consoleWarningHex)
                                (entry.status ?: 0) >= 400 -> hexColor(state.appSettings.consoleErrorHex)
                                else -> TextPrimary }, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text("WebView-observable requests only; complete response status and bodies are not available.", color = TextSecondary, fontSize = state.appSettings.consoleFontSp.sp)
                }
            }
        }
    }
}

@Composable
private fun CompactConsole(state: BrowserShellState) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.consoleEntries, scroll.maxValue, state.appSettings.consoleAutoScroll) {
        if (state.appSettings.consoleAutoScroll) scroll.animateScrollTo(scroll.maxValue)
    }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        SelectionContainer {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(6.dp)) {
                if (state.consoleEntries.isEmpty()) {
                    Text(if (state.developerSettings.javaScriptEnabled) "Waiting for console messages" else "JavaScript is disabled for this tab",
                        color = TextSecondary, fontSize = state.appSettings.consoleFontSp.sp)
                    Text("Messages from the current page appear here when it logs output.", color = TextSecondary,
                        fontSize = state.appSettings.consoleFontSp.sp, modifier = Modifier.padding(top = 6.dp))
                }
                state.consoleEntries.forEach { ConsoleRow(it, false, state.appSettings) }
            }
        }
    }
}

private fun quickButtonAlignment(position: QuickButtonPosition): Alignment = when (position) {
    QuickButtonPosition.BottomLeft -> Alignment.BottomStart
    QuickButtonPosition.BottomCenter -> Alignment.BottomCenter
    QuickButtonPosition.BottomRight -> Alignment.BottomEnd
    QuickButtonPosition.TopLeft -> Alignment.TopStart
    QuickButtonPosition.TopCenter -> Alignment.TopCenter
    QuickButtonPosition.TopRight -> Alignment.TopEnd
}

private fun selectionSharesRailEdge(settings: AppSettings): Boolean =
    if (settings.actionPlacement == ActionPlacement.Bottom) settings.selectionButtonPosition.name.startsWith("Bottom")
    else settings.selectionButtonPosition.name.startsWith("Top")

@Composable
private fun SmallQuickButton(onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = Raised, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(background).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

private fun selectionButtonMargin(settings: AppSettings): Int {
    var margin = if (selectionSharesRailEdge(settings)) settings.actionSizeDp + 32 else 8
    if (settings.showConsoleBadge && settings.selectionButtonPosition.name == settings.badgeCorner.name) {
        margin = maxOf(margin, settings.actionSizeDp + 32) + settings.consoleBadgeSizeDp + 8
    }
    return margin
}

private fun badgeContentLabel(content: ActionContent): String = when (content) {
    ActionContent.Both -> "Icon + count"
    ActionContent.Icons -> "Icon"
    ActionContent.Labels -> "Count"
}
