package io.github.rikumi.lyrichelper

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.json.JSONArray
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play

import top.yukonga.miuix.kmp.icon.extended.Edit
import androidx.compose.ui.window.Popup
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val homeExpandEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val homeCollapseEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private enum class Page { HOME, PERMISSIONS, STYLE, EDITOR }

class MainActivity : ComponentActivity() {
    private var resumeToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            MiuixTheme(
                controller = remember(darkTheme) {
                    ThemeController(
                        colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                        // 使用应用图标中的橘黄色；深色模式提高明度以保持可读性。
                        lightColors = lightColorScheme(primary = Color(0xFFFF9F43)),
                        darkColors = darkColorScheme(primary = Color(0xFFFFB86B)),
                    )
                },
                textStyles = couixTextStyles(),
            ) {
                CouixStatusBar()
                CouixOverscrollHost { LiriNavigation(this@MainActivity.resumeToken) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) resumeToken++
    }
}

@Composable
private fun LiriNavigation(refreshToken: Int) {
    var page by remember { mutableStateOf(Page.HOME) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    fun navigate(target: Page) {
        transitionDirection = if (target == Page.HOME) -1 else 1
        page = target
    }
    BackHandler(enabled = page != Page.HOME) { navigate(Page.HOME) }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(280)) { it * transitionDirection / 4 })
                .togetherWith(fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(220)) { -it * transitionDirection / 4 })
        },
        label = "page_transition",
    ) { currentPage ->
        when (currentPage) {
            Page.HOME -> HomeScreen(onPermissions = { navigate(Page.PERMISSIONS) }, onStyle = { navigate(Page.STYLE) }, onEditor = { navigate(Page.EDITOR) })
            Page.PERMISSIONS -> PermissionScreen(refreshToken) { navigate(Page.HOME) }
            Page.STYLE -> StyleScreen { navigate(Page.HOME) }
            Page.EDITOR -> LocalLrcEditorScreen { navigate(Page.HOME) }
        }
    }
}

