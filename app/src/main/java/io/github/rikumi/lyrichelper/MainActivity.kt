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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import android.widget.ImageView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.json.JSONArray
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.Volley
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

private enum class Page { HOME, PERMISSIONS, STYLE, DISPLAY_SAVE, EDITOR }

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
            Page.HOME -> HomeScreen(onPermissions = { navigate(Page.PERMISSIONS) }, onStyle = { navigate(Page.STYLE) }, onDisplaySave = { navigate(Page.DISPLAY_SAVE) }, onEditor = { navigate(Page.EDITOR) })
            Page.PERMISSIONS -> PermissionScreen(refreshToken) { navigate(Page.HOME) }
            Page.STYLE -> StyleScreen { navigate(Page.HOME) }
            Page.DISPLAY_SAVE -> DisplaySaveScreen { navigate(Page.HOME) }
            Page.EDITOR -> LocalLrcEditorScreen { navigate(Page.HOME) }
        }
    }
}

@Composable
private fun HomeScreen(onPermissions: () -> Unit, onStyle: () -> Unit, onDisplaySave: () -> Unit, onEditor: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf(readPlayerSnapshot(context)) }
    var settingsExpanded by remember { mutableStateOf(true) }
    val nowPlayingExpanded = !settingsExpanded
    fun toggleHomeExpansion() {
        val showLargeArtwork = settingsExpanded
        settingsExpanded = !settingsExpanded
        if (showLargeArtwork) {
            context.startService(Intent(context, MainService::class.java).setAction(ACTION_ENSURE_CURRENT_ALBUM_ART))
        }
    }
    LaunchedEffect(nowPlayingExpanded, player.title, player.artist, player.album, player.cover, player.playbackPackage) {
        if (nowPlayingExpanded) {
            context.startService(Intent(context, MainService::class.java).setAction(ACTION_ENSURE_CURRENT_ALBUM_ART))
        }
    }
    DisposableEffect(context) {
        val preferences = context.settingsPrefs()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            player = readPlayerSnapshot(context)
            if (preferences.getBoolean("show_large_now_playing", false)) {
                preferences.edit().remove("show_large_now_playing").apply()
                settingsExpanded = false
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val listState = rememberLazyListState()
    val overscrollOffset = remember { mutableFloatStateOf(0f) }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            CouixLargeTitle(
                title = "Liri Lyrics",
                dividerProgress = couixTopBarDividerProgress(listState, overscrollOffset),
                actions = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = ::toggleHomeExpansion) {
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
                        CouixCategoryRow(MiuixIcons.Download, "界面显示与保存", onDisplaySave)
                        CouixItemDivider()
                        LyricEditingFragment(player, context, onEditor)
                    }
                }
            }
            item {
                if (settingsExpanded) CouixSmallTitle("正在播放")
                NowPlayingCard(player, nowPlayingExpanded, onEditor, ::toggleHomeExpansion)
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

private data class SearchResult(val id: Long, val key: String, val title: String, val artist: String, val album: String, val cover: String)
private data class PlayerSnapshot(val title: String, val artist: String, val album: String, val cover: String?, val coverVersion: Long, val currentLyric: String, val nextLyric: String, val lyricProgress: Float, val lyricStartElapsed: Long, val lyricDurationMs: Long, val results: List<SearchResult>, val selectedId: Long, val autoSave: Boolean, val saveAlbumArt: Boolean, val stripTitleParentheses: Boolean, val songOffsetMs: Int, val serviceStarted: Boolean, val localLrcExists: Boolean, val manualSearch: Boolean, val searchingLyrics: Boolean, val searchSource: String?, val playing: Boolean, val playbackPackage: String, val notificationIcon: String?, val notificationIconPackage: String, val previewLyric: String, val previewLyricVersion: Long, val previewCoverUrl: String, val albumArtVersion: Long, val playbackPositionMs: Long, val playbackUpdatedElapsed: Long)

private fun readPlayerSnapshot(context: Context): PlayerSnapshot {
    val prefs = context.settingsPrefs()
    val results = runCatching {
        val array = JSONArray(prefs.getString("lyric_search_results", "[]"))
        (0 until array.length()).map { item ->
            val value = array.getJSONObject(item)
            SearchResult(value.getLong("id"), value.optString("key", value.getLong("id").toString()), value.optString("title"), value.optString("artist"), value.optString("album"), value.optString("cover"))
        }
    }.getOrDefault(emptyList())
    return PlayerSnapshot(
        prefs.getString("now_title", "") ?: "",
        prefs.getString("now_artist", "") ?: "",
        prefs.getString("now_album", "") ?: "",
        localAlbumArt(prefs.getString("now_title", "") ?: "", prefs.getString("now_artist", "") ?: "")
            ?: prefs.getString("now_cover", null)?.takeIf { File(it).isFile },
        maxOf(prefs.getLong("now_cover_version", 0L), prefs.getLong("album_art_version", 0L)),
        prefs.getString("now_lyric_current", "") ?: "",
        prefs.getString("now_lyric_next", "") ?: "",
        prefs.getFloat("now_lyric_progress", 0f).coerceIn(0f, 1f),
        prefs.getLong("now_lyric_start_elapsed", 0L),
        prefs.getLong("now_lyric_duration_ms", 0L),
        results,
        prefs.getLong("selected_lyric_id", -1L),
        prefs.getBoolean("save_lyrics_automatically", true),
        prefs.getBoolean("save_album_art_automatically", true),
        prefs.getBoolean("strip_title_parentheses", false),
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
        prefs.getString("preview_lyric", "") ?: "",
        prefs.getLong("preview_lyric_version", 0L),
        prefs.getString("preview_cover_url", "") ?: "",
        prefs.getLong("album_art_version", 0L),
        prefs.getLong("playback_position_ms", 0L),
        prefs.getLong("playback_updated_elapsed", 0L),
    )
}

private fun localAlbumArt(title: String, artist: String): String? {
    if (title.isBlank()) return null
    val file = File("/sdcard/Music/Liri/.albumart", "$title - $artist.png".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
    return file.takeIf { it.isFile }?.absolutePath
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
private fun NowPlayingCard(player: PlayerSnapshot, nowPlayingExpanded: Boolean, onEditor: () -> Unit, onToggleSettings: () -> Unit) {
    val context = LocalContext.current
    val playingHeaderBackground = Color.Black.copy(alpha = if (isSystemInDarkTheme()) 0.24f else 0.06f)
    val cover by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, player.cover, player.coverVersion) {
        value = withContext(Dispatchers.IO) {
            player.cover?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                ?: CurrentAlbumArtMemory.bitmap
                    ?.takeIf { CurrentAlbumArtMemory.matches(player.title, player.artist) }
                    ?.asImageBitmap()
        }
    }
    val playbackAppIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, player.playbackPackage) {
        value = withContext(Dispatchers.IO) { loadApplicationIcon(context, player.playbackPackage) }
    }
    val notificationIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, player.notificationIcon, player.notificationIconPackage, player.playbackPackage) {
        value = withContext(Dispatchers.IO) {
            if (player.notificationIconPackage == player.playbackPackage) {
                player.notificationIcon?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
            } else null
        }
    }
    val playbackIcon = notificationIcon ?: playbackAppIcon
    var liveProgress by remember(player.title, player.currentLyric, player.lyricStartElapsed, player.playing) {
        mutableFloatStateOf(player.lyricProgress)
    }
    LaunchedEffect(player.title, player.currentLyric, player.lyricStartElapsed, player.lyricDurationMs, player.playing) {
        while (true) {
            liveProgress = if (player.playing && player.lyricDurationMs > 0L && player.lyricStartElapsed > 0L) {
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
                targetState = nowPlayingExpanded,
                transitionSpec = {
                    (fadeIn(tween(220, easing = homeExpandEasing)) + slideInVertically(tween(260, easing = homeExpandEasing)) { if (targetState) -it / 3 else it / 3 })
                        .togetherWith(fadeOut(tween(160, easing = homeCollapseEasing)) + slideOutVertically(tween(220, easing = homeCollapseEasing)) { if (targetState) it / 3 else -it / 3 })
                },
                label = "now_playing_header_transition",
            ) { expanded ->
                if (expanded) {
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
                            title = if (!player.serviceStarted && player.title.isBlank()) "等待服务启动…" else if (player.title.isBlank()) "未检测到正在播放" else displaySongTitle(player.title, player.stripTitleParentheses),
                            subtitle = if (!player.serviceStarted) "等待服务启动…" else formatArtistAlbum(player.artist, player.album),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                } else {
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
                            title = if (!player.serviceStarted && player.title.isBlank()) "等待服务启动…" else if (player.title.isBlank()) "未检测到正在播放" else displaySongTitle(player.title, player.stripTitleParentheses),
                            subtitle = if (!player.serviceStarted) "等待服务启动…" else formatArtistAlbum(player.artist, player.album),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (nowPlayingExpanded) {
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
                textAlign = if (nowPlayingExpanded) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .requiredHeight(76.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }
        if (!nowPlayingExpanded) SongOffsetControl(player)
        Box(modifier = Modifier.fillMaxWidth().offset(y = (-4).dp)) {
            MainPlaybackControl(player)
            if (nowPlayingExpanded) {
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
private fun DisplaySaveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.settingsPrefs() }
    var saveLyrics by remember { mutableStateOf(prefs.getBoolean("save_lyrics_automatically", true)) }
    var saveAlbumArt by remember { mutableStateOf(prefs.getBoolean("save_album_art_automatically", true)) }
    var stripTitleParentheses by remember { mutableStateOf(prefs.getBoolean("strip_title_parentheses", false)) }
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            CouixTopAppBar("界面显示与保存", dividerProgress = couixTopBarDividerProgress(listState), navigationIcon = { CouixBackButton(onBack) })
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding).couixOverscroll(listState),
        ) {
            item {
                CouixCard {
                    CouixSwitchPreference(
                        checked = stripTitleParentheses,
                        onCheckedChange = {
                            stripTitleParentheses = it
                            setBool(context, "strip_title_parentheses", it)
                        },
                        title = "界面显示时去除标题括号",
                    )
                    CouixItemDivider()
                    CouixSwitchPreference(
                        checked = saveLyrics,
                        onCheckedChange = {
                            saveLyrics = it
                            setBool(context, "save_lyrics_automatically", it)
                        },
                        title = "保存自动搜索的歌词文件",
                    )
                    CouixItemDivider()
                    CouixSwitchPreference(
                        checked = saveAlbumArt,
                        onCheckedChange = {
                            saveAlbumArt = it
                            setBool(context, "save_album_art_automatically", it)
                        },
                        title = "保存自动匹配的专辑封面",
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(player: PlayerSnapshot, context: Context) {
    var expandedResultKey by remember(player.title) { mutableStateOf<String?>(null) }
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
            modifier = Modifier.fillMaxWidth().requiredHeight(56.dp).padding(start = 8.dp, end = 16.dp),
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
                val expanded = expandedResultKey == result.key
                val previousExpanded = index > 0 && expandedResultKey == player.results[index - 1].key
                if (index > 0 && !expanded && !previousExpanded) CouixItemDivider()
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (expanded) {
                        SearchResultPreview(player, result, context) {
                            expandedResultKey = null
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedResultKey = result.key
                                context.settingsPrefs().edit()
                                    .putLong("preview_lyric_id", result.id)
                                    .putString("preview_lyric_key", result.key)
                                    .putString("preview_lyric_source", player.searchSource ?: "netease")
                                    .putString("preview_lyric_title", result.title)
                                    .putString("preview_lyric_artist", result.artist)
                                    .putString("preview_album_art_url", result.cover)
                                    .putString("preview_album_art_title", result.title)
                                    .putString("preview_album_art_artist", result.artist)
                                    .remove("preview_lyric")
                                    .remove("preview_cover_url")
                                    .apply()
                                context.startService(Intent(context, MainService::class.java).setAction(ACTION_PREVIEW_SEARCH_LYRICS))
                            }.padding(horizontal = 16.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CouixPreferenceText(
                                title = result.title,
                                subtitle = "${result.artist} - ${result.album}",
                                titleColor = if (result.id == player.selectedId) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultPreview(player: PlayerSnapshot, result: SearchResult, context: Context, onCollapse: () -> Unit) {
    val coverUrl = player.previewCoverUrl.ifBlank { result.cover }
    var previewCover by remember(result.key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    DisposableEffect(result.key, coverUrl) {
        val requestQueue = Volley.newRequestQueue(context)
        val request = if (coverUrl.isNotBlank()) ImageRequest(
            coverUrl,
            { bitmap -> previewCover = bitmap.asImageBitmap() },
            0,
            0,
            ImageView.ScaleType.CENTER_CROP,
            android.graphics.Bitmap.Config.ARGB_8888,
            {},
        ) else null
        request?.let { requestQueue.add(it) }
        onDispose { requestQueue.cancelAll { true }; requestQueue.stop() }
    }
    val latestPlayer by rememberUpdatedState(player)
    var position by remember(result.key, player.previewLyricVersion) { mutableLongStateOf(0L) }
    LaunchedEffect(result.key, player.previewLyricVersion) {
        while (true) {
            val playbackPosition = latestPlayer.playbackPositionMs + if (latestPlayer.playing && latestPlayer.playbackUpdatedElapsed > 0L) {
                (SystemClock.elapsedRealtime() - latestPlayer.playbackUpdatedElapsed).coerceAtLeast(0L)
            } else 0L
            position = playbackPosition.coerceAtLeast(0L)
            delay(16)
        }
    }
    val lines = remember(player.previewLyric, player.previewLyricVersion) { parseLyricText(player.previewLyric) }
    val currentIndex = lines.indexOfLast { it.timeMs.toLong() <= position }
    val currentLine = lines.getOrNull(currentIndex)
    val nextLine = lines.getOrNull(currentIndex + 1)
    val current = currentLine?.text.orEmpty()
    val next = nextLine?.text.orEmpty()
    val previewProgress = if (currentLine != null && nextLine != null && nextLine.timeMs > currentLine.timeMs) {
        ((position - currentLine.timeMs) / (nextLine.timeMs - currentLine.timeMs).toFloat()).coerceIn(0f, 1f)
    } else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSystemInDarkTheme()) {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                } else {
                    Color.Black.copy(alpha = 0.06f)
                },
            ),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(tween(260)) { -it / 2 } + fadeIn(tween(220)),
            ) {
                PlayingArtwork(previewCover, 52.dp)
            }
            Spacer(Modifier.width(14.dp))
            CouixPreferenceText(title = result.title, subtitle = "${result.artist} - ${result.album}", modifier = Modifier.weight(1f))
        }
        CouixPreferenceText(
            title = current,
            subtitle = next,
            subtitleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            animateText = true,
            progress = previewProgress,
            animateProgress = false,
            compressPunctuation = true,
            contentHorizontalPadding = 16.dp,
            modifier = Modifier.requiredHeight(76.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "使用歌词",
                    style = MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.primary),
                    modifier = Modifier.clickable {
                        context.settingsPrefs().edit()
                            .putLong("selected_lyric_id", result.id)
                            .putString("selected_lyric_key", result.key)
                            .putBoolean("lyric_selection_manual", true)
                            .apply()
                    }.padding(vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "使用专辑封面",
                    style = MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.primary),
                    modifier = Modifier.clickable {
                        context.settingsPrefs().edit()
                            .putString("preview_album_art_url", player.previewCoverUrl.ifBlank { result.cover })
                            .putString("preview_album_art_title", result.title)
                            .putString("preview_album_art_artist", result.artist)
                            .apply()
                        context.startService(Intent(context, MainService::class.java).setAction(ACTION_SAVE_SEARCH_ALBUM_ART))
                    }.padding(vertical = 8.dp),
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
    val entries = remember(refreshToken) {
        buildList {
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
    }
    val listState = rememberLazyListState()
    Scaffold(containerColor = MiuixTheme.colorScheme.surface, contentWindowInsets = WindowInsets(0.dp), topBar = { CouixTopAppBar("授予系统权限", dividerProgress = couixTopBarDividerProgress(listState), navigationIcon = { CouixBackButton(onBack) }) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding).couixOverscroll(listState)) {
            item {
                CouixCard {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) CouixItemDivider()
                    CouixSwitchPreference(checked = entry.enabled, onCheckedChange = { entry.open() }, title = entry.title, subtitle = entry.subtitle)
                    }
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
    LaunchedEffect(Unit) {
        val editor = prefs.edit()
        fun migrate(valueKey: String, enabledKey: String, default: Int) {
            if (!prefs.contains(enabledKey) && prefs.contains(valueKey)) {
                editor.putBoolean(enabledKey, prefs.getInt(valueKey, default) != default)
            }
        }
        if (!prefs.contains(KEY_TOP) && topValue != topDefault) editor.putInt(KEY_TOP, topValue)
        migrate(KEY_LEFT, KEY_LEFT_ENABLED, DEFAULT_LEFT)
        if (!prefs.contains(KEY_TOP_ENABLED) && topValue != topDefault) editor.putBoolean(KEY_TOP_ENABLED, true)
        migrate(KEY_WIDTH, KEY_WIDTH_ENABLED, widthDefault)
        migrate(KEY_FONT, KEY_FONT_ENABLED, DEFAULT_FONT)
        migrate(KEY_ANGLE, KEY_ANGLE_ENABLED, DEFAULT_ANGLE)
        migrate(KEY_DISTANCE, KEY_DISTANCE_ENABLED, DEFAULT_DISTANCE)
        migrate(KEY_DURATION, KEY_DURATION_ENABLED, DEFAULT_DURATION)
        migrate(KEY_SHADOW_DIRECTION, KEY_SHADOW_DIRECTION_ENABLED, DEFAULT_SHADOW_DIRECTION)
        migrate(KEY_SHADOW_RADIUS, KEY_SHADOW_RADIUS_ENABLED, DEFAULT_SHADOW_RADIUS)
        editor.apply()
    }
    val listState = rememberLazyListState()
    Scaffold(containerColor = MiuixTheme.colorScheme.surface, contentWindowInsets = WindowInsets(0.dp), topBar = { CouixTopAppBar("位置与样式", dividerProgress = couixTopBarDividerProgress(listState), navigationIcon = { CouixBackButton(onBack) }) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding).couixOverscroll(listState)) {
        item {
            CouixSmallTitle("悬浮歌词位置")
            CouixGroup(
                items = listOf(
                    SwitchItem(KEY_LEFT_ENABLED, "调整左边界", sliderKey = KEY_LEFT, sliderMin = 0, sliderMax = 64, sliderDefault = DEFAULT_LEFT),
                    SwitchItem(KEY_TOP_ENABLED, "调整 Y 坐标（上边距）", sliderKey = KEY_TOP, sliderMin = 0, sliderMax = 100, sliderDefault = topDefault, sliderUnit = "%", sliderValueText = { "%.1f%%".format(Locale.ROOT, it / 10f) }),
                    SwitchItem(KEY_WIDTH_ENABLED, "调整宽度", sliderKey = KEY_WIDTH, sliderMin = 100, sliderMax = widthMax, sliderDefault = widthDefault),
                    SwitchItem(KEY_FONT_ENABLED, "调整字号", sliderKey = KEY_FONT, sliderMin = 10, sliderMax = 18, sliderDefault = DEFAULT_FONT, sliderUnit = "sp"),
                ),
                prefs = prefs,
                ctx = context,
            )
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
            CouixGroup(
                items = listOf(
                    SwitchItem(KEY_ANGLE_ENABLED, "调整切入角度", sliderKey = KEY_ANGLE, sliderMin = 0, sliderMax = 360, sliderDefault = DEFAULT_ANGLE, sliderUnit = "°", sliderStep = 15),
                    SwitchItem(KEY_DISTANCE_ENABLED, "调整切入距离", sliderKey = KEY_DISTANCE, sliderMin = 8, sliderMax = 64, sliderDefault = DEFAULT_DISTANCE),
                    SwitchItem(KEY_DURATION_ENABLED, "调整切入切出持续时间", sliderKey = KEY_DURATION, sliderMin = 50, sliderMax = 600, sliderDefault = DEFAULT_DURATION, sliderUnit = "ms", sliderStep = 50),
                ),
                prefs = prefs,
                ctx = context,
            )
        }
        item {
            CouixSmallTitle("歌词阴影")
            CouixGroup(
                items = listOf(
                    SwitchItem(KEY_SHADOW_DIRECTION_ENABLED, "调整阴影方向", sliderKey = KEY_SHADOW_DIRECTION, sliderMin = 0, sliderMax = 360, sliderDefault = DEFAULT_SHADOW_DIRECTION, sliderUnit = "°", sliderStep = 15),
                    SwitchItem(KEY_SHADOW_RADIUS_ENABLED, "调整阴影半径", sliderKey = KEY_SHADOW_RADIUS, sliderMin = 0, sliderMax = 12, sliderDefault = DEFAULT_SHADOW_RADIUS),
                ),
                prefs = prefs,
                ctx = context,
            )
        }
        }
    }
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

internal const val KEY_LEFT = "overlay_left_dp"
internal const val KEY_LEFT_ENABLED = "overlay_left_dp_enabled"
internal const val KEY_TOP = "overlay_top_percent_tenths"
internal const val KEY_TOP_ENABLED = "overlay_top_percent_enabled"
internal const val KEY_WIDTH = "overlay_width_dp"
internal const val KEY_WIDTH_ENABLED = "overlay_width_enabled"
internal const val KEY_FONT = "overlay_font_sp"
internal const val KEY_FONT_ENABLED = "overlay_font_enabled"
private const val KEY_SHIFT_ON_NOTIFICATION = "overlay_shift_on_notification"
private const val KEY_SHIFT_ON_NOTIFICATION_DP = "overlay_shift_on_notification_dp"
private const val KEY_SHIFT_EACH_NOTIFICATION = "overlay_shift_each_notification"
private const val KEY_SHIFT_EACH_NOTIFICATION_DP = "overlay_shift_each_notification_dp"
internal const val KEY_ANGLE = "lyric_animation_angle"
internal const val KEY_ANGLE_ENABLED = "lyric_animation_angle_enabled"
internal const val KEY_DISTANCE = "lyric_animation_distance_dp"
internal const val KEY_DISTANCE_ENABLED = "lyric_animation_distance_enabled"
internal const val KEY_DURATION = "lyric_animation_duration_ms"
internal const val KEY_DURATION_ENABLED = "lyric_animation_duration_enabled"
internal const val KEY_SHADOW_DIRECTION = "lyric_shadow_direction"
internal const val KEY_SHADOW_DIRECTION_ENABLED = "lyric_shadow_direction_enabled"
internal const val KEY_SHADOW_RADIUS = "lyric_shadow_radius_dp"
internal const val KEY_SHADOW_RADIUS_ENABLED = "lyric_shadow_radius_enabled"
internal const val DEFAULT_LEFT = 12
internal const val DEFAULT_WIDTH = 236
internal const val DEFAULT_FONT = 13
internal const val DEFAULT_ANGLE = 180
internal const val DEFAULT_DISTANCE = 32
internal const val DEFAULT_DURATION = 300
internal const val DEFAULT_SHADOW_DIRECTION = 180
internal const val DEFAULT_SHADOW_RADIUS = 3

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
