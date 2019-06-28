package io.github.rikumi.lyrichelper

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.rikumi.lyrichelper.MainActivity


class MainActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()

        val intent = Intent(this, MainService::class.java)
        startService(intent)

        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainService::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainService::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {}

        packageManager.setComponentEnabledSetting(
            ComponentName(
                this,
                MainActivity::class.java
            ),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        finish()
    }
}
