package io.github.rikumi.lyrichelper

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.MediaMetadata
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.graphics.Color
import android.graphics.PixelFormat
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.SparseArray
import android.widget.Toast
import android.app.Notification
import android.app.NotificationManager
import androidx.core.util.forEach
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

const val ACTION_EDITOR_TOGGLE_PLAYBACK = "io.github.rikumi.lyrichelper.action.EDITOR_TOGGLE_PLAYBACK"
const val ACTION_EDITOR_START = "io.github.rikumi.lyrichelper.action.EDITOR_START"
const val ACTION_EDITOR_STOP = "io.github.rikumi.lyrichelper.action.EDITOR_STOP"
const val ACTION_SEARCH_LYRICS = "io.github.rikumi.lyrichelper.action.SEARCH_LYRICS"
const val ACTION_RELOAD_LOCAL_LYRICS = "io.github.rikumi.lyrichelper.action.RELOAD_LOCAL_LYRICS"
const val EXTRA_EDITOR_TITLE = "editor_title"
const val EXTRA_EDITOR_ARTIST = "editor_artist"
const val EXTRA_EDITOR_PACKAGE = "editor_package"

class MainService : NotificationListenerService() {

    var handler = Handler()
    var currentMusic = ""
    var currentMusicLyrics = SparseArray<String>()
    var isPlaying = false

    lateinit var queue: RequestQueue

