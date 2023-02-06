package cloud.keyspace.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.PackageManagerCompat.LOG_TAG
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.*
import kotlin.NoSuchElementException


/**
 *  Copyright (c) 2023 Owais Shaikh @ Keyspace (some top notch stuff here)
 *                                      ._______________________________________.
 *                                      ↓                                       |
 *  START                       Continuously                                    |
 *  AccessibilityService -----> read view --------> Insert each node            |
 *                              nodes on screen     into detector function      |
 *                                                    ↓                         |
 *                                                    IF                        |
 *                                                    any view node  ELSE ---> mutableListOf<AccessibilityNodeInfo>()
 *                                                    is fillable              .clear()
 *                                                    ↓
 *                                            mutableListOf<AccessibilityNodeInfo>()
 *                                            .add()
 *                                            ↓
 *                                            IF mutableListOf<AccessibilityNodeInfo>() IS NOT empty
 *                                            |
 *                                            ↓             IF          WAIT                     SET
 *                                            Show nub ---> tapped ---> for user to pick ------> IOUtilities.Login? variable
 *                                                                      or autopick                         |
 *                                                                      based on URL / package ID           |
 *                                                                                                          ↓
 *                                                             .------------------------------------------> FOR
 *                                                             |     IF IOUtilities.Login? IS NOT null  <-- node in mutableListOf<AccessibilityNodeInfo>()
 *                                                             |    a) AUTOFILL STRING using SET_TEXT
 *                                                             '--- b) mutableListOf<AccessibilityNodeInfo>().remove(node)
 *                                                                  |
 *                                                                  ON LOOP END
 *                                                                  ↓
 *                                                                  SET IOUtilities.Login? to null
 *                                                                  ↓
 *                                                                  FINISH
 */

class AutofillAccessibilityService: AccessibilityService() {

    private var autofillableElements = mutableMapOf<String, AccessibilityNodeInfo>()

    private var windowManager: WindowManager? = null
    var autofillLayout: FrameLayout? = null
    var autofillButton: View? = null
    var nub: LinearLayout? = null
    var nubVisible: Boolean = false

    val nubTimeout = 7500

    private val urlsOnScreen: MutableList<String> = mutableListOf()
    var urlOnScreen: String? = null
    private val autofillableFields: MutableList<AccessibilityNodeInfo> = mutableListOf()

    lateinit var misc: MiscUtilities

    val TYPE_LOGIN = "login"
    val TYPE_CARD = "card"

    var loginData: IOUtilities.Login? = null
    var cardData: IOUtilities.Card? = null
    // var autofillableSMSData: String? = null
    // var autofillable2faData: String? = null

    init {
        loginData = IOUtilities.Login (
            id = UUID.randomUUID().toString(),
            organizationId = null,
            type = TYPE_LOGIN,
            name = "FirstName LastName",
            notes = "notes",
            favorite = true,
            tagId = null,
            loginData = IOUtilities.LoginData (
                username = "u53rn4m3",
                password = "password123",
                passwordHistory = null,
                email = "em@il.il",
                totp = IOUtilities.Totp (
                    secret = "ASDFGHJKLASDFGHJKL",
                    backupCodes = mutableListOf("123456", "654321"),
                ),
                siteUrls = mutableListOf("https://google.com", "accounts.google.com")
            ),
            dateCreated = 1673464321,
            dateModified = 1673464321,
            frequencyAccessed = 0L,
            iconFile = "keyspace",
            customFields = null
        )
    }

    val blacklistedPackages = listOf (
        "cloud.keyspace.android", // keyspace android app
        "com.android.systemui",
        "com.android.settings",
        "app.lawnchair",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.launcher",
        "com.computer.desktop.ui.launcher",
        "com.launcher.notelauncher",
        "com.anddoes.launcher",
        "com.actionlauncher.playstore",
        "ch.deletescape.lawnchair.plah",
        "com.microsoft.launcher",
        "com.teslacoilsw.launcher",
        "com.teslacoilsw.launcher.prime",
        "is.shortcut",
        "me.craftsapp.nlauncher",
        "com.ss.squarehome2",
        "com.treydev.pns"
    )

