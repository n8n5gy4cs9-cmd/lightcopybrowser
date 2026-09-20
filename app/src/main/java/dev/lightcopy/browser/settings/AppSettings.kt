package dev.lightcopy.browser.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class AppearanceMode(val label: String) { Light("Light"), Dark("Dark"), Amoled("AMOLED") }
enum class AddressBarPosition(val label: String) { Top("Top"), Bottom("Bottom") }
enum class SyntaxTheme(val label: String) { Cyan("Cyan"), Solarized("Solarized"), Mono("Monochrome") }
enum class SearchEngine(val label: String, val searchUrl: String) {
    Google("Google", "https://www.google.com/search?q="),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q="),
    Bing("Bing", "https://www.bing.com/search?q="),
}
enum class CopyFormat(val label: String) { PlainText("Plain text"), Markdown("Markdown") }

enum class ConsolePlacement { Bottom, Top, Left, Right }
enum class QuickButtonPosition { BottomLeft, BottomCenter, BottomRight, TopLeft, TopCenter, TopRight }
enum class BadgeCorner { BottomRight, BottomLeft, TopRight, TopLeft }
enum class ActionPlacement { Bottom, Top }
enum class ActionContent { Both, Icons, Labels }

data class ThemePreset(val name: String, val primary: String, val secondary: String)
val themePresets = listOf(
    ThemePreset("Graphite", "5DD6FF", "8DF5C4"), ThemePreset("Ocean", "64B5F6", "80CBC4"),
    ThemePreset("Forest", "81C784", "FFD54F"), ThemePreset("Sunset", "FFAB91", "CE93D8"),
    ThemePreset("Violet", "B39DDB", "80DEEA"), ThemePreset("Rose", "F48FB1", "B0BEC5"),
    ThemePreset("Amber", "FFD54F", "FFAB91"), ThemePreset("Ice", "80DEEA", "90CAF9"),
    ThemePreset("Lime", "DCE775", "A5D6A7"), ThemePreset("Coral", "EF9A9A", "FFE082"),
)
fun validHex(value: String) = value.matches(Regex("[0-9a-fA-F]{6}"))

data class ClearOnExit(
    val history: Boolean = false,
    val bookmarks: Boolean = false,
    val webData: Boolean = false,
)