    private var currentLine = ""
    private var nextLine = ""
    private var currentLineStartMs = Long.MIN_VALUE
    private var loadedSongOffsetMs = Int.MIN_VALUE
    private val channel = "lyrics"
    private lateinit var windowManager: WindowManager
    private var lyricWindow: LyricWindow? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var activeResultId = -1
    private var requestedResultId = -1
    private var publishedPlayerKey = ""
    private var observedSessionToken: MediaSession.Token? = null
    private var observedController: MediaController? = null
    private var observedTitle = ""
    private var observedArtist = ""
    private var observedAlbum = ""
    private var observedPackageName = ""
    private var lyricBoundaryRunnable: Runnable? = null
    private var editorPauseRunnable: Runnable? = null
    // 数据来源：NoHeartPen/Kanji2Hanzi 的公开《简日汉字对照表》。
    private val commonSimplifiedToJapanese by lazy { loadKanjiTable("simplified_to_japanese_common.tsv") }
    private val settingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val overlaySettingChanged = key?.startsWith("overlay_") == true || key?.startsWith("lyric_animation_") == true
        val searchRequested = key == "lyric_search_requested" && settingsPrefs().getBoolean("lyric_search_requested", false)
        if (searchRequested || overlaySettingChanged || key == "current_song_offset_ms" || key == "selected_lyric_id" || key == "lyric_selection_manual" || key == "lyric_text_replacements" || key?.startsWith("replace_") == true) {
            handler.post {
                if (searchRequested) {
                    settingsPrefs().edit().putBoolean("lyric_search_requested", false).apply()
                    searchCurrentSongLyrics()
                }
                if (overlaySettingChanged) applyOverlaySettings()
                if (key == "current_song_offset_ms") reloadLyricsAfterSongOffsetChange()
                if (key == "lyric_text_replacements" || key?.startsWith("replace_") == true) reloadLyricsAfterTextReplacementChange()
                refreshCurrentLyricDisplay()
            }
        }
    }

    private fun searchCurrentSongLyrics() {
        val prefs = settingsPrefs()
        val title = observedTitle.ifBlank { prefs.getString("now_title", "") ?: "" }
        val artist = observedArtist.ifBlank { prefs.getString("now_artist", "") ?: "" }
        val album = observedAlbum.ifBlank { prefs.getString("now_album", "") ?: "" }
        if (title.isBlank() || artist.isBlank()) {
            prefs.edit().putBoolean("lyric_searching", false).apply()
            showSearchToast("未检测到正在播放的歌曲")
            return
        }
        if (prefs.getString("lyric_search_query", "").isNullOrBlank()) {
            prefs.edit().putString("lyric_search_query", "$title $artist").apply()
        }
        prefs.edit().putBoolean("lyric_force_search", true).apply()
        refreshLyrics(title, artist, album, observedController?.playbackState?.position ?: 0L)
    }
    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            handler.post { update() }
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return
            if (title != observedTitle) return
            val cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (cover != null) {
                handler.post { publishPlayer(observedTitle, observedArtist, observedAlbum, null, cover) }
            }
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            val title = observedTitle
            if (title.isBlank()) return
            handler.post {
                isPlaying = state?.state == STATE_PLAYING
                if (!isPlaying) {
                    lyricBoundaryRunnable?.let { handler.removeCallbacks(it) }
                    lyricBoundaryRunnable = null
                }
                scheduleEditorPause()
                refreshLyrics(title, observedArtist, observedAlbum, state?.position ?: 0L)
            }
        }

        override fun onSessionDestroyed() {
            observedController?.unregisterCallback(this)
            observedController = null
            observedSessionToken = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        queue = Volley.newRequestQueue(this)
        settingsPrefs().edit().putBoolean("service_started", true).apply()
        settingsPrefs().registerOnSharedPreferenceChangeListener(settingsChangeListener)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showLyricWindow()
    }

    override fun onDestroy() {
        observedController?.unregisterCallback(mediaControllerCallback)
        settingsPrefs().edit().putBoolean("service_started", false).apply()
        settingsPrefs().unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
        lyricBoundaryRunnable?.let { handler.removeCallbacks(it) }
        lyricBoundaryRunnable = null
        editorPauseRunnable?.let { handler.removeCallbacks(it) }
        editorPauseRunnable = null
        observedController = null
        lyricWindow?.let { runCatching { windowManager.removeView(it) } }
        lyricWindow = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return super.onBind(intent)!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEARCH_LYRICS -> {
                showSearchToast("正在搜索歌词…")
                searchCurrentSongLyrics()
            }
            ACTION_RELOAD_LOCAL_LYRICS -> {
                if (observedTitle.isNotBlank() && observedArtist.isNotBlank()) {
                    currentMusic = ""
                    refreshLyrics(observedTitle, observedArtist, observedAlbum, observedController?.playbackState?.position ?: 0L)
                }
            }
            ACTION_EDITOR_START -> {
                settingsPrefs().edit()
                    .putBoolean("editor_active", true)
                    .putString("editor_target_title", intent.getStringExtra(EXTRA_EDITOR_TITLE).orEmpty())
                    .putString("editor_target_artist", intent.getStringExtra(EXTRA_EDITOR_ARTIST).orEmpty())
                    .putString("editor_target_package", intent.getStringExtra(EXTRA_EDITOR_PACKAGE).orEmpty())
                    .apply()
                scheduleEditorPause()
            }
            ACTION_EDITOR_STOP -> {
                settingsPrefs().edit().putBoolean("editor_active", false).apply()
                editorPauseRunnable?.let { handler.removeCallbacks(it) }
                editorPauseRunnable = null
            }
            ACTION_EDITOR_TOGGLE_PLAYBACK -> {
                val prefs = settingsPrefs()
                val matches = prefs.getBoolean("editor_active", false)
                    && prefs.getString("editor_target_title", "") == observedTitle
                    && prefs.getString("editor_target_artist", "") == observedArtist
                    && prefs.getString("editor_target_package", "") == observedPackageName
                if (!matches) return START_NOT_STICKY
                observedController?.let { controller ->
                    if (controller.playbackState?.state == STATE_PLAYING) controller.transportControls.pause()
                    else controller.transportControls.play()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleEditorPause() {
        editorPauseRunnable?.let { handler.removeCallbacks(it) }
        editorPauseRunnable = null
        val prefs = settingsPrefs()
        if (!prefs.getBoolean("editor_active", false)
            || prefs.getString("editor_target_title", "") != observedTitle
            || prefs.getString("editor_target_artist", "") != observedArtist
            || prefs.getString("editor_target_package", "") != observedPackageName
        ) return
        val controller = observedController ?: return
        val state = controller.playbackState ?: return
        if (state.state != STATE_PLAYING) return
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return
        if (duration <= 0L) return
        val runnable = Runnable {
            val latest = observedController
            val latestState = latest?.playbackState
            if (latest != null && latest === controller
                && latestState?.state == STATE_PLAYING
                && settingsPrefs().getBoolean("editor_active", false)
                && settingsPrefs().getString("editor_target_title", "") == observedTitle
                && settingsPrefs().getString("editor_target_artist", "") == observedArtist
                && settingsPrefs().getString("editor_target_package", "") == observedPackageName
            ) latest.transportControls.pause()
        }
        editorPauseRunnable = runnable
        handler.postDelayed(runnable, (duration - state.position - 1500L).coerceAtLeast(0L))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        handler.postDelayed({ update() }, 80L)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // activeNotifications 在移除回调返回前可能仍包含旧列表，稍后再读取以得到最新数量。
        handler.postDelayed({ update() }, 80L)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        handler.post { update() }
        // 服务刚连接时，系统可能还未完成媒体会话和通知列表的恢复；
        // 已在播放的歌曲未必会再次发送状态变化，因此只在启动阶段补几次短重试。
        listOf(300L, 1000L, 2000L, 4000L, 8000L).forEach { delayMs ->
            handler.postDelayed({ update() }, delayMs)
        }
    }

    private fun update() {
        if (lyricWindow == null && Settings.canDrawOverlays(this)) showLyricWindow()
        applyOverlaySettings()
        val filtered = activeNotifications.filter { sbn ->
            val extras = sbn.notification.extras
            extras.getString("android.template") == "android.app.Notification\$MediaStyle"
                || extras.get("android.mediaSession") is MediaSession.Token
        }
        val prioritizedNotifications = filtered.sortedByDescending { notification ->
            notificationPlaybackState(notification) == STATE_PLAYING
        }
        // 同一播放器自然切歌时，媒体通知可能暂时仍带着上一首的标题；
        // 活跃媒体会话的 metadata 更新更及时，应优先用它刷新歌曲和歌词。
        val activeSessionMusicFound = runCatching {
            val manager = getSystemService(MediaSessionManager::class.java)
            manager.getActiveSessions(ComponentName(this, MainService::class.java))
                .sortedByDescending { it.playbackState?.state == STATE_PLAYING }
                .any { parseActiveSession(it) }
        }.getOrDefault(false)
        val musicFound = activeSessionMusicFound || prioritizedNotifications.any { parseNotification(it) }
        if (!musicFound) {
            lyricWindow?.hideLyric()
            clearPlayerState()
        } else {
            reloadLyricsAfterSongOffsetChange()
        }
    }

    private fun parseNotification(sbn: StatusBarNotification): Boolean {
        val map = sbn.notification.extras
        val mst = map.get("android.mediaSession") as? MediaSession.Token ?: return false
        val artistAndAlbum = map.get("android.text")?.toString()?.split(" - ") ?: emptyList()
        val controller = MediaController(this, mst)
        val notificationTitle = map.get("android.title")?.toString()?.takeIf { it.isNotBlank() }
            ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: return false
        return parseController(controller, sbn.packageName, notificationTitle, artistAndAlbum)
    }

    private fun notificationPlaybackState(sbn: StatusBarNotification): Int {
        val token = sbn.notification.extras.get("android.mediaSession") as? MediaSession.Token ?: return 0
        return MediaController(this, token).playbackState?.state ?: 0
    }

    private fun parseActiveSession(controller: MediaController): Boolean {
        val metadata = controller.metadata ?: return false
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return false
        return parseController(controller, controller.packageName, title, emptyList())
    }

    private fun parseController(
        controller: MediaController,
        packageName: String,
        title: String,
        artistAndAlbum: List<String>,
    ): Boolean {
        val state = controller.playbackState ?: return false
        val metadata = controller.metadata
        if (!isMusicPlayback(packageName, metadata, artistAndAlbum)) return false
                val position = state.position
                val notificationArtist = artistAndAlbum.getOrNull(0)?.takeIf { it.isNotBlank() } ?: ""
                val notificationAlbum = artistAndAlbum.getOrNull(1) ?: ""
                val metadataTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val metadataMatchesNotification = metadataTitle.isNullOrBlank() || metadataTitle == title
                val artist = if (metadataMatchesNotification) {
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: notificationArtist
                } else notificationArtist
                val album = if (metadataMatchesNotification) {
                    metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: notificationAlbum
                } else notificationAlbum

                isPlaying = state.state == STATE_PLAYING
                settingsPrefs().edit()
                    .putString("playback_title", title)
                    .putString("playback_artist", artist)
                    .putString("playback_package", packageName)
                    .putLong("playback_position_ms", state.position.coerceAtLeast(0L))
                    .putLong("playback_duration_ms", metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                    .putLong("playback_updated_elapsed", SystemClock.elapsedRealtime())
                    .putBoolean("playback_is_playing", isPlaying)
                    .apply()
                observedTitle = title
                observedArtist = artist
                observedAlbum = album
                observedPackageName = packageName
                scheduleEditorPause()
                if (observedSessionToken != controller.sessionToken) {
                    observedController?.unregisterCallback(mediaControllerCallback)
                    observedSessionToken = controller.sessionToken
                    observedController = controller
                    controller.registerCallback(mediaControllerCallback)
                }
                val cover = if (metadataMatchesNotification) {
                    metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                } else null
                publishPlayer(title, artist, album, null, cover)
                handler.postDelayed({ refreshObservedCover(controller.sessionToken, title, artist, album) }, 350)
                handler.postDelayed({ refreshObservedCover(controller.sessionToken, title, artist, album) }, 1000)

                refreshLyrics(title, artist, album, position)
                return true
    }

    private fun isMusicPlayback(packageNameValue: String, metadata: MediaMetadata?, artistAndAlbum: List<String>): Boolean {
        val packageName = packageNameValue.lowercase()
        val telegramPackages = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.beta",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
        )
        val videoPackages = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music.video",
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.tencent.qqlive",
            "com.youku.phone",
            "com.iqiyi.iqiyi",
            "com.ss.android.ugc.aweme",
            "com.smile.gifmaker",
            "com.kuaishou.nebula",
            "com.mxtech.videoplayer.ad",
        )
        if (videoPackages.contains(packageName)) return false
        if (telegramPackages.contains(packageName)) return true
        val mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)?.lowercase() ?: ""
        if (mediaUri.endsWith(".mp4") || mediaUri.endsWith(".mkv") || mediaUri.endsWith(".webm") || mediaUri.endsWith(".avi")) return false
        val hasMusicMetadata = !metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank()
            || !metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).isNullOrBlank()
            || !metadata?.getString(MediaMetadata.METADATA_KEY_GENRE).isNullOrBlank()
        return hasMusicMetadata || artistAndAlbum.size > 1
    }

    private fun clearPlayerState() {
        if (currentMusic.isEmpty() && settingsPrefs().getString("now_title", "").isNullOrEmpty()) return
        currentMusic = ""
        currentMusicLyrics.clear()
        currentLine = ""
        nextLine = ""
        activeResultId = -1
        requestedResultId = -1
        publishedPlayerKey = ""
        observedController?.unregisterCallback(mediaControllerCallback)
        observedController = null
        observedSessionToken = null
        settingsPrefs().edit()
            .remove("now_title")
            .remove("now_artist")
            .remove("now_album")
            .remove("now_cover")
            .remove("now_cover_version")
            .remove("now_lyric_current")
            .remove("now_lyric_next")
            .remove("now_lyric_progress")
            .remove("lyric_search_results")
            .remove("selected_lyric_id")
            .putBoolean("lyric_manual_search", false)
            .apply()
    }

    private fun showMessage(message: String) {
        currentMusicLyrics.clear()
        currentMusicLyrics.put(0, currentMusic)
        currentMusicLyrics.put(Int.MAX_VALUE, message)
        settingsPrefs().edit().putString("now_lyric_current", "").putString("now_lyric_next", message).apply()
        lyricWindow?.hideLyric()
    }

    private fun cloudMusicRequest(
        route: String,
        musicKey: String? = null,
        callback: (JSONObject) -> Unit,
    ) {
        val request = JsonObjectRequest(
            Request.Method.GET,
            "https://music.163.com$route",
            null,
            Response.Listener<JSONObject> { callback(it) },
            Response.ErrorListener { e ->
                if (musicKey != null && musicKey != currentMusic) return@ErrorListener
                currentMusic = ""
                settingsPrefs().edit().putBoolean("lyric_searching", false).apply()
                showMessage("歌词获取失败，正在重试…")
                showSearchToast("歌词搜索失败：${e.networkResponse?.statusCode ?: "网络错误"}")
                Log.e("musicRequest", e.toString())
            }
        )
        queue.add(request)
    }

    private fun showSearchToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshLyrics(title: String, artist: String, album: String, position: Long) {
        val nameIdentifier = "$title - $artist"
        val forceSearch = settingsPrefs().getBoolean("lyric_force_search", false)
        val hadCurrentTrack = currentMusic.isNotBlank()
        val trackChanged = currentMusic != nameIdentifier

        if (currentMusic != nameIdentifier || forceSearch) {
            lyricBoundaryRunnable?.let { handler.removeCallbacks(it) }
            lyricBoundaryRunnable = null
            currentMusic = nameIdentifier
            if (!forceSearch) {
                currentLine = ""
                nextLine = ""
                currentLineStartMs = Long.MIN_VALUE
                settingsPrefs().edit()
                    .putString("now_lyric_current", "")
                    .putString("now_lyric_next", "")
                    .putFloat("now_lyric_progress", 0f)
                    .putLong("now_lyric_start_elapsed", 0L)
                    .putLong("now_lyric_duration_ms", 0L)
                    .apply()
            }
            activeResultId = -1
            requestedResultId = -1
            val prefs = settingsPrefs()
            prefs.edit().remove("selected_lyric_id")
                .putBoolean("lyric_selection_manual", false)
                .putBoolean("lyric_manual_search", forceSearch)
                .putBoolean("lyric_force_search", false)
                .putString("lyric_search_query", if (forceSearch) prefs.getString("lyric_search_query", "") ?: "" else "$title $artist")
                .putString("lyric_search_results", "[]")
                .apply()
            if (!forceSearch) showMessage("正在获取歌词…")

            // 自然切歌时先读取本地文件，避免无意义的搜索请求阻塞已经保存的歌词。
            val localFile = File("/sdcard/Music/Liri", lrcName(title, artist))
            val local = if (forceSearch) null else readLrc(title, artist)
            if (!forceSearch && localFile.isFile && local == null) {
                settingsPrefs().edit().putBoolean("lyric_searching", false).apply()
                showMessage("本地歌词读取失败")
                return
            }
            if (local != null) {
                settingsPrefs().edit()
                    .putString("current_lyric_source", local)
                    .putString("current_lyric_source_key", currentMusic)
                    .apply()
                parseLyrics(local)
                settingsPrefs().edit().putBoolean("lyric_searching", false).apply()
                // 已有歌曲自然切换时，媒体会话可能暂时返回上一首的位置；服务首次启动时则必须使用当前真实位置。
                val lyricPosition = if (trackChanged && hadCurrentTrack) 0L else position
                refreshLyrics(title, artist, album, lyricPosition)
                return
            }

            settingsPrefs().edit().putBoolean("lyric_searching", true).apply()
            val musicBeforeRequest = currentMusic
            val preserveLocalLyric = File("/sdcard/Music/Liri", lrcName(title, artist)).isFile
            val searchQuery = settingsPrefs().getString("lyric_search_query", "")?.trim().takeUnless { it.isNullOrBlank() } ?: "$title $artist"
            cloudMusicRequest("/api/search/get?type=1&s=${URLEncoder.encode(searchQuery, "UTF-8")}", musicBeforeRequest) { res ->
                if (musicBeforeRequest != currentMusic) return@cloudMusicRequest
                runCatching {
                    val songs = res.optJSONObject("result")?.optJSONArray("songs")
                    if (songs == null || songs.length() == 0) {
                        settingsPrefs().edit().putBoolean("lyric_searching", false).putString("lyric_search_results", "[]").apply()
                        showMessage("找不到歌词")
                        showSearchToast("未找到歌词搜索结果")
                        return@runCatching
                    }
                    val results = org.json.JSONArray()
                    val songItems = (0 until songs.length()).map { songs.getJSONObject(it) }.sortedWith(
                        compareByDescending<JSONObject> { song ->
                            val songArtist = song.optJSONArray("artists")?.optJSONObject(0)?.optString("name", "") ?: ""
                            songArtist.equals(artist, ignoreCase = true) || songArtist.contains(artist, ignoreCase = true) || artist.contains(songArtist, ignoreCase = true)
                        }
                    )
                    for (song in songItems.take(8)) {
                        results.put(JSONObject()
                            .put("id", song.getLong("id"))
                            .put("title", song.optString("name"))
                            .put("artist", song.optJSONArray("artists")?.optJSONObject(0)?.optString("name", artist) ?: artist)
                            .put("album", song.optJSONObject("album")?.optString("name", album) ?: album))
                    }
                    settingsPrefs().edit()
                        .putString("lyric_search_results", results.toString())
                        .putLong("selected_lyric_id", if (preserveLocalLyric) -1L else {
                            val selected = settingsPrefs().getLong("selected_lyric_id", -1L)
                            if ((0 until results.length()).any { results.getJSONObject(it).getLong("id") == selected }) selected else results.getJSONObject(0).getLong("id")
                        })
                        .putBoolean("lyric_searching", false)
                        .apply()
                    showSearchToast("找到 ${results.length()} 条歌词搜索结果")
                    if (!preserveLocalLyric) {
                        fetchLyrics(settingsPrefs().getLong("selected_lyric_id", -1L), musicBeforeRequest, title, artist)
                    }
                }.onFailure { error ->
                    settingsPrefs().edit().putBoolean("lyric_searching", false).apply()
                    showMessage("歌词搜索结果解析失败")
                    showSearchToast("歌词搜索出错：${error.message ?: "结果格式错误"}")
                    Log.e("lyricSearch", "parse search response failed", error)
                }
            }
        } else {
            val selected = settingsPrefs().getLong("selected_lyric_id", activeResultId.toLong())
            if (selected > 0 && selected.toInt() != activeResultId && selected.toInt() != requestedResultId) {
                fetchLyrics(selected, currentMusic, title, artist)
            }
            var currentLine: String? = null
            var nextLine: String? = null
            var currentIndex = -1
            for (index in 0 until currentMusicLyrics.size()) {
                if (currentMusicLyrics.keyAt(index) <= position) {
                    currentIndex = index
                    currentLine = currentMusicLyrics.valueAt(index)
                } else if (currentIndex >= 0) {
                    nextLine = currentMusicLyrics.valueAt(index)
                    break
                }
            }
            val firstLineBeforePlayback = if (currentLine == null && currentMusicLyrics.size() > 0) currentMusicLyrics.valueAt(0) else ""
            val progress = if (currentIndex >= 0 && currentIndex + 1 < currentMusicLyrics.size()) {
                val start = currentMusicLyrics.keyAt(currentIndex)
                val end = currentMusicLyrics.keyAt(currentIndex + 1)
                if (end > start) ((position - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
            } else 0f
            val lineStart = if (currentIndex >= 0) currentMusicLyrics.keyAt(currentIndex).toLong() else position
            val lineEnd = if (currentIndex >= 0 && currentIndex + 1 < currentMusicLyrics.size()) {
                currentMusicLyrics.keyAt(currentIndex + 1).toLong()
            } else {
                observedController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > lineStart } ?: lineStart
            }
            val hasNextTimestamp = currentIndex >= 0 && currentIndex + 1 < currentMusicLyrics.size()
            updateLyric(
                currentLine ?: "",
                nextLine ?: firstLineBeforePlayback,
                progress,
                if (hasNextTimestamp) (lineEnd - lineStart).coerceAtLeast(0L) else 0L,
                SystemClock.elapsedRealtime() - (position - lineStart),
                lineStart,
            )
            if (isPlaying) {
                val nextTime = when {
                    currentIndex >= 0 && currentIndex + 1 < currentMusicLyrics.size() ->
                        currentMusicLyrics.keyAt(currentIndex + 1)
                    currentIndex < 0 && currentMusicLyrics.size() > 0 ->
                        currentMusicLyrics.keyAt(0)
                    else -> Int.MAX_VALUE
                }
                if (nextTime != Int.MAX_VALUE && nextTime > position) {
                    scheduleLyricBoundary(title, artist, album, nextTime, position)
                }
            }
        }
    }

    private fun scheduleLyricBoundary(title: String, artist: String, album: String, nextTime: Int, position: Long) {
        if (!isPlaying) return
        lyricBoundaryRunnable?.let { handler.removeCallbacks(it) }
        val delay = (nextTime.toLong() - position).coerceAtLeast(1L)
        val musicAtSchedule = currentMusic
        val task = Runnable {
            lyricBoundaryRunnable = null
            if (currentMusic != musicAtSchedule) return@Runnable
            // 使用时间轴边界，避免播放器回调位置滞后导致换句延迟。
            refreshLyrics(title, artist, album, nextTime.toLong())
        }
        lyricBoundaryRunnable = task
        handler.postDelayed(task, delay)
    }

    private fun fetchLyrics(id: Long, musicBeforeRequest: String, title: String, artist: String) {
        requestedResultId = id.toInt()
        val local = if (settingsPrefs().getBoolean("lyric_selection_manual", false)) null else readLrc(title, artist)
        if (local != null) {
            if (musicBeforeRequest == currentMusic) {
                activeResultId = id.toInt()
                settingsPrefs().edit()
                    .putString("current_lyric_source", local)
                    .putString("current_lyric_source_key", currentMusic)
                    .apply()
                parseLyrics(local)
                refreshCurrentLyricDisplay()
            }
            return
        }
        cloudMusicRequest("/api/song/media?id=$id", musicBeforeRequest) { res ->
            if (musicBeforeRequest != currentMusic) return@cloudMusicRequest
            if (res.has("lyric")) {
                val lyric = res.getString("lyric")
                activeResultId = id.toInt()
                val prefs = settingsPrefs()
                prefs.edit()
                    .putString("current_lyric_source", lyric)
                    .putString("current_lyric_source_key", currentMusic)
                    .apply()
                if (prefs.getBoolean("save_lyrics_automatically", true) || prefs.getBoolean("lyric_selection_manual", false)) {
                    saveLrc(title, artist, lyric)
                }
                parseLyrics(lyric)
                refreshCurrentLyricDisplay()
            } else showMessage("歌曲无歌词")
        }
    }

    private fun publishPlayer(title: String, artist: String, album: String, icon: Icon?, metadataBitmap: Bitmap?) {
        val prefs = settingsPrefs()
        val playerKey = "$title\u0000$artist\u0000$album"
        if (publishedPlayerKey == playerKey && prefs.getString("now_cover", null) != null) return
        publishedPlayerKey = playerKey
        val edit = prefs.edit().putString("now_title", title).putString("now_artist", artist).putString("now_album", album).remove("now_cover")
        runCatching {
            val drawable = icon?.loadDrawable(this)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: metadataBitmap ?: return@runCatching
            val file = File(filesDir, "album_cover_${playerKey.hashCode().toUInt().toString(16)}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            edit.putString("now_cover", file.absolutePath).putLong("now_cover_version", System.currentTimeMillis())
        }
        edit.putLong("now_cover_version", System.currentTimeMillis())
        edit.apply()
    }

    private fun refreshObservedCover(token: MediaSession.Token, title: String, artist: String, album: String) {
        if (observedSessionToken != token || observedTitle != title) return
        val metadata = observedController?.metadata ?: return
        if (metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim() != title.trim()) return
        val cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return
        publishPlayer(title, artist, album, null, cover)
    }

    private fun extractIcon(extras: android.os.Bundle): Icon? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) extras.getParcelable("android.largeIcon", Icon::class.java)
            else @Suppress("DEPRECATION") extras.getParcelable("android.largeIcon") as? Icon
        }.getOrNull()
    }

    private fun lrcName(title: String, artist: String) = "$title - $artist.lrc".replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun readLrc(title: String, artist: String): String? = runCatching {
        val name = lrcName(title, artist)
        File("/sdcard/Music/Liri", name).takeIf { it.isFile }?.readText()
    }.getOrNull()

    private fun saveLrc(title: String, artist: String, lyric: String) {
        runCatching {
            val name = lrcName(title, artist)
            val dir = File("/sdcard/Music/Liri")
            check(dir.exists() || dir.mkdirs()) { "Cannot create lyric directory: ${dir.absolutePath}" }
            check(dir.isDirectory) { "Lyric path is not a directory: ${dir.absolutePath}" }
            FileOutputStream(File(dir, name), false).bufferedWriter().use { writer -> writer.write(lyric) }
        }.onFailure { Log.w("Liri", "save lrc failed", it) }
    }

    private fun parseLyrics(lyrics: String) {
        Log.d("parseLyrics", lyrics)
        currentMusicLyrics.clear()
        currentLine = ""
        nextLine = ""
        currentLineStartMs = Long.MIN_VALUE
        val taggedOffset = Regex("(?im)^\\[offset:([+-]?\\d+)\\]").find(lyrics)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val songOffset = (taggedOffset ?: 0).coerceIn(-30000, 30000)
        val offset = songOffset
        loadedSongOffsetMs = songOffset
        settingsPrefs().edit().putInt("current_song_offset_ms", songOffset).apply()
        val timeTagRegex = Regex("\\[(\\d+):(\\d{1,2})(?:[.:](\\d+))?\\]")
        Regex("(\\[[\\d.:]+])+([^\\[\\n]*)").findAll(lyrics).forEach { line ->
            val content = applyTextReplacements(line.groupValues[2])
            timeTagRegex.findAll(line.value).forEach { tag ->
                val minutes = tag.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = tag.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = tag.groupValues[3]
                val milliseconds = when {
                    fraction.isEmpty() -> 0L
                    fraction.length == 1 -> fraction.toLong() * 100L
                    fraction.length == 2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                val time = (((minutes * 60L + seconds) * 1000L) + milliseconds + offset.toLong())
                    .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    .toInt()
                currentMusicLyrics.append(time, content.trim())
            }
        }
    }

    private fun applyTextReplacements(input: String): String {
        val prefs = settingsPrefs()
        var result = input
        if (prefs.getBoolean("replace_remove_kana_annotations", false)) {
            result = result.replace(Regex("\\([^()]*[\\u3040-\\u309F\\u30A0-\\u30FF]+[^()]*\\)"), "")
        }
        if (prefs.getBoolean("replace_full_width_space", false)) result = result.replace('\u3000', ' ')
        val map = if (prefs.getBoolean("replace_simplified_chinese", false)) commonSimplifiedToJapanese else emptyMap()
        if (map.isNotEmpty()) result = result.map { map[it] ?: it }.joinToString("")
        runCatching {
            val rules = JSONArray(prefs.getString("lyric_text_replacements", "[]"))
            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                val pattern = rule.optString("pattern")
                if (pattern.isNotEmpty()) result = Regex(pattern).replace(result, rule.optString("replacement"))
            }
        }.onFailure { Log.w("Liri", "invalid lyric replacement rule", it) }
        return result
    }

    private fun loadKanjiTable(assetName: String): Map<Char, Char> = runCatching {
        val table = LinkedHashMap<Char, Char>()
        assets.open(assetName).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val fields = line.split('\t')
                if (fields.size == 2 && fields[0].length == 1 && fields[1].length == 1) {
                    table.putIfAbsent(fields[0][0], fields[1][0])
                }
            }
        }
        table
    }.getOrDefault(emptyMap())

    private fun reloadLyricsAfterSongOffsetChange() {
        if (currentMusic.isBlank()) return
        val requested = settingsPrefs().getInt("current_song_offset_ms", loadedSongOffsetMs)
            .coerceIn(-30000, 30000)
        if (requested == loadedSongOffsetMs) return
        val separator = currentMusic.lastIndexOf(" - ")
        if (separator <= 0) return
        val title = currentMusic.substring(0, separator)
        val artist = currentMusic.substring(separator + 3)
        val prefs = settingsPrefs()
        val source = readLrc(title, artist) ?: prefs.getString("current_lyric_source", null)
            ?.takeIf { prefs.getString("current_lyric_source_key", null) == currentMusic }
            ?: return
        val updated = withOffsetTag(source, requested)
        saveLrc(title, artist, updated)
        parseLyrics(updated)
        currentLine = ""
        nextLine = ""
    }

    private fun reloadLyricsAfterTextReplacementChange() {
        if (currentMusic.isBlank()) return
        val separator = currentMusic.lastIndexOf(" - ")
        if (separator <= 0) return
        val prefs = settingsPrefs()
        val source = readLrc(currentMusic.substring(0, separator), currentMusic.substring(separator + 3))
            ?: prefs.getString("current_lyric_source", null)
                ?.takeIf { prefs.getString("current_lyric_source_key", null) == currentMusic }
            ?: return
        parseLyrics(source)
        currentLine = ""
        nextLine = ""
    }

    private fun withOffsetTag(lyrics: String, offsetMs: Int): String {
        val tag = "[offset:$offsetMs]"
        return if (Regex("(?im)^\\[offset:[+-]?\\d+\\]").containsMatchIn(lyrics)) {
            lyrics.replace(Regex("(?im)^\\[offset:[+-]?\\d+\\]"), tag)
        } else {
            "$tag\n$lyrics"
        }
    }

    private var currentLyricProgress = -1f

    private fun updateLyric(line: String, next: String, progress: Float = 0f, durationMs: Long = 0L, startElapsed: Long = 0L, lineStartMs: Long = Long.MIN_VALUE) {
        val lineChanged = currentLine != line || nextLine != next || currentLineStartMs != lineStartMs
        if (lineChanged || kotlin.math.abs(currentLyricProgress - progress) >= 0.01f) {
            currentLine = line
            nextLine = next
            currentLineStartMs = lineStartMs
            currentLyricProgress = progress
            val edit = settingsPrefs().edit()
                .putString("now_lyric_current", line)
                .putString("now_lyric_next", next)
                .putFloat("now_lyric_progress", progress)
            if (lineChanged) {
                edit.putLong("now_lyric_start_elapsed", startElapsed)
                    .putLong("now_lyric_duration_ms", durationMs)
            }
            edit.apply()
            Log.d("updateLyric", currentLine)
            if (lineChanged) lyricWindow?.setLyric(line, next, lineStartMs)
        }
    }

    private fun refreshCurrentLyricDisplay() {
        if (currentMusic.isBlank()) return
        val separator = currentMusic.lastIndexOf(" - ")
        if (separator <= 0) return
        val title = currentMusic.substring(0, separator)
        val artist = currentMusic.substring(separator + 3)
        refreshLyrics(
            title,
            artist,
            observedAlbum,
            observedController?.playbackState?.position ?: 0L,
        )
    }

    private fun showLyricWindow() {
        if (lyricWindow != null) return
        val view = LyricWindow(this)
        val prefs = settingsPrefs()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenWidthDp = (screenWidth / resources.displayMetrics.density).toInt()
        val (left, width) = overlayHorizontalBounds(prefs)
        val params = WindowManager.LayoutParams(
            width, dp(32),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            // 系统状态栏无法被悬浮窗覆盖，顶部从状态栏底部按设置值偏移。
            y = topSettingPx(prefs)
        }
        overlayParams = params
        view.setFontSize(prefs.getInt("overlay_font_sp", 13).coerceIn(10, 18).toFloat())
        view.setAnimation(
            prefs.getInt("lyric_animation_angle", 180).coerceIn(0, 360).toFloat(),
            prefs.getInt("lyric_animation_distance_dp", 32).coerceIn(8, 64),
            prefs.getInt("lyric_animation_duration_ms", 280).coerceIn(100, 600),
        )
        runCatching { windowManager.addView(view, params); lyricWindow = view }
            .onFailure { Log.w("LyricWindow", "overlay unavailable", it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun statusBarHeightDp(): Int = (statusBarHeight() / resources.displayMetrics.density).toInt()

    private fun topSettingPx(prefs: android.content.SharedPreferences): Int {
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val defaultTop = (statusBarHeight() - dp(20)).coerceAtLeast(0)
        val percent = if (prefs.contains("overlay_top_percent_tenths")) {
            prefs.getInt("overlay_top_percent_tenths", 0) / 10f
        } else if (prefs.contains("overlay_top_percent")) {
            prefs.getInt("overlay_top_percent", 0).toFloat()
        } else {
            val legacyDp = prefs.getInt("overlay_top_dp", -1)
            if (legacyDp >= 0) dp(legacyDp) * 100f / height else defaultTop * 100f / height
        }
        return (height * percent.coerceIn(0f, 10f) / 100f).roundToInt().coerceIn(0, height)
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        return extras.getString("android.template") == "android.app.Notification\$MediaStyle"
            || extras.get("android.mediaSession") is MediaSession.Token
            || sbn.notification.category == Notification.CATEGORY_TRANSPORT
            || (observedPackageName.isNotBlank() && sbn.packageName == observedPackageName)
    }

    private fun isSilentNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(notification.channelId)
                ?.let { return it.importance < NotificationManager.IMPORTANCE_DEFAULT }
        }
        return notification.priority < Notification.PRIORITY_DEFAULT
    }

    private fun otherNotificationCount(): Int = activeNotifications.count { !isMediaNotification(it) && !isSilentNotification(it) }

    private fun overlayHorizontalBounds(prefs: android.content.SharedPreferences): Pair<Int, Int> {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenWidthDp = (screenWidth / resources.displayMetrics.density).toInt()
        val baseLeft = dp(prefs.getInt("overlay_left_dp", 12).coerceIn(0, 64))
        val baseWidth = dp(prefs.getInt("overlay_width_dp", 236).coerceIn(100, screenWidthDp))
            .coerceAtMost(screenWidth - baseLeft)
        val count = otherNotificationCount()
        val firstShift = if (prefs.getBoolean("overlay_shift_on_notification", false)) prefs.getInt("overlay_shift_on_notification_dp", 0).coerceIn(0, 128) else 0
        val eachShift = if (prefs.getBoolean("overlay_shift_each_notification", false)) prefs.getInt("overlay_shift_each_notification_dp", 0).coerceIn(0, 128) else 0
        val requestedShift = if (count > 0) {
            dp(firstShift + (count - 1) * eachShift)
        } else {
            0
        }
        val maxShift = (baseWidth - dp(100)).coerceAtLeast(0)
            .coerceAtMost((screenWidth - baseLeft - dp(100)).coerceAtLeast(0))
        val shift = requestedShift.coerceIn(0, maxShift)
        return (baseLeft + shift) to (baseWidth - shift).coerceAtLeast(dp(100)).coerceAtMost(screenWidth - baseLeft - shift)
    }

    private fun applyOverlaySettings() {
        val view = lyricWindow ?: return
        val params = overlayParams ?: return
        val prefs = settingsPrefs()
        val screenWidth = resources.displayMetrics.widthPixels
        val (left, width) = overlayHorizontalBounds(prefs)
        val top = topSettingPx(prefs)
        if (params.width != width || params.x != left || params.y != top) {
            params.width = width
            params.x = left
            params.y = top
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        view.setFontSize(prefs.getInt("overlay_font_sp", 13).coerceIn(10, 18).toFloat())
        view.setAnimation(
            prefs.getInt("lyric_animation_angle", 180).coerceIn(0, 360).toFloat(),
            prefs.getInt("lyric_animation_distance_dp", 32).coerceIn(8, 64),
            prefs.getInt("lyric_animation_duration_ms", 280).coerceIn(100, 600),
        )
    }

    private class LyricWindow(context: Context) : FrameLayout(context) {
        private var outgoing = lyricText()
        private var incoming = lyricText()
        private var current = ""
        private var currentLineStartMs = Long.MIN_VALUE
        private var fontSize = 13f
        private var animationAngle = 180f
        private var animationDistanceDp = 32
        private var animationDurationMs = 280L
        private var switchAnimator: AnimatorSet? = null
        private var scrollAnimator: ValueAnimator? = null
        private var scrollTarget = 0f

        init {
            clipChildren = true
            visibility = View.GONE
            setPadding(dp(10), 0, dp(10), 0)
            addView(outgoing, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(incoming, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            incoming.visibility = View.INVISIBLE
        }

        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        private fun lyricText() = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setSingleLine(true)
            fontFeatureSettings = "kern"
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        fun setFontSize(size: Float) {
            if (fontSize == size) return
            fontSize = size
            outgoing.textSize = size
            incoming.textSize = size
        }

        fun setAnimation(angle: Float, distanceDp: Int, durationMs: Int) {
            animationAngle = angle
            animationDistanceDp = distanceDp
            animationDurationMs = durationMs.toLong()
        }

        fun setLyric(line: String, next: String, lineStartMs: Long = Long.MIN_VALUE) {
            if (line.isBlank() || (line == current && lineStartMs == currentLineStartMs)) return
            current = line
            currentLineStartMs = lineStartMs
            if (visibility != View.VISIBLE) {
                cancelAnimations()
                applyTextWidth(outgoing, line)
                outgoing.text = line
                resetTransform(outgoing)
                outgoing.visibility = View.VISIBLE
                incoming.visibility = View.INVISIBLE
                visibility = View.VISIBLE
                startSinglePassScroll(outgoing, line)
                return
            }
            switchAnimator?.let { finishSwitch() }
            cancelScroll()
            val distance = dp(animationDistanceDp)
            val radians = Math.toRadians(animationAngle.toDouble())
            // 0° 表示上方，180° 表示下方，90°/270° 分别表示左右。
            val startX = (sin(radians) * distance).toFloat()
            val startY = (-cos(radians) * distance).toFloat()
            applyTextWidth(incoming, line)
            incoming.text = line
            incoming.visibility = View.VISIBLE
            incoming.translationX = startX
            incoming.translationY = startY
            incoming.alpha = 1f
            outgoing.alpha = 1f
            val set = AnimatorSet()
            set.playTogether(
                ObjectAnimator.ofFloat(outgoing, View.TRANSLATION_X, 0f, -startX),
                ObjectAnimator.ofFloat(outgoing, View.TRANSLATION_Y, 0f, -startY),
                ObjectAnimator.ofFloat(outgoing, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(incoming, View.TRANSLATION_X, startX, 0f),
                ObjectAnimator.ofFloat(incoming, View.TRANSLATION_Y, startY, 0f),
            )
            set.duration = animationDurationMs
            set.interpolator = android.view.animation.DecelerateInterpolator()
            set.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    switchAnimator = null
                    finishSwitch()
                    startSinglePassScroll(outgoing, current)
                }
                override fun onAnimationCancel(animation: android.animation.Animator) { switchAnimator = null }
            })
            switchAnimator = set
            set.start()
        }

        private fun applyTextWidth(view: TextView, text: String) {
            view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(height.coerceAtLeast(dp(28)), MeasureSpec.EXACTLY))
            val textWidth = view.paint.measureText(text).toInt() + dp(2)
            view.layoutParams = LayoutParams(textWidth.coerceAtLeast(width), LayoutParams.MATCH_PARENT)
            view.requestLayout()
        }

        private fun startSinglePassScroll(view: TextView, text: String) {
            cancelScroll()
            view.translationX = 0f
            scrollTarget = 0f
            post {
                if (outgoing !== view || current != text || visibility != View.VISIBLE) return@post
                // width 是窗口的总宽度，FrameLayout 两侧的 padding 不属于可见文本区域。
                // 终点必须按 padding 后的容器宽度计算，否则文本会在右侧还剩一段时停止。
                val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
                val target = minOf(0f, contentWidth.toFloat() - view.paint.measureText(text))
                scrollTarget = target
                if (target < 0f) animateScroll(view, 0f, target, true)
            }
        }

        private fun animateScroll(view: TextView, from: Float, to: Float, withDelay: Boolean) {
            cancelScroll()
            val speed = 0.1f * asciiSpeed(current)
            val duration = (kotlin.math.abs(to - from) / speed).toLong().coerceAtLeast(1L)
            val animator = ValueAnimator.ofFloat(from, to).apply {
                this.duration = duration
                startDelay = if (withDelay) 500L else 0L
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { view.translationX = it.animatedValue as Float }
            }
            scrollAnimator = animator
            animator.start()
        }

        private fun asciiSpeed(text: String): Float {
            if (text.isEmpty()) return 1f
            val ascii = text.count { it.code in 32..126 }.toFloat() / text.length
            return 1f + ascii
        }

        private fun finishSwitch() {
            val oldOutgoing = outgoing
            outgoing = incoming
            incoming = oldOutgoing
            resetTransform(incoming)
            incoming.visibility = View.INVISIBLE
        }

        private fun resetTransform(view: TextView) {
            view.translationY = 0f
            view.translationX = 0f
            view.alpha = 1f
        }

        private fun cancelScroll() {
            scrollAnimator?.cancel()
            scrollAnimator = null
        }

        private fun cancelAnimations() {
            switchAnimator?.cancel()
            switchAnimator = null
            cancelScroll()
        }

        fun hideLyric() {
            cancelAnimations()
            current = ""
            visibility = View.GONE
            outgoing.text = null
            incoming.text = null
            resetTransform(outgoing)
            resetTransform(incoming)
            incoming.visibility = View.INVISIBLE
        }
    }

}