    var urlBarIdentifiers = listOf (
        "url",
        "url_bar",
        "url_field",
        "omnibarTextInput",
        "url_bar_title",
        /*
        "search",
        "search_fragment_input_view",
        "mozac_browser_toolbar_url_view",
        "mozac_browser_toolbar_url_view",
        "search",
        "search_box",
        "edit_text",
        "addressbarEdit",
        "mozac_browser_toolbar_url_view,url_bar_title",
        "location_bar_edit_text",
        "location_bar_edit_text",
        "url_edittext",
        "bro_omnibar_address_title_text",
        "bro_omnibox_collapsed_title",
        "g2",
        "am",
        "an",
        "as",
        "display_url",
        "addressbar_url",
        "editor",
        "search_field",
        "enterUrl",
        "address_bar_edit_text",
        "address_editor_with_progress",
        "address_editor_with_progress",
        "enterUrl",
        "title"*/
    )

    val passwordIdentifiers = listOf (
        "password",
        "passphrase",
        "secret"
    )

    val emailIdentifiers = listOf (
        "email",
        "email id",
        "mail id",
        "e-mail",
        "mail"
    )

    val usernameIdentifiers = listOf (
        "username",
        "user",
        "user id",
        "userid",
        "user",
        "account name",
        "id",
        "user",
        "input",
        "id",
        "identity",
        "identifier",
        "uid",
        "candidate",
        "udid",
        "name"
    )

    val pinIdentifiers = listOf (
        "pin"
    )

    val twoFactorAuthIdentifiers = listOf (
        "one time password",
        "token",
        "otp",
        "2fa",
        "authenticator"
    )

    val smsOtpIdentifiers = listOf (
        "sent",
        "sms",
        "text message",
    )

    val cvvIdentifiers = listOf (
        "cvv",
        "cccsc"
    )

    val cardNumberIdentifiers = listOf (
        "card number",
        "card no",
        "ccnumber"
    )

    val cardHolderNameIdentifiers = listOf (
        "card name",
        "cc-name",
        "holder"
    )

    val cardExpiryIdentifiers = listOf (
        "expiry",
        "exp-year",
        "exp-month",
        "mm",
        "yyyy",
        "exp",
    )

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var info: AccessibilityServiceInfo

    override fun onServiceConnected() {
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
        info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.notificationTimeout = 1000
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = (
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                        or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                        or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                        or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        or AccessibilityServiceInfo.DEFAULT
                        or AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
                        or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                )
        this.serviceInfo = info
        Toast.makeText(applicationContext, "Started Accessibility Service for Keyspace!", Toast.LENGTH_SHORT).show()

        misc = MiscUtilities(applicationContext)

    }

    @Override
    override fun onKeyEvent(event: KeyEvent): Boolean {
        killNub()
        return true
    }

    @SuppressLint("UseCompatLoadingForDrawables", "RtlHardcoded")
    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        val viewNode = event.source ?: return

        if (viewNode.packageName == null) return
        if (misc.checkIfListContainsSubstring(blacklistedPackages, viewNode.packageName.toString())) return

        logNodeHierarchy(viewNode, 3)

        for (element in autofillableElements) {
            /*Log.d ("KeyspaceAccElement",
                      element.key
                      + " | isPassword?: "
                      + element.value.isPassword.toString()
                      + " | isEditText?: " + element.value.className.toString().lowercase().contains("edittext")
                      + " | fillableNodes: ${autofillableElements.size}"
            )*/
            fillLoginData(element.value)
        }

        val url = getUrlsOnScreen(event)
        if (url != null) {
            val nubSpawned = intelligentlySpawnNub (event)
            if (!nubSpawned) return
        }

