package io.github.rikumi.lyrichelper

import android.content.Context
import android.content.SharedPreferences
import java.io.File

internal sealed interface SettingsItem
internal data class GroupTitleItem(val title: String) : SettingsItem
internal data class SwitchItem(
    val key: String, val label: String, val subtitle: String? = null,
    val sliderKey: String? = null, val sliderMax: Int = 0, val sliderDefault: Int = 0,
    val sliderUnit: String = "dp", val sliderMin: Int = 0,
) : SettingsItem
internal data class FolderBlockItem(val key: String, val label: String, val folderName: String) : SettingsItem
internal data class SelectItem(
    val key: String, val label: String, val options: List<String>, val defaultValue: Int = 0,
    val valueKey: String? = null, val dynamicOptions: ((Context) -> List<String>)? = null,
) : SettingsItem

internal fun Context.settingsPrefs(): SharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
internal fun setBool(ctx: Context, key: String, value: Boolean) { ctx.settingsPrefs().edit().putBoolean(key, value).apply() }
internal fun setInt(ctx: Context, key: String, value: Int) { ctx.settingsPrefs().edit().putInt(key, value).apply() }
internal fun deleteEmptyMediaFolder(folderName: String) { }
internal fun createMediaFolder(folderName: String) { }