data class AppSettings(
    val showLiveNavigation: Boolean = true,
    val liveNavigationPosition: ActionPlacement = ActionPlacement.Bottom,
    val liveNavigationSizeDp: Int = 32,
    val showLiveBack: Boolean = true,
    val showLiveForward: Boolean = false,
    val showLiveStop: Boolean = false,
    val showLiveRefresh: Boolean = true,
    val showLiveNetwork: Boolean = true,
    val nativeTextSelection: Boolean = true,
    val selectionButtonPosition: QuickButtonPosition = QuickButtonPosition.BottomCenter,
    val selectionButtonSizeDp: Int = 48,
    val showStatusStrip: Boolean = true,
    val showSelectionToggle: Boolean = true,
    val badgeCorner: BadgeCorner = BadgeCorner.BottomRight,
    val consoleBadgeSizeDp: Int = 56,
    val consoleBadgeContent: ActionContent = ActionContent.Both,
    val showConsoleBadge: Boolean = true,
    val actionSizeDp: Int = 56,
    val actionPlacement: ActionPlacement = ActionPlacement.Bottom,
    val actionContent: ActionContent = ActionContent.Both,
    val addressHeightDp: Int = 56,
    val addressFontSp: Int = 14,
    val primaryHex: String = "5DD6FF",
    val secondaryHex: String = "8DF5C4",
    val consolePlacement: ConsolePlacement = ConsolePlacement.Bottom,
    val consolePercent: Int = 35,
    val consoleFontSp: Int = 14,
    val consoleAutoScroll: Boolean = true,
    val consoleErrorHex: String = "FF7B8B",
    val consoleWarningHex: String = "FFCA70",
    val consoleInfoHex: String = "5DD6FF",
    val consoleLogHex: String = "A5D6A7",
    val consoleDebugHex: String = "CE93D8",
    val appearance: AppearanceMode = AppearanceMode.Dark,
    val addressBarPosition: AddressBarPosition = AddressBarPosition.Top,
    val sourceFontSizeSp: Int = 14,
    val showSourceLineNumbers: Boolean = true,
    val wrapSourceLines: Boolean = true,
    val syntaxTheme: SyntaxTheme = SyntaxTheme.Cyan,
    val consoleTimestamps: Boolean = true,
    val preserveConsoleOnNavigation: Boolean = false,
    val autoClearConsole: Boolean = false,
    val consoleLevel: String = "ALL",
    val searchEngine: SearchEngine = SearchEngine.Google,
    val defaultIncognito: Boolean = false,
    val keepScreenOn: Boolean = false,
    val desktopByDefault: Boolean = false,
    val blockImagesByDefault: Boolean = false,
    val preferredCopyFormat: CopyFormat = CopyFormat.PlainText,
    val clearOnExit: ClearOnExit = ClearOnExit(),
    val doNotTrack: Boolean = false,
    val userAgents: List<String> = emptyList(),
    val cssInjection: String = "",
    val javaScriptInjection: String = "",
    val copyVibration: Boolean = false,
    val downloadFolderUri: String = "",
) {
    fun sanitized(): AppSettings = copy(
        liveNavigationSizeDp = liveNavigationSizeDp.coerceIn(5, 80),
        consoleBadgeSizeDp = consoleBadgeSizeDp.coerceIn(5, 80),
        selectionButtonSizeDp = selectionButtonSizeDp.coerceIn(5, 80),
        actionSizeDp = actionSizeDp.coerceIn(5, 80),
        addressHeightDp = addressHeightDp.coerceIn(5, 88),
        addressFontSp = addressFontSp.coerceIn(5, 24),
        consolePercent = consolePercent.coerceIn(5, 95),
        consoleFontSp = consoleFontSp.coerceIn(5, 24),
        primaryHex = primaryHex.takeIf(::validHex) ?: "5DD6FF",
        secondaryHex = secondaryHex.takeIf(::validHex) ?: "8DF5C4",
        consoleErrorHex = consoleErrorHex.takeIf(::validHex) ?: "FF7B8B",
        consoleWarningHex = consoleWarningHex.takeIf(::validHex) ?: "FFCA70",
        consoleInfoHex = consoleInfoHex.takeIf(::validHex) ?: "5DD6FF",
        consoleLogHex = consoleLogHex.takeIf(::validHex) ?: "A5D6A7",
        consoleDebugHex = consoleDebugHex.takeIf(::validHex) ?: "CE93D8",
        sourceFontSizeSp = sourceFontSizeSp.coerceIn(5, 24),
        consoleLevel = consoleLevel.takeIf { it in CONSOLE_LEVELS } ?: "ALL",
        userAgents = userAgents.map(String::trim).filter { UserAgentEntryValidator.error(it) == null }.distinct().take(MAX_USER_AGENTS),
        cssInjection = cssInjection.take(MAX_INJECTION_LENGTH),
        javaScriptInjection = javaScriptInjection.take(MAX_INJECTION_LENGTH),
    )

    companion object {
        const val MAX_USER_AGENTS = 12
        const val MAX_INJECTION_LENGTH = 8_000
        val CONSOLE_LEVELS = setOf("ALL", "ERRORS", "WARNING", "INFO", "LOG", "DEBUG")
    }
}

object UserAgentEntryValidator {
    fun error(value: String): String? = when {
        value.isBlank() -> "Enter a user agent."
        value.length > 512 -> "User agent must be 512 characters or fewer."
        value.any { it.code < 0x20 || it.code == 0x7f } -> "User agent cannot contain control characters."
        else -> null
    }
}

interface AppSettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

class LocalAppSettingsStore(context: Context) : AppSettingsStore {
    private val values = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    override fun load(): AppSettings = AppSettingsJson.decode(values.getString(VALUE, null)).getOrDefault(AppSettings())
    override fun save(settings: AppSettings) { values.edit().putString(VALUE, AppSettingsJson.encode(settings)).apply() }
    private companion object { const val NAME = "app_settings"; const val VALUE = "settings" }
}