@Composable
private fun HomeScreen(onPermissions: () -> Unit, onStyle: () -> Unit, onEditor: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf(readPlayerSnapshot(context)) }
    DisposableEffect(context) {
        val preferences = context.settingsPrefs()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            player = readPlayerSnapshot(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val listState = rememberLazyListState()
    val overscrollOffset = remember { mutableFloatStateOf(0f) }
    var settingsExpanded by remember { mutableStateOf(true) }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            CouixLargeTitle(
                title = "Liri Lyrics",
                dividerProgress = couixTopBarDividerProgress(listState, overscrollOffset),
                actions = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { settingsExpanded = !settingsExpanded }) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = if (settingsExpanded) MiuixIcons.ExpandMore else MiuixIcons.ExpandLess,
                            contentDescription = if (settingsExpanded) "收起设置" else "展开设置",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding).couixOverscroll(listState, overscrollOffset).padding(top = 8.dp),
        ) {
            item {
                AnimatedVisibility(
                    visible = settingsExpanded,
                    enter = fadeIn(tween(220, easing = homeExpandEasing)) + expandVertically(animationSpec = tween(280, easing = homeExpandEasing)),
                    exit = fadeOut(tween(160, easing = homeCollapseEasing)) + slideOutVertically(animationSpec = tween(280, easing = homeCollapseEasing)) { -it } + shrinkVertically(animationSpec = tween(280, easing = homeCollapseEasing)),
                ) {
                    CouixCard {
                        CouixCategoryRow(MiuixIcons.Lock, "授予系统权限", onPermissions)
                        CouixItemDivider()
                        CouixCategoryRow(MiuixIcons.Tune, "位置与样式", onStyle)
                        CouixItemDivider()
                        LyricEditingFragment(player, context) {
                            onEditor()
                        }
                    }
                }
            }
            item {
                if (settingsExpanded) CouixSmallTitle("正在播放")
                NowPlayingCard(player, settingsExpanded, onEditor) { settingsExpanded = !settingsExpanded }
            }
            if (settingsExpanded) {
                item {
                    CouixSmallTitle("手动搜词")
                    SearchResultCard(player, context)
                }
            }
            item {
                BasicText(
                    text = "𝄽",
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 48.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private data class SearchResult(val id: Long, val key: String, val title: String, val artist: String, val album: String)
private data class PlayerSnapshot(val title: String, val artist: String, val album: String, val cover: String?, val coverVersion: Long, val currentLyric: String, val nextLyric: String, val lyricProgress: Float, val lyricStartElapsed: Long, val lyricDurationMs: Long, val results: List<SearchResult>, val selectedId: Long, val autoSave: Boolean, val songOffsetMs: Int, val serviceStarted: Boolean, val localLrcExists: Boolean, val manualSearch: Boolean, val searchingLyrics: Boolean, val searchSource: String?, val playing: Boolean, val playbackPackage: String, val notificationIcon: String?, val notificationIconPackage: String)

private fun readPlayerSnapshot(context: Context): PlayerSnapshot {
    val prefs = context.settingsPrefs()
    val results = runCatching {
        val array = JSONArray(prefs.getString("lyric_search_results", "[]"))
        (0 until array.length()).map { item ->
            val value = array.getJSONObject(item)
            SearchResult(value.getLong("id"), value.optString("key", value.getLong("id").toString()), value.optString("title"), value.optString("artist"), value.optString("album"))
        }
    }.getOrDefault(emptyList())
    return PlayerSnapshot(
        prefs.getString("now_title", "") ?: "",
        prefs.getString("now_artist", "") ?: "",
        prefs.getString("now_album", "") ?: "",
        prefs.getString("now_cover", null),
        prefs.getLong("now_cover_version", 0L),
        prefs.getString("now_lyric_current", "") ?: "",
        prefs.getString("now_lyric_next", "") ?: "",
        prefs.getFloat("now_lyric_progress", 0f).coerceIn(0f, 1f),
        prefs.getLong("now_lyric_start_elapsed", 0L),
        prefs.getLong("now_lyric_duration_ms", 0L),
        results,
        prefs.getLong("selected_lyric_id", -1L),
        prefs.getBoolean("save_lyrics_automatically", true),
        prefs.getInt("current_song_offset_ms", 0),
        prefs.getBoolean("service_started", false),
        localLrcExists(prefs.getString("now_title", "") ?: "", prefs.getString("now_artist", "") ?: ""),
        prefs.getBoolean("lyric_manual_search", false),
        prefs.getBoolean("lyric_searching", false),
        prefs.getString("lyric_search_source", null)?.takeIf { prefs.getString("lyric_search_source_key", null) == prefs.getString("now_title", "") + " - " + prefs.getString("now_artist", "") },
        prefs.getBoolean("playback_is_playing", false),
        prefs.getString("playback_package", "") ?: "",
        prefs.getString("playback_notification_icon", null),
        prefs.getString("playback_notification_package", "") ?: "",
    )
}

private fun localLrcExists(title: String, artist: String): Boolean {
    if (title.isBlank() || artist.isBlank()) return false
    val fileName = "$title - $artist.lrc".replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return File("/sdcard/Music/Liri", fileName).isFile
}

private fun clearLocalLrc(title: String, artist: String): Result<Unit> = runCatching {
    val fileName = "$title - $artist.lrc".replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val directory = File("/sdcard/Music/Liri")
    check(directory.exists() || directory.mkdirs()) { "无法创建歌词目录" }
    check(directory.isDirectory) { "歌词路径不是目录" }
    FileOutputStream(File(directory, fileName), false).use { }
}

@Composable
private fun NowPlayingCard(player: PlayerSnapshot, settingsExpanded: Boolean, onEditor: () -> Unit, onToggleSettings: () -> Unit) {
    val context = LocalContext.current
    val playingHeaderBackground = Color.Black.copy(alpha = if (isSystemInDarkTheme()) 0.24f else 0.06f)
    val cover = remember(player.cover, player.coverVersion) { player.cover?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    val playbackAppIcon = remember(player.playbackPackage) { loadApplicationIcon(context, player.playbackPackage) }
    val notificationIcon = remember(player.notificationIcon, player.notificationIconPackage, player.playbackPackage) {
        if (player.notificationIconPackage == player.playbackPackage) {
            player.notificationIcon?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        } else null
    }
    val playbackIcon = notificationIcon ?: playbackAppIcon
    var liveProgress by remember(player.title, player.currentLyric, player.lyricStartElapsed) {
        mutableFloatStateOf(player.lyricProgress)
    }
    LaunchedEffect(player.title, player.currentLyric, player.lyricStartElapsed, player.lyricDurationMs) {
        while (true) {
            liveProgress = if (player.lyricDurationMs > 0L && player.lyricStartElapsed > 0L) {
                ((SystemClock.elapsedRealtime() - player.lyricStartElapsed).toFloat() / player.lyricDurationMs).coerceIn(0f, 1f)
            } else player.lyricProgress
            delay(16)
        }
    }
    CouixCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(playingHeaderBackground),
        ) {
            AnimatedContent(
                targetState = settingsExpanded,
                transitionSpec = {
                    (fadeIn(tween(220, easing = homeExpandEasing)) + slideInVertically(tween(260, easing = homeExpandEasing)) { if (targetState) -it / 3 else it / 3 })
                        .togetherWith(fadeOut(tween(160, easing = homeCollapseEasing)) + slideOutVertically(tween(220, easing = homeCollapseEasing)) { if (targetState) it / 3 else -it / 3 })
                },
                label = "now_playing_header_transition",
            ) { expanded ->
                if (expanded) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayingArtwork(
                            cover,
                            52.dp,
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleSettings,
                            ),
                        )
                        Spacer(Modifier.width(14.dp))
                        CouixPreferenceText(
                            title = if (!player.serviceStarted && player.title.isBlank()) "等待服务启动…" else if (player.title.isBlank()) "未检测到正在播放" else player.title,
                            subtitle = if (!player.serviceStarted) "等待服务启动…" else formatArtistAlbum(player.artist, player.album),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlayingArtwork(
                            cover = cover,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .aspectRatio(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleSettings,
                                ),
                        )
                        Spacer(Modifier.height(12.dp))
                        CouixPreferenceText(
                            title = if (!player.serviceStarted && player.title.isBlank()) "等待服务启动…" else if (player.title.isBlank()) "未检测到正在播放" else player.title,
                            subtitle = if (!player.serviceStarted) "等待服务启动…" else formatArtistAlbum(player.artist, player.album),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            if (!settingsExpanded) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(1.dp)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                    )
                }
            }
            CouixPreferenceText(
                title = player.currentLyric,
                subtitle = player.nextLyric,
                subtitleColor = (if (isSystemInDarkTheme()) Color.White else Color.Black).copy(alpha = 0.55f),
                animateText = true,
                progress = liveProgress,
                contentHorizontalPadding = 16.dp,
                animateProgress = false,
                compressPunctuation = true,
                textAlign = if (settingsExpanded) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .requiredHeight(76.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }
        if (settingsExpanded) SongOffsetControl(player)
        Box(modifier = Modifier.fillMaxWidth().offset(y = (-4).dp)) {
            MainPlaybackControl(player)
            if (!settingsExpanded) {
                if (playbackIcon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .size(40.dp)
                            .clickable {
                                context.packageManager
                                    .getLaunchIntentForPackage(player.playbackPackage)
                                    ?.let(context::startActivity)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = playbackIcon,
                            contentDescription = "打开当前音乐应用",
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)),
                        )
                    }
                }
                top.yukonga.miuix.kmp.basic.IconButton(
                    onClick = onEditor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                ) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        MiuixIcons.Edit,
                        contentDescription = "编辑歌词",
                        tint = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private fun loadApplicationIcon(context: Context, packageName: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    if (packageName.isBlank()) return@runCatching null
    val drawable = context.packageManager.getApplicationIcon(packageName)
    val iconSize = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
    android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(canvas)
    }.asImageBitmap()
}.getOrNull()

private fun formatArtistAlbum(artist: String, album: String): String = when {
    artist.isBlank() -> album
    album.isBlank() -> artist
    else -> "$artist - $album"
}

@Composable
private fun PlayingArtwork(
    cover: androidx.compose.ui.graphics.ImageBitmap?,
    size: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier,
) {
    val artworkModifier = if (size != null) modifier.size(size) else modifier
    val artworkShape = RoundedCornerShape(10.dp)
    val artworkWithShadow = artworkModifier.shadow(
        elevation = 2.dp,
        shape = artworkShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.12f),
        spotColor = Color.Black.copy(alpha = 0.12f),
    )
    if (cover != null) Image(cover, contentDescription = "专辑封面", modifier = artworkWithShadow.clip(artworkShape))
    else Box(
        modifier = artworkWithShadow
            .clip(artworkShape)
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        top.yukonga.miuix.kmp.basic.Icon(
            MiuixIcons.Community,
            contentDescription = "当前歌曲",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(if (size != null) size / 2 else 56.dp),
        )
    }
}

@Composable
private fun MainPlaybackControl(player: PlayerSnapshot) {
    val context = LocalContext.current
    val iconColor = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = { context.startService(Intent(context, MainService::class.java).setAction(ACTION_EDITOR_SKIP_PREVIOUS)) },
            modifier = Modifier.size(48.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.ChevronBackward, "上一曲", tint = iconColor, modifier = Modifier.size(22.dp))
        }
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = { context.startService(Intent(context, MainService::class.java).setAction(ACTION_EDITOR_TOGGLE_PLAYBACK)) },
            modifier = Modifier.size(48.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Icon(
                if (player.playing) MiuixIcons.Pause else MiuixIcons.Play,
                if (player.playing) "暂停" else "播放",
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        }
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = { context.startService(Intent(context, MainService::class.java).setAction(ACTION_EDITOR_SKIP_NEXT)) },
            modifier = Modifier.size(48.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.ChevronForward, "下一曲", tint = iconColor, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SongOffsetControl(player: PlayerSnapshot) {
    val context = LocalContext.current
    var offsetMs by remember(player.title, player.artist, player.songOffsetMs) { mutableIntStateOf(player.songOffsetMs.coerceIn(-30000, 30000)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            BasicText(
                text = "提前 -",
                style = MiuixTheme.textStyles.body2.copy(fontFamily = mapleMono, color = MiuixTheme.colorScheme.primary),
                modifier = Modifier.clickable {
                val value = (offsetMs - 100).coerceAtLeast(-30000)
                offsetMs = value
                context.settingsPrefs().edit().putInt("current_song_offset_ms", value).apply()
            }.padding(vertical = 10.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicText(
                text = "%+.1fs".format(offsetMs / 1000f),
                style = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
            )
        }
            BasicText(
                text = "+ 延后",
                style = MiuixTheme.textStyles.body2.copy(fontFamily = mapleMono, color = MiuixTheme.colorScheme.primary),
                modifier = Modifier.clickable {
                val value = (offsetMs + 100).coerceAtMost(30000)
                offsetMs = value
                context.settingsPrefs().edit().putInt("current_song_offset_ms", value).apply()
            }.padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun LyricEditingFragment(player: PlayerSnapshot, context: Context, onEditor: () -> Unit) {
    CouixSwitchPreference(
        checked = player.autoSave,
        onCheckedChange = { setBool(context, "save_lyrics_automatically", it) },
        title = "保存自动搜索的歌词文件",
        leadingIcon = MiuixIcons.Download,
    )
    CouixItemDivider()
    var showClearDialog by remember { mutableStateOf(false) }
    CouixActionPairRow(
        leftTitle = "编辑本地歌词",
        onLeftClick = onEditor,
        rightTitle = "清空本地歌词",
        onRightClick = { showClearDialog = true },
        leftIcon = MiuixIcons.Edit,
        rightIcon = MiuixIcons.Delete,
        leftShowChevron = false,
        rightShowChevron = false,
    )
    if (showClearDialog) {
        CouixConfirmDialog(
            text = "确认清空歌词？",
            confirmLabel = "确认",
            dismissLabel = "取消",
            onConfirm = {
                showClearDialog = false
                clearLocalLrc(player.title, player.artist).onSuccess {
                    Toast.makeText(context, "歌词已清空", Toast.LENGTH_SHORT).show()
                    context.startService(Intent(context, MainService::class.java).setAction(ACTION_RELOAD_LOCAL_LYRICS))
                }.onFailure {
                    Toast.makeText(context, "清空歌词失败：${it.message ?: "无权限"}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun SearchResultCard(player: PlayerSnapshot, context: Context) {
    var searchQuery by remember(player.title, player.artist) {
        mutableStateOf(defaultLyricSearchQuery(player.title, player.artist))
    }
    LaunchedEffect(player.title, player.artist) {
        searchQuery = defaultLyricSearchQuery(player.title, player.artist)
    }
    CouixCard {
        fun requestSearch(source: String) {
            context.settingsPrefs().edit()
                .putString("lyric_search_query", searchQuery.trim())
                .putString("lyric_search_source", source)
                .putString("lyric_search_source_key", "${player.title} - ${player.artist}")
                .putBoolean("lyric_manual_search", true)
                .apply()
            Toast.makeText(context, "正在搜索${if (source == "qq") "QQ" else "网易"}歌词…", Toast.LENGTH_SHORT).show()
            context.startService(Intent(context, MainService::class.java).setAction(ACTION_SEARCH_LYRICS))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.5.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("netease" to "网易", "qq" to "QQ").forEach { (source, label) ->
                    val underlineColor = MiuixTheme.colorScheme.primary
                    Column(
                        modifier = Modifier
                            .width(32.dp)
                            .offset(y = 2.dp)
                            .clickable(enabled = !player.searchingLyrics) { requestSearch(source) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(
                            text = label,
                            style = MiuixTheme.textStyles.body2.copy(
                                color = MiuixTheme.colorScheme.primary.copy(alpha = if (player.searchingLyrics) 0.38f else 1f),
                            ),
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                                .drawBehind {
                                    if (player.searchSource == source) {
                                        drawRect(
                                            color = underlineColor,
                                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
        if (player.localLrcExists && !player.searchingLyrics && !player.manualSearch && player.results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(48.dp)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "已加载本地歌词，可手动重新发起搜索",
                    style = MiuixTheme.textStyles.body2.copy(
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (player.searchingLyrics || player.manualSearch || player.results.isNotEmpty()) {
            CouixItemDivider()
        }
        if (player.searchingLyrics) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CouixPreferenceText(title = "正在搜索歌词…", subtitle = "")
            }
        } else if (player.results.isNotEmpty()) {
            player.results.forEachIndexed { index, result ->
                if (index > 0) CouixItemDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        context.settingsPrefs().edit()
                            .putLong("selected_lyric_id", result.id)
                            .putString("selected_lyric_key", result.key)
                            .putBoolean("lyric_selection_manual", true)
                            .apply()
                    }.padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CouixPreferenceText(
                        title = result.title,
                        subtitle = "${result.artist} - ${result.album}",
                        // modifier = Modifier.weight(1f),
                        titleColor = if (result.id == player.selectedId) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}


private data class PermissionEntry(val title: String, val subtitle: String, val enabled: Boolean, val open: () -> Unit)

@Composable
private fun PermissionScreen(refreshToken: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = buildList {
        add(PermissionEntry("通知读取权限", "若播放信息始终没有更新，请尝试重新授权", isNotificationListenerEnabled(context)) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        add(PermissionEntry("存储空间权限", "保存歌词文件用于手动选词", hasStorageAccess(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            } else {
                (context as? ComponentActivity)?.requestPermissions(
                    arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"),
                    1001,
                )
            }
        })
        add(PermissionEntry("后台运行权限", "关闭电池优化，避免服务被系统暂停", isIgnoringBatteryOptimizations(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
        })
        add(PermissionEntry("悬浮窗权限", "通过悬浮窗显示当前歌词", Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        })
    }
    val listState = rememberLazyListState()
    Scaffold(containerColor = MiuixTheme.colorScheme.surface, contentWindowInsets = WindowInsets(0.dp), topBar = { CouixTopAppBar("授予系统权限", dividerProgress = couixTopBarDividerProgress(listState), navigationIcon = { CouixBackButton(onBack) }) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding).couixOverscroll(listState)) {
            items(entries) { entry ->
                CouixCard {
                    CouixSwitchPreference(checked = entry.enabled, onCheckedChange = { entry.open() }, title = entry.title, subtitle = entry.subtitle)
                }
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any { component ->
        component.packageName == context.packageName && component.className == MainService::class.java.name
    }
}

private fun hasStorageAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
} else {
    context.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun StyleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.settingsPrefs() }
    val screenWidthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val widthMax = screenWidthDp.coerceAtLeast(100)
    val widthDefault = DEFAULT_WIDTH.coerceIn(100, widthMax)
    val topDefault = defaultTop(context)
    val topValue = if (prefs.contains(KEY_TOP)) {
        prefs.getInt(KEY_TOP, topDefault).coerceIn(0, 100)
    } else if (prefs.contains("overlay_top_percent")) {
        (prefs.getInt("overlay_top_percent", 0) * 10).coerceIn(0, 100)
    } else {
        val legacyDp = prefs.getInt("overlay_top_dp", -1)
        if (legacyDp >= 0) {
            (legacyDp * context.resources.displayMetrics.density * 100f / context.resources.displayMetrics.heightPixels.coerceAtLeast(1))
                .roundToInt().coerceIn(0, 100)
        } else topDefault
    }
    val listState = rememberLazyListState()
    Scaffold(containerColor = MiuixTheme.colorScheme.surface, contentWindowInsets = WindowInsets(0.dp), topBar = { CouixTopAppBar("位置与样式", dividerProgress = couixTopBarDividerProgress(listState), navigationIcon = { CouixBackButton(onBack) }) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding).couixOverscroll(listState)) {
        item {
            CouixSmallTitle("悬浮歌词位置")
            CouixCard {
                StyleSlider("左边界", "dp", prefs.getInt(KEY_LEFT, DEFAULT_LEFT), 0, 64, DEFAULT_LEFT) { setInt(context, KEY_LEFT, it) }
                CouixItemDivider()
                StyleSlider("Y 坐标（上边距）", "%", topValue, 0, 100, topDefault, valueText = { "%.1f%%".format(Locale.ROOT, it / 10f) }) { setInt(context, KEY_TOP, it) }
                CouixItemDivider()
                StyleSlider("宽度", "dp", prefs.getInt(KEY_WIDTH, widthDefault), 100, widthMax, widthDefault) { setInt(context, KEY_WIDTH, it) }
                CouixItemDivider()
                StyleSlider("字号", "sp", prefs.getInt(KEY_FONT, DEFAULT_FONT), 10, 18, DEFAULT_FONT) { setInt(context, KEY_FONT, it) }
            }
        }
        item {
            CouixSmallTitle("避开通知图标")
            CouixGroup(
                items = listOf(
                    SwitchItem(
                        key = KEY_SHIFT_ON_NOTIFICATION,
                        label = "有其它通知时右移",
                        sliderKey = KEY_SHIFT_ON_NOTIFICATION_DP,
                        sliderMin = 0,
                        sliderMax = 128,
                        sliderDefault = 0,
                        sliderUnit = "dp",
                    ),
                    SwitchItem(
                        key = KEY_SHIFT_EACH_NOTIFICATION,
                        label = "每个通知额外右移",
                        sliderKey = KEY_SHIFT_EACH_NOTIFICATION_DP,
                        sliderMin = 0,
                        sliderMax = 128,
                        sliderDefault = 0,
                        sliderUnit = "dp",
                    ),
                ),
                prefs = prefs,
                ctx = context,
            )
        }
        item {
            CouixSmallTitle("歌词动画")
            CouixCard {
                StyleSlider("切入角度", "°", prefs.getInt(KEY_ANGLE, DEFAULT_ANGLE), 0, 360, DEFAULT_ANGLE, step = 15) { setInt(context, KEY_ANGLE, it) }
                CouixItemDivider()
                StyleSlider("切入距离", "dp", prefs.getInt(KEY_DISTANCE, DEFAULT_DISTANCE), 8, 64, DEFAULT_DISTANCE) { setInt(context, KEY_DISTANCE, it) }
                CouixItemDivider()
                StyleSlider("切入切出持续时间", "ms", prefs.getInt(KEY_DURATION, DEFAULT_DURATION), 100, 600, DEFAULT_DURATION) { setInt(context, KEY_DURATION, it) }
            }
        }
        }
    }
}

@Composable
private fun StyleSlider(title: String, unit: String, value: Int, min: Int, max: Int, default: Int, step: Int = 1, valueText: ((Int) -> String)? = null, onChange: (Int) -> Unit) {
    fun snap(raw: Int): Int = (min + ((raw - min + step / 2) / step) * step).coerceIn(min, max)
    var current by remember(value, min, max, step) { mutableIntStateOf(snap(value)) }
    CouixSliderPreference(
        title = title,
        value = current,
        unit = unit,
        min = min,
        max = max,
        valueText = valueText?.invoke(current),
        onValueChange = {
            current = snap((min + it * (max - min)).toInt())
            onChange(current)
        },
    )
}

@Composable
internal fun CouixBackButton(onBack: () -> Unit) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
        top.yukonga.miuix.kmp.basic.Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(COUIX_BACK_ICON),
        )
    }
}

private const val KEY_LEFT = "overlay_left_dp"
private const val KEY_TOP = "overlay_top_percent_tenths"
private const val KEY_WIDTH = "overlay_width_dp"
private const val KEY_FONT = "overlay_font_sp"
private const val KEY_SHIFT_ON_NOTIFICATION = "overlay_shift_on_notification"
private const val KEY_SHIFT_ON_NOTIFICATION_DP = "overlay_shift_on_notification_dp"
private const val KEY_SHIFT_EACH_NOTIFICATION = "overlay_shift_each_notification"
private const val KEY_SHIFT_EACH_NOTIFICATION_DP = "overlay_shift_each_notification_dp"
private const val KEY_ANGLE = "lyric_animation_angle"
private const val KEY_DISTANCE = "lyric_animation_distance_dp"
private const val KEY_DURATION = "lyric_animation_duration_ms"
private const val DEFAULT_LEFT = 12
private const val DEFAULT_WIDTH = 236
private const val DEFAULT_FONT = 13
private const val DEFAULT_ANGLE = 180
private const val DEFAULT_DISTANCE = 32
private const val DEFAULT_DURATION = 280

private fun defaultTop(context: Context): Int {
    val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val px = if (id != 0) context.resources.getDimensionPixelSize(id) else (24 * context.resources.displayMetrics.density).toInt()
    val topPx = (px - (20 * context.resources.displayMetrics.density).toInt()).coerceAtLeast(0)
    return (topPx * 1000f / context.resources.displayMetrics.heightPixels.coerceAtLeast(1)).roundToInt().coerceIn(0, 100)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val manager = context.getSystemService(PowerManager::class.java)
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || manager.isIgnoringBatteryOptimizations(context.packageName)
}
private fun colorOSAccentColor(context: Context, fallback: Long) = androidx.compose.ui.graphics.Color(fallback)
