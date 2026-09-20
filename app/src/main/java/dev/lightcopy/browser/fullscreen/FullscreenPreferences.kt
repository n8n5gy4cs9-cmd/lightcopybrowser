package dev.lightcopy.browser.fullscreen

import android.content.Context
import kotlin.math.roundToLong

enum class ExitControlPosition(val label: String) {
    BottomMiddle("Bottom middle"),
    BottomLeft("Bottom left"),
    BottomRight("Bottom right"),
    TopLeft("Top left"),
    TopMiddle("Top middle"),
    TopRight("Top right"),
    FreeDrag("Free drag"),
}

data class FullscreenPreferences(
    val position: ExitControlPosition = ExitControlPosition.BottomMiddle,
    val freeX: Float = .5f,
    val freeY: Float = .9f,
    val sizeDp: Int = 56,
    val opacity: Float = .72f,
    val autoHideMillis: Long = 3_000,
) {
    fun sanitized() = copy(
        freeX = freeX.coerceIn(0f, 1f),
        freeY = freeY.coerceIn(0f, 1f),
        sizeDp = sizeDp.coerceIn(5, 80),
        opacity = opacity.coerceIn(.35f, 1f),
        autoHideMillis = autoHideMillis.coerceIn(0, 10_000),
    )
}

interface FullscreenPreferencesStore {
    fun load(): FullscreenPreferences
    fun save(preferences: FullscreenPreferences)
}

class LocalFullscreenPreferencesStore(context: Context) : FullscreenPreferencesStore {
    private val values = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun load(): FullscreenPreferences = FullscreenPreferences(
        position = runCatching {
            ExitControlPosition.valueOf(values.getString(POSITION, null).orEmpty())
        }.getOrDefault(ExitControlPosition.BottomMiddle),
        freeX = values.getFloat(FREE_X, .5f),
        freeY = values.getFloat(FREE_Y, .9f),
        sizeDp = values.getInt(SIZE, 56),
        opacity = values.getFloat(OPACITY, .72f),
        autoHideMillis = values.getLong(AUTO_HIDE, 3_000),
    ).sanitized()

    override fun save(preferences: FullscreenPreferences) {
        val safe = preferences.sanitized()
        values.edit()
            .putString(POSITION, safe.position.name)
            .putFloat(FREE_X, safe.freeX)
            .putFloat(FREE_Y, safe.freeY)
            .putInt(SIZE, safe.sizeDp)
            .putLong(AUTO_HIDE, safe.autoHideMillis)
            .putFloat(OPACITY, safe.opacity)
            .apply()
    }

    private companion object {
        const val NAME = "fullscreen_preferences"
        const val POSITION = "position"
        const val FREE_X = "free_x"
        const val FREE_Y = "free_y"
        const val SIZE = "size_dp"
        const val OPACITY = "opacity"
        const val AUTO_HIDE = "auto_hide_millis"
    }
}

fun Float.toPercentLabel(): String = "${(coerceIn(0f, 1f) * 100).roundToLong()}%"