/** Versioned, strict interchange format. Unknown schema versions and malformed fields are rejected. */
object AppSettingsJson {
    const val SCHEMA_VERSION = 1

    fun encode(settings: AppSettings): String {
        val safe = settings.sanitized()
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("showLiveNavigation", safe.showLiveNavigation)
            put("liveNavigationPosition", safe.liveNavigationPosition.name)
            put("liveNavigationSizeDp", safe.liveNavigationSizeDp)
            put("showLiveBack", safe.showLiveBack)
            put("showLiveForward", safe.showLiveForward)
            put("showLiveStop", safe.showLiveStop)
            put("showLiveRefresh", safe.showLiveRefresh)
            put("showLiveNetwork", safe.showLiveNetwork)
            put("nativeTextSelection", safe.nativeTextSelection)
            put("selectionButtonPosition", safe.selectionButtonPosition.name)
            put("selectionButtonSizeDp", safe.selectionButtonSizeDp)
            put("showStatusStrip", safe.showStatusStrip)
            put("showSelectionToggle", safe.showSelectionToggle)
            put("badgeCorner", safe.badgeCorner.name)
            put("consoleBadgeSizeDp", safe.consoleBadgeSizeDp)
            put("consoleBadgeContent", safe.consoleBadgeContent.name)
            put("showConsoleBadge", safe.showConsoleBadge)
            put("actionSizeDp", safe.actionSizeDp)
            put("actionPlacement", safe.actionPlacement.name)
            put("actionContent", safe.actionContent.name)
            put("addressHeightDp", safe.addressHeightDp)
            put("addressFontSp", safe.addressFontSp)
            put("primaryHex", safe.primaryHex)
            put("secondaryHex", safe.secondaryHex)
            put("consolePlacement", safe.consolePlacement.name)
            put("consolePercent", safe.consolePercent)
            put("consoleFontSp", safe.consoleFontSp)
            put("consoleAutoScroll", safe.consoleAutoScroll)
            put("consoleErrorHex", safe.consoleErrorHex)
            put("consoleWarningHex", safe.consoleWarningHex)
            put("consoleInfoHex", safe.consoleInfoHex)
            put("consoleLogHex", safe.consoleLogHex)
            put("consoleDebugHex", safe.consoleDebugHex)
            put("appearance", safe.appearance.name)
            put("addressBarPosition", safe.addressBarPosition.name)
            put("sourceFontSizeSp", safe.sourceFontSizeSp)
            put("showSourceLineNumbers", safe.showSourceLineNumbers)
            put("wrapSourceLines", safe.wrapSourceLines)
            put("syntaxTheme", safe.syntaxTheme.name)
            put("consoleTimestamps", safe.consoleTimestamps)
            put("preserveConsoleOnNavigation", safe.preserveConsoleOnNavigation)
            put("autoClearConsole", safe.autoClearConsole)
            put("consoleLevel", safe.consoleLevel)
            put("searchEngine", safe.searchEngine.name)
            put("defaultIncognito", safe.defaultIncognito)
            put("keepScreenOn", safe.keepScreenOn)
            put("desktopByDefault", safe.desktopByDefault)
            put("blockImagesByDefault", safe.blockImagesByDefault)
            put("preferredCopyFormat", safe.preferredCopyFormat.name)
            put("clearOnExit", JSONObject().put("history", safe.clearOnExit.history).put("bookmarks", safe.clearOnExit.bookmarks).put("webData", safe.clearOnExit.webData))
            put("doNotTrack", safe.doNotTrack)
            put("userAgents", JSONArray(safe.userAgents))
            put("cssInjection", safe.cssInjection)
            put("javaScriptInjection", safe.javaScriptInjection)
            put("copyVibration", safe.copyVibration)
            put("downloadFolderUri", safe.downloadFolderUri)
        }.toString()
    }

    fun decode(text: String?): Result<AppSettings> = runCatching {
        require(!text.isNullOrBlank()) { "Settings file is empty." }
        val root = JSONObject(text)
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Unsupported settings schema." }
        fun string(name: String) = root.opt(name).takeIf { it is String } as? String ?: error("Invalid $name.")
        fun bool(name: String) = root.opt(name).takeIf { it is Boolean } as? Boolean ?: error("Invalid $name.")
        fun int(name: String) = root.opt(name).takeIf { it is Int || it is Long }?.let { (it as Number).toInt() } ?: error("Invalid $name.")
        fun <T : Enum<T>> enum(name: String, values: Array<T>): T = values.firstOrNull { it.name == string(name) } ?: error("Invalid $name.")
        val clear = root.optJSONObject("clearOnExit") ?: error("Invalid clearOnExit.")
        fun clearBool(name: String) = clear.opt(name).takeIf { it is Boolean } as? Boolean ?: error("Invalid clearOnExit.$name.")
        val agents = root.optJSONArray("userAgents") ?: error("Invalid userAgents.")
        require(agents.length() <= AppSettings.MAX_USER_AGENTS) { "Too many user agents." }
        val userAgents = (0 until agents.length()).map { agents.opt(it).takeIf { value -> value is String } as? String ?: error("Invalid userAgents.") }
        listOf("liveNavigationSizeDp" to 5..80, "consoleBadgeSizeDp" to 5..80, "selectionButtonSizeDp" to 5..80, "actionSizeDp" to 5..80, "addressHeightDp" to 5..88, "addressFontSp" to 5..24, "consolePercent" to 5..95, "consoleFontSp" to 5..24).forEach { (key, range) ->
            require(!root.has(key) || int(key) in range) { "$key is out of range." }
        }
        listOf("primaryHex", "secondaryHex", "consoleErrorHex", "consoleWarningHex", "consoleInfoHex", "consoleLogHex", "consoleDebugHex").forEach { key ->
            require(!root.has(key) || validHex(string(key))) { "Invalid $key color." }
        }
        val settings = AppSettings(
            showLiveNavigation = if (root.has("showLiveNavigation")) bool("showLiveNavigation") else true,
            liveNavigationPosition = if (root.has("liveNavigationPosition")) enum("liveNavigationPosition", ActionPlacement.entries.toTypedArray()) else ActionPlacement.Bottom,
            liveNavigationSizeDp = if (root.has("liveNavigationSizeDp")) int("liveNavigationSizeDp") else 32,
            showLiveBack = if (root.has("showLiveBack")) bool("showLiveBack") else true,
            showLiveForward = if (root.has("showLiveForward")) bool("showLiveForward") else false,
            showLiveStop = if (root.has("showLiveStop")) bool("showLiveStop") else false,
            showLiveRefresh = if (root.has("showLiveRefresh")) bool("showLiveRefresh") else true,
            showLiveNetwork = if (root.has("showLiveNetwork")) bool("showLiveNetwork") else true,

            nativeTextSelection = if (root.has("nativeTextSelection")) bool("nativeTextSelection") else true,
            selectionButtonPosition = if (root.has("selectionButtonPosition")) enum("selectionButtonPosition", QuickButtonPosition.entries.toTypedArray()) else QuickButtonPosition.BottomCenter,
            selectionButtonSizeDp = if (root.has("selectionButtonSizeDp")) int("selectionButtonSizeDp") else 48,
            showStatusStrip = if (root.has("showStatusStrip")) bool("showStatusStrip") else true,
            showSelectionToggle = if (root.has("showSelectionToggle")) bool("showSelectionToggle") else true,
            badgeCorner = if (root.has("badgeCorner")) enum("badgeCorner", BadgeCorner.entries.toTypedArray()) else BadgeCorner.BottomRight,
            consoleBadgeSizeDp = if (root.has("consoleBadgeSizeDp")) int("consoleBadgeSizeDp") else if (root.has("actionSizeDp")) int("actionSizeDp").coerceIn(5, 80) else 56,
            consoleBadgeContent = if (root.has("consoleBadgeContent")) enum("consoleBadgeContent", ActionContent.entries.toTypedArray()) else if (root.has("actionContent")) enum("actionContent", ActionContent.entries.toTypedArray()) else ActionContent.Both,
            showConsoleBadge = if (root.has("showConsoleBadge")) bool("showConsoleBadge") else true,
            actionSizeDp = if (root.has("actionSizeDp")) int("actionSizeDp") else 56,
            actionPlacement = if (root.has("actionPlacement")) enum("actionPlacement", ActionPlacement.entries.toTypedArray()) else ActionPlacement.Bottom,
            actionContent = if (root.has("actionContent")) enum("actionContent", ActionContent.entries.toTypedArray()) else ActionContent.Both,
            addressHeightDp = if (root.has("addressHeightDp")) int("addressHeightDp") else 56,
            addressFontSp = if (root.has("addressFontSp")) int("addressFontSp") else 14,
            primaryHex = if (root.has("primaryHex")) string("primaryHex") else "5DD6FF",
            secondaryHex = if (root.has("secondaryHex")) string("secondaryHex") else "8DF5C4",
            consolePlacement = if (root.has("consolePlacement")) enum("consolePlacement", ConsolePlacement.entries.toTypedArray()) else ConsolePlacement.Bottom,
            consolePercent = if (root.has("consolePercent")) int("consolePercent") else 35,
            consoleFontSp = if (root.has("consoleFontSp")) int("consoleFontSp") else 14,
            consoleAutoScroll = if (root.has("consoleAutoScroll")) bool("consoleAutoScroll") else true,
            consoleErrorHex = if (root.has("consoleErrorHex")) string("consoleErrorHex") else "FF7B8B",
            consoleWarningHex = if (root.has("consoleWarningHex")) string("consoleWarningHex") else "FFCA70",
            consoleInfoHex = if (root.has("consoleInfoHex")) string("consoleInfoHex") else "5DD6FF",
            consoleLogHex = if (root.has("consoleLogHex")) string("consoleLogHex") else "A5D6A7",
            consoleDebugHex = if (root.has("consoleDebugHex")) string("consoleDebugHex") else "CE93D8",

            appearance = enum("appearance", AppearanceMode.entries.toTypedArray()), addressBarPosition = enum("addressBarPosition", AddressBarPosition.entries.toTypedArray()),
            sourceFontSizeSp = int("sourceFontSizeSp"), showSourceLineNumbers = bool("showSourceLineNumbers"), wrapSourceLines = bool("wrapSourceLines"), syntaxTheme = enum("syntaxTheme", SyntaxTheme.entries.toTypedArray()),
            consoleTimestamps = bool("consoleTimestamps"), preserveConsoleOnNavigation = bool("preserveConsoleOnNavigation"), autoClearConsole = bool("autoClearConsole"), consoleLevel = string("consoleLevel"),
            searchEngine = enum("searchEngine", SearchEngine.entries.toTypedArray()), defaultIncognito = bool("defaultIncognito"), keepScreenOn = bool("keepScreenOn"), desktopByDefault = bool("desktopByDefault"), blockImagesByDefault = bool("blockImagesByDefault"),
            preferredCopyFormat = enum("preferredCopyFormat", CopyFormat.entries.toTypedArray()), clearOnExit = ClearOnExit(clearBool("history"), clearBool("bookmarks"), clearBool("webData")), doNotTrack = bool("doNotTrack"),
            userAgents = userAgents, cssInjection = string("cssInjection"), javaScriptInjection = string("javaScriptInjection"), copyVibration = bool("copyVibration"), downloadFolderUri = string("downloadFolderUri"),
        ).sanitized()
        require(settings.sourceFontSizeSp == int("sourceFontSizeSp")) { "Source font size is out of range." }
        require(settings.userAgents.size == userAgents.size) { "Invalid user agent entry." }
        require(settings.cssInjection.length == string("cssInjection").length && settings.javaScriptInjection.length == string("javaScriptInjection").length) { "Injection is too long." }
        settings
    }
}
