package dev.lightcopy.browser.ui

import android.graphics.Bitmap
import dev.lightcopy.browser.browser.AddressParser
import dev.lightcopy.browser.browser.DeveloperSettings
import dev.lightcopy.browser.browser.UserAgentMode
import dev.lightcopy.browser.browser.UserAgentValidator
import dev.lightcopy.browser.copy.ExtractionKind
import dev.lightcopy.browser.copy.ExtractionResult
import dev.lightcopy.browser.console.ConsoleEntry
import dev.lightcopy.browser.console.ConsoleFilter
import dev.lightcopy.browser.fullscreen.ExitControlPosition
import dev.lightcopy.browser.fullscreen.FullscreenPreferences
import dev.lightcopy.browser.inspect.ElementInspection
import dev.lightcopy.browser.inspect.InspectionResult
import dev.lightcopy.browser.pageinfo.*
import dev.lightcopy.browser.network.NetworkEntry
import dev.lightcopy.browser.tabs.BrowserTab
import dev.lightcopy.browser.reader.ReaderContent
import dev.lightcopy.browser.copy.PageExportKind
import dev.lightcopy.browser.data.HistoryRecord
import dev.lightcopy.browser.data.BookmarkRecord
import dev.lightcopy.browser.downloads.DownloadInput
import dev.lightcopy.browser.settings.AppSettings
import dev.lightcopy.browser.settings.SearchEngine

enum class LibraryView { History, Bookmarks }

enum class QuickTool(val label: String, val glyph: String) {
    Source("Source", "</>"),
    Text("Text", "Aa"),
    Console("Console", ">_"),
    Info("Info", "i")
}

data class BrowserShellState(
    val isLiveNetworkOpen: Boolean = false,
    val isLiveConsole: Boolean = false,
    val address: String = "developer.android.com",
    val currentUrl: String = "https://developer.android.com",
    val title: String = "New tab",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: BrowserError? = null,
    val selectedTool: QuickTool? = null,
    val isMenuOpen: Boolean = false,
    val extraction: ExtractionViewState? = null,
    val inspection: ElementInspection? = null,
    val pageInfo: PageInfoViewState? = null,
    val pendingStorageClear: StorageKind? = null,
    val sslState: SslState = SslState.Unavailable,
    val loadTimeMillis: Long? = null,
    val favicon: Bitmap? = null,
    val feedback: String? = null,
    val consoleQuery: String = "",
    val pausedConsoleEntries: List<ConsoleEntry>? = null,
    val consoleEntries: List<ConsoleEntry> = emptyList(),
    val consoleFilter: ConsoleFilter = ConsoleFilter.ALL,
    val consoleTimestamps: Boolean = true,
    val appSettings: AppSettings = AppSettings(),
    val appSettingsDraft: AppSettings = AppSettings(),
    val isFullscreen: Boolean = false,
    val isFullscreenSetupOpen: Boolean = false,
    val fullscreenPreferences: FullscreenPreferences = FullscreenPreferences(),
    val networkEntries: List<NetworkEntry> = emptyList(),
    val isNetworkOpen: Boolean = false,
    val isDeveloperSettingsOpen: Boolean = false,
    val developerSettings: DeveloperSettings = DeveloperSettings(),
    val developerDraft: DeveloperSettings = DeveloperSettings(),
    val userAgentError: String? = null,
    val tabs: List<BrowserTab> = listOf(BrowserTab("tab-1")),
    val activeTabId: String = "tab-1",
    val isTabSwitcherOpen: Boolean = false,
    val isFindOpen: Boolean = false,
    val findQuery: String = "",
    val findActiveMatch: Int = 0,
    val findMatchCount: Int = 0,
    val readerContent: ReaderContent? = null,
    val readerError: String? = null,
    val isReaderLoading: Boolean = false,
    val isPageDark: Boolean = false,
    val isSaveMenuOpen: Boolean = false,
    val libraryView: LibraryView? = null,
    val history: List<HistoryRecord> = emptyList(),
    val bookmarks: List<BookmarkRecord> = emptyList(),
    val isQrScannerOpen: Boolean = false,
    val showCameraRationale: Boolean = false,
    val pendingDownload: DownloadInput? = null,
)

data class ExtractionViewState(
    val kind: ExtractionKind,
    val isLoading: Boolean = true,
    val content: String? = null,
    val error: String? = null,
)

data class BrowserError(val description: String, val isOffline: Boolean)
data class PageInfoViewState(val isLoading: Boolean = true, val info: PageInfo? = null, val error: String? = null)

