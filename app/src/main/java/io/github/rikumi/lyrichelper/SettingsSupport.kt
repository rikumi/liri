package io.github.rikumi.lyrichelper

import android.content.Context
import android.content.SharedPreferences

internal data class SwitchItem(
    val key: String, val label: String, val subtitle: String? = null,
    val sliderKey: String? = null, val sliderMax: Int = 0, val sliderDefault: Int = 0,
    val sliderUnit: String = "dp", val sliderMin: Int = 0, val sliderStep: Int = 1,
    val sliderValueText: ((Int) -> String)? = null,
)

internal fun Context.settingsPrefs(): SharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
internal fun setBool(ctx: Context, key: String, value: Boolean) { ctx.settingsPrefs().edit().putBoolean(key, value).apply() }
internal fun setInt(ctx: Context, key: String, value: Int) { ctx.settingsPrefs().edit().putInt(key, value).apply() }
