package cloud.keyspace.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Vibrator
import android.service.quicksettings.TileService
import androidx.security.crypto.MasterKey
import java.io.File


class KeyspaceTile: TileService(){

    private val filenameExtension = "kfs"
    private val vaultFilename = "vault"
    private var filename: String? = "$vaultFilename.$filenameExtension"

    private val quickSettingsTile = "quickSettingsTile"
    private val channelId: Int = 2310

    override fun onClick() {
        super.onClick()

        val userData: SharedPreferences = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)
        val keyring: SharedPreferences = getSharedPreferences(MasterKey.DEFAULT_MASTER_KEY_ALIAS, MODE_PRIVATE)

        val email = userData.getString("userEmail", null)

        if (email != null) {
            sendNotification ("Your Keyspace Vault was wiped!")

            keyring.edit().clear().commit()
            userData.edit().clear().commit()
            val file = File(applicationContext.cacheDir, filename!!)
            file.delete()
            file.deleteRecursively()

            (applicationContext as Activity).finish()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.label = "Keyspace"
                qsTile.subtitle = "Tap to sign in"
                qsTile.icon = Icon.createWithResource(this, R.drawable.keyspace)
            } else qsTile.label = "Sign into Keyspace"

        } else {
            val intent = Intent(this, StartHere::class.java)
            intent.flags = FLAG_ACTIVITY_NEW_TASK
            startActivityAndCollapse(intent)
            sendNotification ("Wiped on-device Vault!")
        }

        qsTile.updateTile()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(50) // Vibrate for a second
        }

    }

    override fun onTileAdded() { // Called when the Tile is added
        super.onTileAdded()
    }

    override fun onStartListening() {  // Called when the Tile becomes visible
        super.onStartListening()

        val userData: SharedPreferences = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        userData.edit().putBoolean(quickSettingsTile, true).apply()
        val email = userData.getString("userEmail", null)

        if (email != null) {
            qsTile.icon = Icon.createWithResource(this, R.drawable.ic_baseline_delete_24)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.label = "Keyspace"
                qsTile.subtitle = "Tap to wipe"
            } else qsTile.label = "Wipe Keyspace"
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.label = "Keyspace"
                qsTile.subtitle = "Tap to sign in"
                qsTile.icon = Icon.createWithResource(this, R.drawable.keyspace)
            } else qsTile.label = "Sign into Keyspace"
        }

        qsTile.updateTile()

    }

    override fun onTileRemoved() {  // Called when the tile is no longer visible
        super.onTileRemoved()

        val userData: SharedPreferences = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        userData.edit().putBoolean(quickSettingsTile, false).apply()
    }

    override fun onStopListening() {
        super.onStopListening()

        val userData: SharedPreferences = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        userData.edit().putBoolean(quickSettingsTile, false).apply()
    }

    private fun sendNotification (message: String) {
        lateinit var builder: Notification.Builder
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, StartHere::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationChannel = NotificationChannel(channelId.toString(), getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH)
        notificationChannel.enableLights(true)
        // notificationChannel.lightColor = Color.CYAN
        notificationChannel.enableVibration(false)
        notificationManager.createNotificationChannel(notificationChannel)

        builder = Notification.Builder(this, channelId.toString())
            .setSmallIcon(R.drawable.keyspace)
            .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.drawable.keyspace))
            .setContentText(message)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(pendingIntent)

        notificationManager.notify(channelId, builder.build())
    }

}