sealed interface BrowserAction {
    data class EditAddress(val value: String) : BrowserAction
    data object SubmitAddress : BrowserAction
    data class SelectTool(val tool: QuickTool) : BrowserAction
    data object NavigateBack : BrowserAction
    data object NavigateForward : BrowserAction
    data object StopLoading : BrowserAction
    data object OpenLiveNetwork : BrowserAction
    data object CloseLiveNetwork : BrowserAction
    data object Reload : BrowserAction
    data object Retry : BrowserAction
    data object ToggleMenu : BrowserAction
    data class RequestExtraction(val kind: ExtractionKind) : BrowserAction
    data class ExtractionCompleted(val result: ExtractionResult) : BrowserAction
    data class InspectionCompleted(val result: InspectionResult) : BrowserAction
    data object DismissInspector : BrowserAction
    data object RequestPageInfo : BrowserAction
    data class PageInfoCompleted(val result: PageInfoResult) : BrowserAction
    data object CopyPageInfo : BrowserAction
    data class RequestStorageClear(val kind: StorageKind) : BrowserAction
    data object CancelStorageClear : BrowserAction
    data object ConfirmStorageClear : BrowserAction
    data class StorageClearCompleted(val result: ClearStorageResult) : BrowserAction
    data class CopyInspectorValue(val label: String, val value: String) : BrowserAction
    data object DismissExtraction : BrowserAction
    data object CopyExtraction : BrowserAction
    data class ConsoleCaptured(val entries: List<ConsoleEntry>) : BrowserAction
    data class SetConsoleFilter(val filter: ConsoleFilter) : BrowserAction
    data object ToggleConsoleTimestamps : BrowserAction
    data object ClearConsole : BrowserAction
    data object CopyConsole : BrowserAction
    data object ExportConsole : BrowserAction
    data class ConsoleExportCompleted(val message: String) : BrowserAction
    data class SetConsoleQuery(val query: String) : BrowserAction
    data object ToggleConsolePause : BrowserAction
    data object ToggleLiveConsole : BrowserAction
    data object ToggleSelectionMode : BrowserAction
    data object DismissConsole : BrowserAction
    data object FeedbackShown : BrowserAction
    data object OpenFullscreenSetup : BrowserAction
    data object CloseFullscreenSetup : BrowserAction
    data object EnterFullscreen : BrowserAction
    data object ExitFullscreen : BrowserAction
    data class SetExitPosition(val position: ExitControlPosition) : BrowserAction
    data class SetExitSize(val sizeDp: Int) : BrowserAction
    data class SetExitOpacity(val opacity: Float) : BrowserAction
    data class SetExitAutoHide(val millis: Long) : BrowserAction
    data class SaveFreeDrag(val x: Float, val y: Float) : BrowserAction
    data object OpenNetwork : BrowserAction
    data object CloseNetwork : BrowserAction
    data class NetworkCaptured(val entries: List<NetworkEntry>) : BrowserAction
    data object ClearNetwork : BrowserAction
    data object OpenDeveloperSettings : BrowserAction
    data object CloseDeveloperSettings : BrowserAction
    data class SetJavaScriptEnabled(val enabled: Boolean) : BrowserAction
    data class SetAdBlockingEnabled(val enabled: Boolean) : BrowserAction
    data class SetImagesEnabled(val enabled: Boolean) : BrowserAction
    data class SetUserAgentMode(val mode: UserAgentMode) : BrowserAction
    data class SetCustomUserAgent(val value: String) : BrowserAction
    data class UpdateAppSettingsDraft(val settings: AppSettings) : BrowserAction
    data class SettingsImported(val settings: AppSettings?, val error: String? = null) : BrowserAction
    data object ExportSettings : BrowserAction
    data object ImportSettings : BrowserAction
    data object PickDownloadFolder : BrowserAction
    data class DownloadFolderSelected(val uri: String) : BrowserAction
    data object ApplyDeveloperSettings : BrowserAction
    data object OpenTabs : BrowserAction
    data object CloseTabs : BrowserAction
    data class CreateTab(val incognito: Boolean) : BrowserAction
    data class SelectTab(val id: String) : BrowserAction
    data class CloseTab(val id: String) : BrowserAction
    data object OpenFind : BrowserAction
    data object CloseFind : BrowserAction
    data class EditFind(val value: String) : BrowserAction
    data class FindResult(val activeMatch: Int, val matchCount: Int) : BrowserAction
    data class FindNext(val forward: Boolean) : BrowserAction
    data object OpenReader : BrowserAction
    data object CloseReader : BrowserAction
    data class ReaderCompleted(val content: ReaderContent?, val error: String?) : BrowserAction
    data object TogglePageDark : BrowserAction
    data object CapturePage : BrowserAction
    data object OpenSaveMenu : BrowserAction
    data object CloseSaveMenu : BrowserAction
    data class SavePage(val kind: PageExportKind) : BrowserAction
    data class PageSaved(val success: Boolean) : BrowserAction
    data class OpenLibrary(val view: LibraryView) : BrowserAction
    data object CloseLibrary : BrowserAction
    data class OpenRecord(val url: String) : BrowserAction
    data object ClearHistory : BrowserAction
    data object ToggleBookmark : BrowserAction
    data object SharePage : BrowserAction
    data object OpenQr : BrowserAction
    data object ConfirmCameraPermission : BrowserAction
    data object CancelQr : BrowserAction
    data class QrScanned(val url: String) : BrowserAction
    data class RequestDownload(val input: DownloadInput) : BrowserAction
    data object ConfirmDownload : BrowserAction
    data object CancelDownload : BrowserAction
}

