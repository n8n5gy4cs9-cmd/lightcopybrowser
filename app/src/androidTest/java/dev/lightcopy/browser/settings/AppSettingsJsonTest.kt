package dev.lightcopy.browser.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsJsonTest {
    private fun base(): JSONObject = JSONObject(AppSettingsJson.encode(AppSettings()))

    @Test fun roundTripPreservesEveryField() {
        val settings = AppSettings(
            appearance = AppearanceMode.Amoled, addressBarPosition = AddressBarPosition.Top, sourceFontSizeSp = 18,
            showSourceLineNumbers = false, wrapSourceLines = false, syntaxTheme = SyntaxTheme.Solarized,
            consoleTimestamps = false, preserveConsoleOnNavigation = true, autoClearConsole = true, consoleLevel = "ERRORS",
            searchEngine = SearchEngine.DuckDuckGo, defaultIncognito = true, keepScreenOn = true, desktopByDefault = true,
            blockImagesByDefault = true, preferredCopyFormat = CopyFormat.Markdown,
            clearOnExit = ClearOnExit(history = true, bookmarks = true, webData = true), doNotTrack = true,
            userAgents = listOf("Agent/1.0", "Agent/2.0"), cssInjection = "body{opacity:1}", javaScriptInjection = "console.log(1)",
            copyVibration = true, downloadFolderUri = "content://folder",
        )
        assertEquals(settings, AppSettingsJson.decode(AppSettingsJson.encode(settings)).getOrNull())
    }

    @Test fun emptyAndBlankInputAreRejected() {
        assertTrue(AppSettingsJson.decode("").isFailure)
        assertTrue(AppSettingsJson.decode("   ").isFailure)
        assertTrue(AppSettingsJson.decode(null).isFailure)
    }

    @Test fun unknownSchemaVersionsAreRejected() {
        assertTrue(AppSettingsJson.decode(base().put("schemaVersion", 99).toString()).isFailure)
        assertTrue(AppSettingsJson.decode("""{"noVersion":true}""").isFailure)
    }

    @Test fun wrongFieldTypesAreRejected() {
        assertTrue(AppSettingsJson.decode(base().put("sourceFontSizeSp", "big").toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("showSourceLineNumbers", "yes").toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("consoleLevel", 3).toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("userAgents", JSONArray().put("a").put(7)).toString()).isFailure)
    }

    @Test fun unknownEnumValuesAreRejected() {
        assertTrue(AppSettingsJson.decode(base().put("appearance", "Neon").toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("searchEngine", "Yahoo").toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("syntaxTheme", "Pink").toString()).isFailure)
    }

    @Test fun outOfRangeFontSizeIsRejected() {
        assertTrue(AppSettingsJson.decode(base().put("sourceFontSizeSp", 99).toString()).isFailure)
    }

    @Test fun tooManyUserAgentsAreRejected() {
        val many = JSONArray()
        (1..AppSettings.MAX_USER_AGENTS + 1).forEach { many.put("Agent/$it") }
        assertTrue(AppSettingsJson.decode(base().put("userAgents", many).toString()).isFailure)
    }

    @Test fun invalidUserAgentEntriesAreRejected() {
        val bad = JSONArray().put("Agent/1").put("has\ncontrol")
        assertTrue(AppSettingsJson.decode(base().put("userAgents", bad).toString()).isFailure)
    }

    @Test fun oversizedInjectionsAreRejected() {
        val long = "x".repeat(AppSettings.MAX_INJECTION_LENGTH + 1)
        assertTrue(AppSettingsJson.decode(base().put("cssInjection", long).toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("javaScriptInjection", long).toString()).isFailure)
    }

    @Test fun missingClearOnExitIsRejected() {
        assertTrue(AppSettingsJson.decode(base().remove("clearOnExit").toString()).isFailure)
    }
    @Test fun customizationRoundTripAndLegacyDefaults() {
        val settings = AppSettings(consolePlacement = ConsolePlacement.Left, consolePercent = 60,
            primaryHex = "ABCDEF", secondaryHex = "112233", actionContent = ActionContent.Icons,
            actionPlacement = ActionPlacement.Top, nativeTextSelection = false, showSelectionToggle = false,
            consoleFontSp = 20, addressFontSp = 18, addressHeightDp = 80, actionSizeDp = 72,
            showConsoleBadge = false, badgeCorner = BadgeCorner.TopLeft, consoleAutoScroll = false,
            consoleErrorHex = "123456", consoleWarningHex = "234567", consoleInfoHex = "345678",
            consoleLogHex = "456789", consoleDebugHex = "56789A")
        assertEquals(settings, AppSettingsJson.decode(AppSettingsJson.encode(settings)).getOrThrow())
        val legacy = base()
        listOf("consolePlacement", "consolePercent", "primaryHex", "nativeTextSelection").forEach { legacy.remove(it) }
        assertEquals(AppSettings(), AppSettingsJson.decode(legacy.toString()).getOrThrow())
    }

    @Test fun invalidCustomizationIsRejected() {
        assertTrue(AppSettingsJson.decode(base().put("primaryHex", "#GG0000").toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("consolePercent", 100).toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("addressHeightDp", 0).toString()).isFailure)
        assertTrue(AppSettingsJson.decode(base().put("nativeTextSelection", "yes").toString()).isFailure)
    }
    @Test fun smallSizesAndSelectionPlacementPersist() {
        val settings = AppSettings(addressHeightDp = 5, addressFontSp = 5, actionSizeDp = 5,
            sourceFontSizeSp = 5, consoleFontSp = 5, consolePercent = 5,
            selectionButtonSizeDp = 12, selectionButtonPosition = QuickButtonPosition.TopLeft, showStatusStrip = false)
        assertEquals(settings, AppSettingsJson.decode(AppSettingsJson.encode(settings)).getOrThrow())
        assertTrue(AppSettingsJson.decode(base().put("selectionButtonSizeDp", 0).toString()).isFailure)
    }
    @Test fun independentConsoleBadgeSettingsPersistAndLegacyValuesMigrate() {
        val settings = AppSettings(consoleBadgeSizeDp = 24, consoleBadgeContent = ActionContent.Labels, showConsoleBadge = false, actionSizeDp = 70)
        assertEquals(settings, AppSettingsJson.decode(AppSettingsJson.encode(settings)).getOrThrow())
        val legacy = base().put("actionSizeDp", 32).put("actionContent", "Icons")
        legacy.remove("consoleBadgeSizeDp")
        legacy.remove("consoleBadgeContent")
        val restored = AppSettingsJson.decode(legacy.toString()).getOrThrow()
        assertEquals(32, restored.consoleBadgeSizeDp)
        assertEquals(ActionContent.Icons, restored.consoleBadgeContent)
        assertTrue(AppSettingsJson.decode(base().put("consoleBadgeSizeDp", 0).toString()).isFailure)
    }
    @Test fun liveNavigationSettingsRoundTripAndLegacyDefaults() {
        val settings = AppSettings(showLiveNavigation = false, liveNavigationPosition = ActionPlacement.Top,
            liveNavigationSizeDp = 12, showLiveBack = false, showLiveRefresh = false,
            showLiveStop = true, showLiveForward = true, showLiveNetwork = false)
        assertEquals(settings, AppSettingsJson.decode(AppSettingsJson.encode(settings)).getOrThrow())
        val legacy = base()
        listOf("showLiveNavigation", "liveNavigationPosition", "liveNavigationSizeDp", "showLiveBack", "showLiveRefresh", "showLiveStop", "showLiveForward", "showLiveNetwork").forEach { legacy.remove(it) }
        val restored = AppSettingsJson.decode(legacy.toString()).getOrThrow()
        assertTrue(restored.showLiveNavigation && restored.showLiveBack && restored.showLiveRefresh && restored.showLiveNetwork)
        assertTrue(!restored.showLiveStop && !restored.showLiveForward)
        assertEquals(32, restored.liveNavigationSizeDp)
        assertTrue(AppSettingsJson.decode(base().put("liveNavigationSizeDp", 0).toString()).isFailure)
    }
}
