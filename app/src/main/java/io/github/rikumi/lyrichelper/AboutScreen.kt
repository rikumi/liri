package io.github.rikumi.lyrichelper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PROJECT_URL = "https://github.com/rikumi/liri"
private const val DEVELOPER_URL = "https://github.com/rikumi"
private const val COUI_URL = "https://github.com/suqi8/coui"

private fun openAboutLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开链接，请先安装浏览器", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val list = rememberSettingsListScroll(rememberLazyListState())
    Scaffold(
        containerColor = COUITheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            SettingsTopAppBar("关于", scrollBehavior = list.appBar,
                navigationIcon = { SettingsBackButton(onBack) })
        },
    ) { padding ->
        SettingsLazyColumn(list, Modifier.fillMaxSize().padding(padding)) {
            item { SettingsSmallTitle("开发者") }
            item {
                SettingsCard {
                    ArrowPreference(
                        title = "Rikumi", summary = DEVELOPER_URL,
                        startAction = {
                            Image(painterResource(R.drawable.github_avatar), contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape))
                        },
                        onClick = { openAboutLink(context, DEVELOPER_URL) },
                    )
                }
            }
            item {
                SettingsCard {
                    AboutLinkRow("项目源代码", PROJECT_URL)
                    SettingsItemDivider()
                    AboutLinkRow("ColorOS Mod", "https://github.com/rikumi/coloros-mod")
                    SettingsItemDivider()
                    AboutLinkRow("Flyme Mod", "https://github.com/rikumi/flyme-mod")
                    SettingsItemDivider()
                    ArrowPreference(title = "开源许可证", onClick = onOpenLicenses)
                }
            }
            item { Box(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun AboutLinkRow(title: String, url: String) {
    val context = LocalContext.current
    ArrowPreference(title = title, summary = url, onClick = { openAboutLink(context, url) })
}

@Composable
internal fun OpenSourceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val list = rememberSettingsListScroll(rememberLazyListState())
    var license by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        license = withContext(Dispatchers.IO) {
            context.assets.open("licenses/coui-LICENSE").bufferedReader().use { it.readText() }
        }
    }
    Scaffold(
        containerColor = COUITheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            SettingsTopAppBar("开源许可证", scrollBehavior = list.appBar,
                navigationIcon = { SettingsBackButton(onBack) })
        },
    ) { padding ->
        SettingsLazyColumn(list, Modifier.fillMaxSize().padding(padding)) {
            item { Box(Modifier.height(12.dp)) }
            val text = license
            if (text == null) {
                item { SettingsSmallTitle("正在加载…") }
            } else {
                item {
                    SettingsCard {
                        AboutLinkRow("COUI", COUI_URL)
                        SettingsItemDivider()
                        SelectionContainer {
                            Text(text = text, style = COUITheme.textStyles.body2,
                                color = COUITheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            item { Box(Modifier.height(20.dp)) }
        }
    }
}