fun reduce(state: BrowserShellState, action: BrowserAction): BrowserShellState = when (action) {
    is BrowserAction.EditAddress -> state.copy(address = action.value)
    BrowserAction.SubmitAddress -> state.copy(
        currentUrl = normalizeAddress(state.address, state.appSettings.searchEngine),
        selectedTool = null,
        extraction = null,
        error = null,
    )
    is BrowserAction.SelectTool -> state.copy(selectedTool = action.tool, isMenuOpen = false)
    BrowserAction.RequestPageInfo -> state.copy(selectedTool = QuickTool.Info, pageInfo = PageInfoViewState())
    is BrowserAction.PageInfoCompleted -> state.copy(pageInfo = PageInfoViewState(false, action.result.info, action.result.error))
    BrowserAction.CopyPageInfo -> if (state.pageInfo?.info != null) state.copy(feedback = "Page info copied") else state
    is BrowserAction.RequestStorageClear -> state.copy(pendingStorageClear = action.kind)
    BrowserAction.CancelStorageClear -> state.copy(pendingStorageClear = null)
    BrowserAction.ConfirmStorageClear -> state
    is BrowserAction.StorageClearCompleted -> state.copy(pendingStorageClear = null, feedback = action.result.message)
    BrowserAction.ToggleMenu -> state.copy(isMenuOpen = !state.isMenuOpen)
    is BrowserAction.RequestExtraction -> state.copy(
        selectedTool = when (action.kind) {
            ExtractionKind.VisibleText -> QuickTool.Text
            ExtractionKind.OriginalHtml, ExtractionKind.RenderedDom -> QuickTool.Source
            else -> QuickTool.Info
        },
        extraction = ExtractionViewState(action.kind),
    )
    is BrowserAction.ExtractionCompleted -> if (state.extraction?.kind == action.result.kind) state.copy(
        extraction = ExtractionViewState(
            kind = action.result.kind,
            isLoading = false,
            content = action.result.content,
            error = action.result.error,
        ),
    ) else state
    is BrowserAction.InspectionCompleted -> if (action.result.inspection != null) state.copy(
        selectedTool = QuickTool.Info, inspection = action.result.inspection,
        feedback = "Element selected",
    ) else state.copy(selectedTool = QuickTool.Info, feedback = action.result.error ?: "Element inspection failed")
    BrowserAction.DismissInspector -> state.copy(selectedTool = null, inspection = null, pageInfo = null, pendingStorageClear = null)
    is BrowserAction.CopyInspectorValue -> state.copy(feedback = "Copied ${action.label}")
    BrowserAction.DismissExtraction -> state.copy(extraction = null, selectedTool = null)
    BrowserAction.CopyExtraction -> if (state.extraction?.content != null) state.copy(feedback = "Copied") else state
    is BrowserAction.ConsoleCaptured -> state.copy(consoleEntries = action.entries)
    is BrowserAction.SetConsoleFilter -> state.copy(consoleFilter = action.filter)
    BrowserAction.ToggleConsoleTimestamps -> state.copy(consoleTimestamps = !state.consoleTimestamps)
    BrowserAction.ClearConsole -> state.copy(consoleEntries = emptyList(), pausedConsoleEntries = state.pausedConsoleEntries?.let { emptyList() }, feedback = "Console cleared")
    BrowserAction.CopyConsole -> state.copy(feedback = "Console copied")
    BrowserAction.ExportConsole -> state
    is BrowserAction.ConsoleExportCompleted -> state.copy(feedback = action.message)
    is BrowserAction.SetConsoleQuery -> state.copy(consoleQuery = action.query.take(500))
    BrowserAction.ToggleConsolePause -> state.copy(pausedConsoleEntries = if (state.pausedConsoleEntries == null) state.consoleEntries else null)
    BrowserAction.ToggleLiveConsole -> state.copy(isLiveConsole = !state.isLiveConsole, isLiveNetworkOpen = false, selectedTool = QuickTool.Console)
    BrowserAction.ToggleSelectionMode -> state.copy(appSettings = state.appSettings.copy(nativeTextSelection = !state.appSettings.nativeTextSelection))
    BrowserAction.DismissConsole -> state.copy(selectedTool = null, isLiveConsole = false, isLiveNetworkOpen = false)
    BrowserAction.FeedbackShown -> state.copy(feedback = null)
    BrowserAction.OpenFullscreenSetup -> state.copy(isMenuOpen = false, isFullscreenSetupOpen = true)
    BrowserAction.CloseFullscreenSetup -> state.copy(isFullscreenSetupOpen = false)
    BrowserAction.EnterFullscreen -> state.copy(isFullscreen = true, isFullscreenSetupOpen = false, isMenuOpen = false)
    BrowserAction.ExitFullscreen -> state.copy(isFullscreen = false)
    is BrowserAction.SetExitPosition -> state.copy(fullscreenPreferences = state.fullscreenPreferences.copy(position = action.position))
    is BrowserAction.SetExitSize -> state.copy(fullscreenPreferences = state.fullscreenPreferences.copy(sizeDp = action.sizeDp).sanitized())
    is BrowserAction.SetExitOpacity -> state.copy(fullscreenPreferences = state.fullscreenPreferences.copy(opacity = action.opacity).sanitized())
    is BrowserAction.SetExitAutoHide -> state.copy(fullscreenPreferences = state.fullscreenPreferences.copy(autoHideMillis = action.millis).sanitized())
    is BrowserAction.SaveFreeDrag -> state.copy(fullscreenPreferences = state.fullscreenPreferences.copy(
        position = ExitControlPosition.FreeDrag, freeX = action.x, freeY = action.y,
    ).sanitized())
    BrowserAction.OpenLiveNetwork -> if (state.isLiveConsole) state.copy(isLiveNetworkOpen = true) else state
    BrowserAction.CloseLiveNetwork -> state.copy(isLiveNetworkOpen = false)
    BrowserAction.StopLoading -> state.copy(isLoading = false)
    BrowserAction.OpenNetwork -> state.copy(isMenuOpen = false, isNetworkOpen = true)
    BrowserAction.CloseNetwork -> state.copy(isNetworkOpen = false)
    is BrowserAction.NetworkCaptured -> state.copy(networkEntries = action.entries)
    BrowserAction.ClearNetwork -> state.copy(networkEntries = emptyList(), feedback = "Network log cleared")
    BrowserAction.OpenDeveloperSettings -> state.copy(isMenuOpen = false, isDeveloperSettingsOpen = true, developerDraft = state.developerSettings, appSettingsDraft = state.appSettings, userAgentError = null)
    BrowserAction.CloseDeveloperSettings -> state.copy(isDeveloperSettingsOpen = false, developerDraft = state.developerSettings, appSettingsDraft = state.appSettings, userAgentError = null)
    is BrowserAction.SetJavaScriptEnabled -> state.copy(developerDraft = state.developerDraft.copy(javaScriptEnabled = action.enabled))
    is BrowserAction.SetAdBlockingEnabled -> state.copy(developerDraft = state.developerDraft.copy(adBlockingEnabled = action.enabled))
    is BrowserAction.SetImagesEnabled -> state.copy(developerDraft = state.developerDraft.copy(imagesEnabled = action.enabled))
    is BrowserAction.SetUserAgentMode -> state.copy(developerDraft = state.developerDraft.copy(userAgentMode = action.mode), userAgentError = null)
    is BrowserAction.SetCustomUserAgent -> state.copy(developerDraft = state.developerDraft.copy(customUserAgent = action.value), userAgentError = null)
    is BrowserAction.UpdateAppSettingsDraft -> state.copy(appSettingsDraft = action.settings.sanitized())
    is BrowserAction.SettingsImported -> action.settings?.let {
        state.copy(appSettingsDraft = it, feedback = "Settings imported. Apply to use them.")
    } ?: state.copy(feedback = action.error ?: "Settings import failed")
    is BrowserAction.DownloadFolderSelected -> state.copy(appSettingsDraft = state.appSettingsDraft.copy(downloadFolderUri = action.uri))
    BrowserAction.ApplyDeveloperSettings -> {
        val error = if (state.developerDraft.userAgentMode == UserAgentMode.Custom) UserAgentValidator.error(state.developerDraft.customUserAgent) else null
        if (error != null) state.copy(userAgentError = error)
        else {
            val settings = state.appSettingsDraft.sanitized()
            state.copy(
                developerSettings = state.developerDraft,
                appSettings = settings,
                isDeveloperSettingsOpen = false,
                userAgentError = null,
                consoleTimestamps = settings.consoleTimestamps,
                consoleFilter = runCatching { ConsoleFilter.valueOf(settings.consoleLevel) }.getOrDefault(ConsoleFilter.ALL),
                feedback = "Settings applied; page reloaded",
            )
        }
    }
    BrowserAction.OpenTabs -> state.copy(isMenuOpen = false, isTabSwitcherOpen = true)
    BrowserAction.CloseTabs -> state.copy(isTabSwitcherOpen = false)
    BrowserAction.OpenFind -> state.copy(isMenuOpen = false, isFindOpen = true)
    BrowserAction.CloseFind -> state.copy(isFindOpen = false, findQuery = "", findActiveMatch = 0, findMatchCount = 0)
    is BrowserAction.EditFind -> state.copy(findQuery = action.value, findActiveMatch = 0, findMatchCount = 0)
    is BrowserAction.FindResult -> state.copy(findActiveMatch = action.activeMatch, findMatchCount = action.matchCount)
    is BrowserAction.CreateTab, is BrowserAction.SelectTab, is BrowserAction.CloseTab, is BrowserAction.FindNext -> state
    BrowserAction.OpenReader -> state.copy(isMenuOpen = false, isReaderLoading = true, readerContent = null, readerError = null)
    BrowserAction.CloseReader -> state.copy(isReaderLoading = false, readerContent = null, readerError = null)
    is BrowserAction.ReaderCompleted -> state.copy(isReaderLoading = false, readerContent = action.content, readerError = action.error)
    BrowserAction.TogglePageDark -> state.copy(isMenuOpen = false, isPageDark = !state.isPageDark,
        feedback = if (!state.isPageDark) "Page dark mode on" else "Page dark mode off")
    BrowserAction.CapturePage -> state.copy(isMenuOpen = false, feedback = "Preparing full-page capture…")
    BrowserAction.OpenSaveMenu -> state.copy(isMenuOpen = false, isSaveMenuOpen = true)
    BrowserAction.CloseSaveMenu -> state.copy(isSaveMenuOpen = false)
    is BrowserAction.SavePage -> state.copy(isSaveMenuOpen = false)
    is BrowserAction.PageSaved -> state.copy(feedback = if (action.success) "Page saved" else "Save failed")
    is BrowserAction.OpenLibrary -> state.copy(isMenuOpen = false, libraryView = action.view)
    BrowserAction.CloseLibrary -> state.copy(libraryView = null)
    is BrowserAction.OpenRecord -> state.copy(libraryView = null, address = action.url, currentUrl = action.url)
    BrowserAction.ClearHistory -> state.copy(history = emptyList(), feedback = "History cleared")
    BrowserAction.ToggleBookmark -> state
    BrowserAction.SharePage -> state.copy(isMenuOpen = false)
    BrowserAction.OpenQr -> state.copy(isMenuOpen = false)
    BrowserAction.ConfirmCameraPermission -> state.copy(showCameraRationale = false)
    BrowserAction.CancelQr -> state.copy(isQrScannerOpen = false, showCameraRationale = false)
    is BrowserAction.QrScanned -> state.copy(isQrScannerOpen = false, address = action.url, currentUrl = action.url, feedback = "QR URL opened")
    is BrowserAction.RequestDownload -> state.copy(pendingDownload = action.input)
    BrowserAction.ConfirmDownload -> state
    BrowserAction.CancelDownload -> state.copy(pendingDownload = null, feedback = "Download cancelled")
    BrowserAction.NavigateBack,
    BrowserAction.NavigateForward,
    BrowserAction.Reload,
    BrowserAction.Retry,
    BrowserAction.ExportSettings,
    BrowserAction.ImportSettings,
    BrowserAction.PickDownloadFolder -> state
}

fun normalizeAddress(value: String, searchEngine: SearchEngine = SearchEngine.Google): String = AddressParser.parse(value, searchEngine)
