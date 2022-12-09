package cloud.keyspace.android

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.autofill.AutofillManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.permissionx.guolindev.PermissionX

class PermissionCodes {
    val CAMERA_CODE = 69
    val STORAGE_CODE = 420
    val ACCESSIBILITY_CODE = 69420
    val AUTOFILL_CODE = 42069
}

class Permissions : AppCompatActivity() {

    lateinit var grantPermissionsButton: MaterialButton
    lateinit var permissionStatusSymbol: ImageView
    lateinit var titleText: TextView
    lateinit var infoText: TextView
    lateinit var skipPermissionsButton: MaterialButton
    lateinit var permissionsList: LinearLayout

    lateinit var permissionCheckProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onboarding_permissions_description)

        permissionStatusSymbol = findViewById(R.id.permissionStatusSymbol)
        titleText = findViewById(R.id.titleText)
        infoText = findViewById(R.id.infoText)
        grantPermissionsButton = findViewById(R.id.grantPermissionsButton)
        permissionsList = findViewById(R.id.permissionsList)
        skipPermissionsButton = findViewById(R.id.skipPermissionsButton)

        permissionsList.visibility = View.GONE
        grantPermissionsButton.visibility = View.GONE
        skipPermissionsButton.visibility = View.GONE

        val cameraPermission = findViewById<LinearLayout>(R.id.cameraPermission)
        /*
        val overlayPermission = findViewById<LinearLayout>(R.id.overlayPermission)
        */
        val autofillServicePermission = findViewById<LinearLayout>(R.id.autofillServicePermission)

        val skipPermissionsButton = findViewById<MaterialButton>(R.id.skipPermissionsButton)

        var cameraStatus = false
        var overlayStatus = false
        var storageStatus = false
        var connectivityStatus = false
        var keyguardStatus = false
        var autofillServiceStatus = false

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPermission.visibility = View.GONE
            cameraStatus = true
        }

/*
        if (Settings.canDrawOverlays(applicationContext)) {
            overlayPermission.visibility = View.GONE
            overlayStatus = true
        }

        val autofillManager = getSystemService(AutofillManager::class.java) as AutofillManager
        if (autofillManager.hasEnabledAutofillServices()) {
            autofillServicePermission.visibility = View.GONE
            autofillServiceStatus = true
        }
*/

        val learnMoreText = findViewById<View>(R.id.learnMoreText) as TextView
        learnMoreText.movementMethod = LinkMovementMethod.getInstance();

        permissionsList.visibility = View.VISIBLE
        grantPermissionsButton.visibility = View.VISIBLE
        skipPermissionsButton.visibility = View.VISIBLE

        grantPermissionsButton.setOnClickListener {
                // sendNotification ("Notifications enabled! Don't worry, we won't spam you.")

                PermissionX.init(this@Permissions)
                    .permissions (
                        Manifest.permission.CAMERA,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                    .onExplainRequestReason { scope, deniedList ->
                        scope.showRequestReasonDialog(deniedList, "Keyspace needs these permissions in order to work as intended", "Enable", "Go back")
                    }
                    .onForwardToSettings { scope, deniedList ->
                        scope.showForwardToSettingsDialog(deniedList, "Some of these settings need to be enabled manually", "Enable", "Go back")
                    }
                    .request { allGranted, grantedList, deniedList ->
                        if (allGranted) {

                            /*
                            if (!autofillManager.hasEnabledAutofillServices()) {

                                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Permissions).create()
                                alertDialog.setTitle("Autofill permissions")
                                alertDialog.setCancelable(false)
                                alertDialog.setIcon(R.drawable.ic_baseline_password_24)
                                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Open Autofill Settings") { dialog, _ ->
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivityForResult(intent, PermissionCodes().AUTOFILL_CODE)
                                }
                                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "I'll do this later") { dialog, _ -> dialog.dismiss() }
                                alertDialog.setMessage("Keyspace will attempt to open your device's autofill settings. Find your app in the list and enable it.")
                                alertDialog.show()
                            }
                            */

                            /*
                            if (!isAccessibilityServiceEnabled (applicationContext, AutofillAccessibilityService::class.java)) {

                                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Permissions).create()
                                alertDialog.setTitle("Accessibility permissions")
                                alertDialog.setCancelable(false)
                                alertDialog.setIcon(R.drawable.ic_baseline_accessibility_new_24)

                                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Open Accessibility Settings") { dialog, _ ->
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    startActivity(intent)
                                }

                                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "I'll do this later") { dialog, _ -> dialog.dismiss() }
                                alertDialog.setMessage("Keyspace will attempt to open your device's accessibility settings. Find your app in the list and enable it." +
                                        "\n\nNote: Some OEMs (such as Samsung) may require you to disable Secure Start-Up. " +
                                        "Please disable it in settings if your device requires it.")
                                alertDialog.show()

                            }
                            */

                            setToDashboard()

                        }
                    }
        }

        skipPermissionsButton.setOnClickListener {
            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this@Permissions).create()
            alertDialog.setTitle("Skip permissions")
            alertDialog.setMessage("Denying these permissions will result in Keyspace not functioning properly. You can enable them later in settings.")
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Continue anyway") { dialog, _ ->
                restartApp()
            }
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.show()
        }

    }

    fun isAccessibilityServiceEnabled (context: Context, service: Class<out AccessibilityService?>): Boolean {
        val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(service.name)) return true
        }
        return false
    }

    private fun setToDashboard () {
        permissionStatusSymbol.setImageDrawable(getDrawable(R.drawable.ic_baseline_check_24))
        permissionsList.visibility = View.GONE
        titleText.text = "All permissions set"
        infoText.text = "Tap 'Finish' to start using Keyspace!"
        skipPermissionsButton.visibility = View.GONE
        grantPermissionsButton.text = "Finish"
        grantPermissionsButton.icon = getDrawable(R.drawable.ic_baseline_navigate_next_24)
        grantPermissionsButton.isEnabled = true
        grantPermissionsButton.setOnClickListener {
            restartApp()
        }
    }

    private fun restartApp () {
        val intent = baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun sendNotification (message: String) {
        val channelId = 2310
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