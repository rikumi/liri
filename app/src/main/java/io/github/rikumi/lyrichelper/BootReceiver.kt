package io.github.rikumi.lyrichelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.widget.Toast
import android.app.PendingIntent
import androidx.core.content.ContextCompat.getSystemService



class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MainService::class.java)
        val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val startTime = System.currentTimeMillis()
        val intervalTime = (60 * 1000).toLong()
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, startTime, intervalTime, pendingIntent)
    }
}
