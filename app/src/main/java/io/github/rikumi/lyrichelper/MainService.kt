package io.github.rikumi.lyrichelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.forEach
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.net.URLEncoder

class MainService : NotificationListenerService() {

    var handler = Handler()
    var currentMusic = ""
    var currentMusicLyrics = SparseArray<String>()
    var isPlaying = false

    lateinit var queue: RequestQueue

    private var currentLine = ""
    private var nextLine = ""
    private val channel = "lyrics"

    override fun onCreate() {
        super.onCreate()
        queue = Volley.newRequestQueue(this)
        initNotification()
        scheduleAutoUpdate()
    }

    override fun onBind(intent: Intent): IBinder {
        return super.onBind(intent)!!
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        update()
    }

    private fun scheduleAutoUpdate() {
        handler.postDelayed({
            update()
            scheduleAutoUpdate()
        }, 500)
    }

    private fun update() {
        val filtered = activeNotifications.filter { sbn ->
            sbn.notification.extras.getString("android.template") == "android.app.Notification\$MediaStyle"
        }
        if (filtered.size > 0) {
            parseNotification(filtered[0])
        }
    }

    private fun parseNotification(sbn: StatusBarNotification) {
        val map = sbn.notification.extras
        val mst = map.get("android.mediaSession") as MediaSession.Token?
        val title = map.get("android.title")?.toString()
        val artistAndAlbum = map.get("android.text")?.toString()?.split(" - ")
        if (mst != null && title != null && artistAndAlbum != null) {
            val controller = MediaController(this, mst)
            val state = controller.playbackState
            if (state != null) {
                val position = state.position
                val artist = artistAndAlbum[0]
                val album = if (artistAndAlbum.size > 1) artistAndAlbum[1] else ""

                if (isPlaying != (state.state == STATE_PLAYING)) {
                    isPlaying = state.state == STATE_PLAYING
                    updateNotification()
                }

                refreshLyrics(title, artist, album, position)
            }
        }
    }

    private fun showMessage(message: String) {
        currentMusicLyrics.clear()
        currentMusicLyrics.put(0, currentMusic)
        currentMusicLyrics.put(Int.MAX_VALUE, message)
    }

    private fun cloudMusicRequest(route: String, callback: (JSONObject) -> Unit) {
        val request = JsonObjectRequest(
            Request.Method.GET,
            "https://music.163.com$route",
            null,
            Response.Listener<JSONObject> { callback(it) },
            Response.ErrorListener { e ->
                currentMusic = ""
                showMessage("歌词获取失败，正在重试…")
                Log.e("musicRequest", e.toString())
            }
        )
        queue.add(request)
    }

    private fun refreshLyrics(title: String, artist: String, album: String, position: Long) {
        val nameIdentifier = "$title - $artist"

        if (currentMusic != nameIdentifier) {
            currentMusic = nameIdentifier
            showMessage("正在获取歌词…")

            val musicBeforeRequest = currentMusic
            cloudMusicRequest("/api/search/get?type=1&s=${URLEncoder.encode(nameIdentifier, "UTF-8")}") { res ->
                if (res.has("result")) {
                    val result = res.getJSONObject("result")
                    if (result.has("songs")) {
                        val songs = result.getJSONArray("songs")
                        if (songs.length() > 0) {
                            val id = songs.getJSONObject(0).getInt("id")

                            cloudMusicRequest("/api/song/media?id=$id") { res ->
                                if (res.has("lyric")) {
                                    val lyric = res.getString("lyric")
                                    if (musicBeforeRequest == currentMusic) {
                                        parseLyrics(lyric)
                                    }
                                } else {
                                    if (musicBeforeRequest == currentMusic) {
                                        showMessage("歌曲无歌词")
                                    }
                                }
                            }
                        } else {
                            if (musicBeforeRequest == currentMusic) {
                                showMessage("找不到歌词")
                            }
                        }
                    } else {
                        if (musicBeforeRequest == currentMusic) {
                            showMessage("找不到歌词")
                        }
                    }
                } else {
                    if (musicBeforeRequest == currentMusic) {
                        showMessage("接口调用超限")
                    }
                }
            }
        } else {
            var currentLine: String? = null
            var nextLine: String? = null

            currentMusicLyrics.forEach { key, value ->
                if (key <= position) {
                    currentLine = value
                } else if (currentLine != null && nextLine == null) {
                    nextLine = value
                }
            }
            updateLyric(currentLine ?: "", nextLine ?: currentMusicLyrics.valueAt(0) ?: "")
        }
    }

    private fun parseLyrics(lyrics: String) {
        Log.d("parseLyrics", lyrics)
        currentMusicLyrics.clear()
        Regex("(\\[[\\d.:]+])+([^\\[\\n]*)").findAll(lyrics).forEach { line ->
            val content = line.groupValues[2]
            Regex("\\[[\\d.:]+]").findAll(line.value).forEach { tag ->
                val numbers = Regex("\\d+").findAll(tag.value).toList()
                var time = -250
                if (numbers.size > 0) {
                    time += numbers[0].value.toInt() * 60000
                }
                if (numbers.size > 1) {
                    time += numbers[1].value.toInt() * 1000
                }
                if (numbers.size > 2) {
                    time += (("0." + numbers[2].value).toFloat() * 1000).toInt()
                }
                currentMusicLyrics.append(time, content.trim())
            }
        }
    }

    private fun updateLyric(line: String, next: String) {
        if (currentLine != line || nextLine != next) {
            currentLine = line
            nextLine = next
            Log.d("updateLyric", currentLine)
            updateNotification()
        }
    }

    private fun initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "歌词通知"
            val descriptionText = "显示当前的歌词信息"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channel, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(currentLine)
            .setContentText(nextLine)
            .setOngoing(isPlaying)
            .setVibrate(LongArray(1) { 0 })

        with(NotificationManagerCompat.from(this)) {
            notify(0, builder.build())
        }
    }
}