        if (loginData == null) return

    }

    private fun logNodeHierarchy(nodeInfo: AccessibilityNodeInfo?, depth: Int) {
        if (nodeInfo == null) return

        grabFillableFields (nodeInfo)

        for (i in 0 until nodeInfo.childCount) {
            logNodeHierarchy(nodeInfo.getChild(i), depth + 1)
        }

    }

    private fun grabFillableFields (nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        val textOnScreen: String? =
            if (!nodeInfo.text.isNullOrBlank()) nodeInfo.text.toString()
            else if (!nodeInfo.contentDescription.isNullOrBlank()) nodeInfo.contentDescription.toString()
            else null


        /** 1. EMAIL GRABBERS */

        if (!textOnScreen.isNullOrBlank()) {
            if (misc.checkIfListContainsSubstring(emailIdentifiers, textOnScreen)) {
                if (!nodeInfo.className.toString().lowercase().contains("edittext")) {
                    autofillableElements [textOnScreen] = nodeInfo
                }
            }
        }

        if (nodeInfo.className != null) {
            if (nodeInfo.className.toString().lowercase().contains("edittext") && !nodeInfo.isPassword) {
                for (element in autofillableElements) {
                    if (misc.checkIfListContainsSubstring(emailIdentifiers, element.key)) {
                        Log.d ("KeyspaceAccEvent", "Found an email box")
                        element.setValue(nodeInfo)
                    }
                }
            }
        }


        /** 2. USERNAME GRABBERS */

        if (!textOnScreen.isNullOrBlank()) {
            if (misc.checkIfListContainsSubstring(usernameIdentifiers, textOnScreen)) {
                if (!nodeInfo.className.toString().lowercase().contains("edittext")) {
                    autofillableElements [textOnScreen] = nodeInfo
                }
            }
        }

        if (nodeInfo.className.toString().lowercase().contains("edittext") && !nodeInfo.isPassword) {
            for (element in autofillableElements) {
                if (misc.checkIfListContainsSubstring(usernameIdentifiers, element.key)) {
                    Log.d ("KeyspaceAccEvent", "Found a username box")
                    element.setValue(nodeInfo)
                }
            }
        }


        /** 3. PASSWORD GRABBERS */

        if (!textOnScreen.isNullOrBlank()) {
            if (misc.checkIfListContainsSubstring(passwordIdentifiers, textOnScreen)) {
                if (!nodeInfo.className.toString().lowercase().contains("edittext")) {
                    autofillableElements [textOnScreen] = nodeInfo
                }
            }
        }

        if (nodeInfo.className.toString().lowercase().contains("edittext") && nodeInfo.isPassword) {
            for (element in autofillableElements) {
                if (misc.checkIfListContainsSubstring(passwordIdentifiers, element.key)) {
                    Log.d ("KeyspaceAccEvent", "Found a password box")
                    element.setValue(nodeInfo)
                }
            }
        }


        /** 4. 2FA GRABBERS */

        if (!textOnScreen.isNullOrBlank()) {
            if (misc.checkIfListContainsSubstring(smsOtpIdentifiers, textOnScreen)) {
                if (!nodeInfo.className.toString().lowercase().contains("edittext")) {
                    autofillableElements [textOnScreen] = nodeInfo
                }
            }
        }

        if (nodeInfo.className.toString().lowercase().contains("edittext") && nodeInfo.isPassword) {
            for (element in autofillableElements) {
                if (misc.checkIfListContainsSubstring(smsOtpIdentifiers, element.key)) {
                    Log.d ("KeyspaceAccEvent", "Found a 2FA box")
                    element.setValue(nodeInfo)
                }
            }
        }

    }

    private fun getUrlsOnScreen(event: AccessibilityEvent): String? {

        fun captureUrl (event: AccessibilityEvent, addressBarId: String): Boolean {
            val info = event.source ?: return false
            val nodes = info.findAccessibilityNodeInfosByViewId(addressBarId)
            val packageId = info.packageName.toString()

            try {
                var url: String? = null
                val addressBarNodeInfo = nodes[0]
                if (addressBarNodeInfo.text != null) {
                    url = addressBarNodeInfo.text.toString()
                }

                addressBarNodeInfo.recycle()

                if (url != null) {
                    val trimmedUrl = misc.grabURLFromString(url)
                    if (!trimmedUrl.isNullOrEmpty()) {
                        if (!urlsOnScreen.contains(trimmedUrl)) {
                            urlsOnScreen.add(trimmedUrl)
                        }
                    }
                }

            } catch (_: Exception) {
                urlsOnScreen.clear()
                if (
                    info.className.toString().lowercase().contains("edittext")
                    && !misc.checkIfListContainsSubstring(blacklistedPackages, packageId)
                    && !urlsOnScreen.contains(packageId)
                    && urlsOnScreen.isEmpty()
                ) urlsOnScreen.add(packageId)
                return false
            }

            return true

        }

        try {
            val packageName = event.packageName.toString()
            for (identifier in urlBarIdentifiers) {
                val addressBarId = "$packageName:id/${identifier}"
                captureUrl (event, addressBarId) // all urls gathered are stored in urlsOnScreen MutableList
                if (urlsOnScreen.isNotEmpty()) {
                    event.source!!.recycle()
                    // Log.d("URLs Detected", urlsOnScreen.toString())
                    urlOnScreen = urlsOnScreen[0]
                    break
                }
            }

            urlOnScreen = urlsOnScreen[0]

        } catch (_: Exception) { }

        return  urlOnScreen

    }

    private fun intelligentlySpawnNub (event: AccessibilityEvent): Boolean {

        /*
        * This happens in 2 phases:
        * 1. Kill any existing nub
        * 2. Spawn new nub
        * */

        /* NUB MURDERERS
        * Try killing nubs as much as possible before spawning a new one.
        * */

        // 1. When the screen is turned off.
        if (!powerManager.isInteractive || !powerManager.isScreenOn || keyguardManager.isDeviceLocked) {
            killNub()
            return false
        }

        // 2. When in PiP.
        windows.forEach {
            if (it.isInPictureInPictureMode) killNub()
        }

        // Ignore null package.
        if (event.packageName == null) {
            return false
        }

        // Ignore SystemUI changes like status bar clock change.
        if (event.packageName.toString().trim().replace(" ", "").lowercase().contains("systemui")) {
            return false
        }

        // Ignore Keyspace trying to render a nub.
        if (
            event.packageName.toString().trim().replace(" ", "").lowercase()
                .contains(resources.getString(R.string.app_name).lowercase())
        ) {
            return false
        }

        // Kill if a blacklisted package is opened. This may be computationally intensive.
        blacklistedPackages.forEach {
            if (
                event.packageName.toString().trim().replace(" ", "").lowercase()
                    .contains(it.trim().replace(" ", "").lowercase())
            ) {
                killNub()
                return false
            }
        }


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////


        /* NUB SPAWNER-ERS
        * Try making a nub if an autofillable text field exists.
        * */

        if (getLoginField(event) != null) {
            createNub()
            return true
        }

        if (getCardField(event) != null) {
            createNub()
            return true
        }

        return false

    }

    private fun getLoginField (event: AccessibilityEvent): AccessibilityNodeInfo? {
        val source: AccessibilityNodeInfo = event.source ?: return null

        // to fix issue with viewIdResName = null on Android 6+
        source.refresh()
        event.recycle()

        if (getFieldName(event).isNullOrEmpty()) {
            return null
        }

        passwordIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        usernameIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        emailIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        usernameIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        twoFactorAuthIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        smsOtpIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        pinIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        return null
    }

    private fun getCardField (event: AccessibilityEvent): AccessibilityNodeInfo? {
        val source: AccessibilityNodeInfo = event.source ?: return null

        // to fix issue with viewIdResName = null on Android 6+
        source.refresh()
        event.recycle()

        if (getFieldName(event).isNullOrEmpty()) {
            return null
        }

        cardNumberIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        cvvIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        pinIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        cardHolderNameIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        cardExpiryIdentifiers.forEach {
            if (misc.areSimilar(getFieldName(event).toString(), it)) {
                return event.source
            }
        }

        return null
    }

    private fun getFieldName (event: AccessibilityEvent): String? {
        if (!event.source?.viewIdResourceName.isNullOrBlank()) return event.source?.viewIdResourceName.toString()
        if (event.source?.isPassword == true) return "password"
        if (!event.source?.hintText.isNullOrBlank()) return event.source?.hintText.toString()
        if (!event.source?.text.isNullOrBlank()) return event.source?.text.toString()
        return null
    }

    private fun fillLoginData (rootNode: AccessibilityNodeInfo) {
        val editText = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val arguments = Bundle()

        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, loginData!!.loginData!!.password) // Todo: change this to actual fillable fields
        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        loginData = null
    }

    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun createNub () {
        if (autofillLayout == null) {
            autofillLayout = FrameLayout(this)
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val layoutParameters = WindowManager.LayoutParams()
            layoutParameters.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            layoutParameters.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            layoutParameters.format = PixelFormat.TRANSLUCENT
            layoutParameters.flags = layoutParameters.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            layoutParameters.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParameters.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParameters.gravity = Gravity.RIGHT

            autofillButton = LayoutInflater.from(this).inflate(R.layout.accessibility_autofill_nub, autofillLayout)
            windowManager!!.addView(autofillLayout, layoutParameters)

            nub = autofillButton!!.findViewById<LinearLayout>(R.id.nub)
            nub!!.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.from_right))

            if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                nub!!.backgroundTintList = ColorStateList.valueOf(getColor(R.color.darkSecondaryBackgroundColor))
                val keyspaceLogoText = autofillButton!!.findViewById<TextView>(R.id.keyspaceLogoText)
                val keyspaceSubtitle = autofillButton!!.findViewById<TextView>(R.id.keyspaceSubtitle)
                keyspaceLogoText.setTextColor(Color.WHITE)
                keyspaceSubtitle.setTextColor(Color.WHITE)
            }

            nub!!.setOnTouchListener(object : MiscUtilities.OnSwipeTouchListener(this@AutofillAccessibilityService) {

                override fun onClick() {
                    //getVault (urlOnScreen!!)
                    Toast.makeText(applicationContext, "Nub tapped!", Toast.LENGTH_SHORT).show()
                    killNub()
                    super.onClick()
                }

                override fun onSwipeRight() {
                    killNub()
                    super.onSwipeRight()
                }

                override fun onLongClick() { // To open the item picker despite fast autofill status
                    Toast.makeText(applicationContext, "Opening Keyspace", Toast.LENGTH_SHORT).show()
                    killNub()
                    openKeyspace ()
                    super.onLongClick()
                }

            })

            Handler().postDelayed({ killNub() }, nubTimeout.toLong())

        }

        nubVisible = true

    }

    fun openKeyspace () {
        val intent = Intent(this@AutofillAccessibilityService, StartHere::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivity(intent)
    }

    fun killNub() {
        try {
            val slideAway = AnimationUtils.loadAnimation(applicationContext, R.anim.to_right_fast)
            if (nubVisible) nub!!.startAnimation(slideAway)
            slideAway.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(arg0: Animation) {}
                override fun onAnimationRepeat(arg0: Animation) {}
                override fun onAnimationEnd(arg0: Animation) {
                    autofillButton = null
                    nub = null
                    windowManager!!.removeView(autofillLayout)
                    autofillLayout = null
                }
            })
            nubVisible = false
            autofillableFields.clear()
            urlsOnScreen.clear()
        } catch (_: Exception) {
            // No nub
        }

    }

    private fun getVault (url: String): IOUtilities.Vault? {
        Log.d("KeyspaceAutofillAct", url)

        val intent = Intent(this, AutofillAccessibilityActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.setClassName(applicationContext.packageName, applicationContext.packageName + "." + getString(R.string.title_activity_autofill_accessibility))

        intent.putExtra("siteUrl", url)

        intent.putExtra("dataType", "login") // login, card or SMS
        applicationContext.startActivity(intent)

        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        var dataFromAutofillActivity: ByteArray? = null

        if (intent.extras!!.containsKey("dataFromAutofillActivity")) {
            if (intent.getByteArrayExtra ("dataFromAutofillActivity") != null) dataFromAutofillActivity = intent.getByteArrayExtra ("dataFromAutofillActivity")
        }

        val dataType = intent.getStringExtra("dataType")
        if (dataType != null) {

            val mapper = jacksonObjectMapper()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // In case the vault isn't updated, ignore new properties in json

            try {
                when {
                    dataType.contains("login") -> {
                        loginData = mapper.readValue (dataFromAutofillActivity, IOUtilities.Login::class.java)
                    }
                    dataType.contains("card") -> {
                        // autofillableCardData = mapper.readValue (dataFromAutofillActivity, IOUtilities.Card::class.java)
                    }
                    dataType.contains("smsOtp") -> {
                        // autofillableSMSData = String (dataFromAutofillActivity!!)
                    }
                    dataType.contains("2faOtp") -> {
                        // autofillable2faData = String (dataFromAutofillActivity!!)
                    }
                    else -> {
                        throw NullPointerException()
                    }
                }
            } catch (noData: NullPointerException) {
                //Toast.makeText(applicationContext, "Received: $autofillableLoginData", Toast.LENGTH_SHORT).show()
                //Log.d("KeyspaceAutofillRecv", "Received: $autofillableLoginData")
            }
        }

        try {
            for (key in intent.extras!!.keySet()) intent.removeExtra (key)
        } catch (nothingLeft: NullPointerException) {
            Log.d("Keyspace", "Nothing left in intent because it was wiped successfully. You're in good hands... :)")
        }

        intent.action = null
        intent.data = null
        intent.replaceExtras(Bundle())
        intent.flags = 0

        return START_STICKY
    }

    override fun onInterrupt() { }

}