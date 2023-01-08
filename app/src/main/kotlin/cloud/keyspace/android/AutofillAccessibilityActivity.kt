package cloud.keyspace.android

import AutofillAccessibilityService
import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.keyspace.keyspacemobile.NetworkUtilities
import com.nulabinc.zxcvbn.Zxcvbn
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.log


class AutofillAccessibilityActivity : AppCompatActivity() {

    lateinit var urlsOnScreen: String
    lateinit var dataType: String

    lateinit var misc: MiscUtilities
    lateinit var crypto: CryptoUtilities
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities

    lateinit var biometricPrompt: BiometricPrompt

    lateinit var keyring: CryptoUtilities.Keyring
    lateinit var configData: SharedPreferences
    lateinit var vault: IOUtilities.Vault
    val mfaCodesTimer = Timer()

    var loginData: IOUtilities.Login? = null
    var cardData: IOUtilities.Card? = null
    var smsOtp: String? = null
    var _2faOtp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configData = getSharedPreferences (applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        setContentView(R.layout.activity_autofill_via_accessibility)

        val loadingScreen = ShowLoadingScreen ()

        if (isLoggedIn()) {
            try {

                val executor: Executor = ContextCompat.getMainExecutor(this@AutofillAccessibilityActivity) // execute on different thread awaiting response
                val biometricPromptThread = Handler(Looper.getMainLooper())

                try {
                    val biometricManager = BiometricManager.from(applicationContext)
                    val canAuthenticate =
                        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

                    if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                        Log.d("Keyspace", "Device lock found")
                    } else {
                        Log.d("Keyspace", "Device lock not set")
                        throw NoSuchMethodError()
                    }

                    val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { // Authentication succeeded
                            loadingScreen.showScreen()
                            Handler().postDelayed ({

                                crypto = CryptoUtilities(applicationContext, this@AutofillAccessibilityActivity)
                                keyring = crypto.retrieveKeys(crypto.getKeystoreMasterKey())!!

                                io = IOUtilities(applicationContext, this@AutofillAccessibilityActivity, keyring)
                                misc = MiscUtilities(applicationContext)

                                vault = decryptedVault (io.getVault())

                                Log.d("Keyspace", "Authentication successful")
                                biometricPromptThread.removeCallbacksAndMessages(null)

                                loadingScreen.killScreen()

                                if (intent.resolveActivity(applicationContext.packageManager).packageName == BuildConfig.APPLICATION_ID && intent.resolveActivity(applicationContext.packageManager).className == applicationContext.packageName + "." + getString(R.string.title_activity_autofill_accessibility)) {
                                    if (intent.extras!!.containsKey("siteUrl")) urlsOnScreen = intent.getStringExtra("siteUrl")!!
                                    if (intent.extras!!.containsKey("dataType")) dataType = intent.getStringExtra("dataType")!!
                                }

                                val fastLoginEnabled = configData.getBoolean("fastAutofill", false)

                                if (fastLoginEnabled) {
                                    fastLogin()
                                    return@postDelayed
                                } else {
                                    renderPasswordPicker ()
                                    return@postDelayed
                                }

                            }, 250)


                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { // Authentication error. Verify error code and message
                            Log.d("Keyspace", "Authentication canceled")
                            biometricPromptThread.removeCallbacksAndMessages(null)
                            biometricPrompt.cancelAuthentication()
                            loadingScreen.killScreen()
                            killApp()
                        }

                        override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                            Log.d("Keyspace", "Incorrect credentials supplied")
                        }
                    }

                    biometricPrompt = BiometricPrompt(this@AutofillAccessibilityActivity, executor, authenticationCallback)
                    val biometricPromptTitle = resources.getString(R.string.app_name)
                    val biometricPromptSubtitle = resources.getString(R.string.autofill_login_biometrics_generic_subtitle)
                    val biometricPromptDescription = resources.getString(R.string.autofill_login_biometrics_generic_description)
                    val builder = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(biometricPromptTitle)
                        .setSubtitle(biometricPromptSubtitle)
                        .setDescription(biometricPromptDescription)

                    builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    builder.setConfirmationRequired(true)

                    val promptInfo = builder.build()
                    biometricPrompt.authenticate(promptInfo)

                    biometricPromptThread.postDelayed({
                        biometricPrompt.cancelAuthentication()
                        val builder = MaterialAlertDialogBuilder(this@AutofillAccessibilityActivity)
                        builder.setTitle("Authentication error")
                        builder.setCancelable(false)
                        builder.setMessage("Authentication timed out because you waited too long.\n\nPlease try again.")
                        builder.setNegativeButton("Go back"){ _, _ ->
                            biometricPrompt.cancelAuthentication()
                            killApp()
                        }

                        try {
                            val errorDialog: AlertDialog = builder.create()
                            errorDialog.setCancelable(true)
                            errorDialog.show()
                        } catch (activityDead: WindowManager.BadTokenException) {}

                    }, (crypto.DEFAULT_AUTHENTICATION_DELAY - 2).toLong() * 1000)

                } catch (noLockSet: NoSuchMethodError) {
                    val builder = MaterialAlertDialogBuilder(this@AutofillAccessibilityActivity)
                    loadingScreen.killScreen()
                    builder.setTitle("No lock set")
                    builder.setIcon(getDrawable(R.drawable.ic_baseline_phonelink_lock_24))
                    builder.setCancelable(false)
                    builder.setMessage("Please set a device lock, reinstall Keyspace then try again.")
                    builder.setNegativeButton("Got it"){ _, _ ->
                        biometricPrompt.cancelAuthentication()
                        killApp()
                    }
                    Log.e("Keyspace", "Please set a screen lock.")
                    noLockSet.stackTrace
                } catch (incorrectCredentials: Exception) {
                    // killApp()
                    Log.e("Keyspace", "Your identity could not be verified.")
                    incorrectCredentials.stackTrace
                }

            } catch (keyringEmptyOrNull: NullPointerException) {
                loadingScreen.killScreen()
                keyringEmptyOrNull.printStackTrace()
                val builder = MaterialAlertDialogBuilder(this@AutofillAccessibilityActivity)
                builder.setTitle("Authentication error")
                builder.setIcon(getDrawable(R.drawable.ic_baseline_phonelink_lock_24))
                builder.setCancelable(false)
                builder.setMessage("Couldn't open authentication prompt. Tap the power button to lock your device, then unlock it and try autofilling again.")
                builder.setNegativeButton("Got it"){ _, _ ->
                    biometricPrompt.cancelAuthentication()
                    killApp()
                }

                try {
                    val errorDialog: AlertDialog = builder.create()
                    errorDialog.setCancelable(true)
                    errorDialog.show()
                    loadingScreen.killScreen()
                } catch (activityDead: WindowManager.BadTokenException) {}

            }

        } else {
            val builder = MaterialAlertDialogBuilder(this@AutofillAccessibilityActivity)
            builder.setTitle("Sign in")
            builder.setIcon(getDrawable(R.drawable.ic_baseline_error_24))
            builder.setCancelable(false)
            builder.setMessage("Please sign in to your Keyspace account to use autofill.")
            builder.setNegativeButton("Open Keyspace"){ _, _ ->
                killApp()
                val intent = Intent(this, StartHere::class.java)
                this.startActivity(intent)
            }
            builder.setOnCancelListener { killApp() }
            builder.setOnDismissListener { killApp() }
            try {
                val errorDialog: AlertDialog = builder.create()
                errorDialog.setCancelable(true)
                errorDialog.show()
            } catch (activityDead: WindowManager.BadTokenException) {}

        }

    }

    private fun isLoggedIn(): Boolean {
        return !configData.getString("userEmail", null).isNullOrEmpty()
    }

    inner class ShowLoadingScreen {

        var builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(this@AutofillAccessibilityActivity)
        private lateinit var dialog: AlertDialog
        private lateinit var iconography: ImageView
        private lateinit var loadingText: TextView
        private lateinit var loadingSubtitle: TextView
        private lateinit var loadingBar: ProgressBar

        fun showScreen () {
            builder.setCancelable(true)

            val accountInfoBox: View = layoutInflater.inflate(R.layout.loading_screen, null)
            builder.setView(accountInfoBox)

            iconography = accountInfoBox.findViewById<ImageView>(R.id.iconography)
            iconography.setImageDrawable(getDrawable(R.drawable.ic_baseline_phonelink_lock_24))
            iconography.scaleX = 0.75f
            iconography.scaleY = 0.75f

            loadingText = accountInfoBox.findViewById<TextView>(R.id.loadingText)
            loadingSubtitle = accountInfoBox.findViewById<TextView>(R.id.loadingSubtitle)
            loadingBar = accountInfoBox.findViewById<ProgressBar>(R.id.loadingBar)

            loadingText.setText("Decrypting vault")
            accountInfoBox.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.from_bottom))

            if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if strongbox exists
                loadingSubtitle.setText("Reading Strongbox")
            } else if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if hardware keystore exists
                loadingSubtitle.setText("Reading HAL Keystore")
            } else loadingSubtitle.setText("Reading Keystore")

            dialog = builder.create()
            dialog.show()
        }

        fun killScreen () {
            try {
                iconography.setImageDrawable(applicationContext.getDrawable(R.drawable.ic_baseline_check_24))
                loadingText.setText("Vault decrypted!")
                loadingBar.visibility = View.INVISIBLE
                loadingSubtitle.visibility = View.INVISIBLE
                dialog.dismiss()
            } catch (promptNotStarted: UninitializedPropertyAccessException) { }
        }

    }

    fun decryptedVault (vault: IOUtilities.Vault): IOUtilities.Vault {
        val decryptedTags = mutableListOf<IOUtilities.Tag>()
        if (vault.tag != null)
            for (tag in vault.tag!!) decryptedTags.add(io.decryptTag(tag)!!)

        val decryptedLogins = mutableListOf<IOUtilities.Login>()
        if (vault.login != null)
            for (login in vault.login!!) decryptedLogins.add(io.decryptLogin(login)!!)

        val decryptedNotes = mutableListOf<IOUtilities.Note>()
        if (vault.note != null)
            for (note in vault.note!!) decryptedNotes.add(io.decryptNote(note)!!)

        val decryptedCards = mutableListOf<IOUtilities.Card>()
        if (vault.card != null)
            for (card in vault.card!!) decryptedCards.add(io.decryptCard(card)!!)

        val decryptedVault = IOUtilities.Vault(
            version = vault.version,
            tag = decryptedTags,
            login = decryptedLogins,
            note = decryptedNotes,
            card = decryptedCards
        )
        return decryptedVault
    }

    fun fastLogin ()  {
        for (login in vault.login!!) {
            val trimmedUrls = mutableListOf<String?>()
            if (!login.loginData?.siteUrls.isNullOrEmpty()) {
                for (url in login.loginData?.siteUrls!!) trimmedUrls.add(url.replaceBefore("://", "").replace("://", ""))
            }
            
            for (trimmedUrl in trimmedUrls) {
                if (Regex(trimmedUrl!!).containsMatchIn(urlsOnScreen)) {
                    loginData = login
                    break
                }
            }

            biometricPrompt.cancelAuthentication()
            killApp()
        }
    }

    private fun renderPasswordPicker (){

        val builder = MaterialAlertDialogBuilder(this@AutofillAccessibilityActivity)
        builder.setCancelable(true)
        val noLockInfoBox: View = layoutInflater.inflate(R.layout.autofill_dialog, null)
        builder.setView(noLockInfoBox)
        noLockInfoBox.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.from_bottom))

        val dialog = builder.create()
        dialog.show()

        class LoginsAdapter (private val logins: MutableList<IOUtilities.Login>) : RecyclerView.Adapter<LoginsAdapter.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
                val loginCard: View = LayoutInflater.from(parent.context).inflate(R.layout.login, parent, false)
                loginCard.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

                return ViewHolder(loginCard)
            }

            override fun onBindViewHolder(loginCard: ViewHolder, position: Int) {  // binds the list items to a view
                val login = logins[loginCard.adapterPosition]

                loginCard.usernameText.isSelected = true

                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.Main) {
                        if (misc.getSiteIcon(login.name.toString(), null) != null) {
                            try {
                                loginCard.siteIcon.setImageDrawable(misc.getSiteIcon(login.name.toString(), null))
                            } catch (noIcon: IndexOutOfBoundsException) {
                                loginCard.siteIcon.setImageDrawable(DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_website_24)!!))
                            }
                        } else loginCard.siteIcon.setImageDrawable(DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_website_24)!!))
                    }
                }

                loginCard.siteIcon.setColorFilter(loginCard.siteName.currentTextColor)
                loginCard.siteName.text = login.name

                val tags =  vault.tag
                loginCard.tagText.isSelected = true
                loginCard.miscText.isSelected = true

                loginCard.tagText.visibility = View.GONE
                if (tags?.size!! > 0) {
                    for (tag in tags) {
                        val decryptedTag = io.decryptTag(tag)
                        if (login.tagId == tag.id) {
                            loginCard.tagText.visibility = View.VISIBLE
                            loginCard.tagText.text = decryptedTag?.name

                            try {
                                if (!decryptedTag!!.color.isNullOrEmpty()) {
                                    val tagIcon = DrawableCompat.wrap(getDrawable(R.drawable.ic_baseline_circle_24)!!)
                                    DrawableCompat.setTint(tagIcon, Color.parseColor(decryptedTag!!.color))
                                    DrawableCompat.setTintMode(tagIcon, PorterDuff.Mode.SRC_IN)
                                    loginCard.tagText.setCompoundDrawablesWithIntrinsicBounds(null, null, tagIcon, null)
                                }
                            } catch (noColor: StringIndexOutOfBoundsException) {} catch (noColor: IllegalArgumentException) {}

                            break
                        }
                    }
                }

                loginCard.usernameText.isSelected = true
                loginCard.siteName.isSelected = true

                if (!login.loginData?.email.isNullOrEmpty()) {
                    loginCard.usernameText.text = login.loginData!!.email
                    loginCard.usernameText.setCompoundDrawablesRelativeWithIntrinsicBounds(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_email_24),null,null, null)
                } else if (!login.loginData?.username.isNullOrEmpty()) {
                    loginCard.usernameText.text = login.loginData!!.username
                    loginCard.usernameText.setCompoundDrawablesRelativeWithIntrinsicBounds(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_account_circle_24),null,null, null)
                } else loginCard.usernameText.visibility = View.GONE

                // misc icon data
                if (login.favorite) {
                    loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null, ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24),null)
                    loginCard.miscText.text = ""//"Favorite"
                } else if (Zxcvbn().measure(login.loginData?.password.toString()).score <= 2) {
                    loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null, ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_warning_24),null)
                    loginCard.miscText.text = "Weak password"
                    if (login.loginData?.password.isNullOrEmpty()) loginCard.miscText.text = "No password"
                } else if (login.loginData?.password.isNullOrEmpty()) {
                    loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null, ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_warning_24),null)
                    loginCard.miscText.text = "No password"
                } else loginCard.miscText.visibility = View.GONE

                if (login.loginData?.password.isNullOrEmpty() && !login.loginData?.totp?.secret.isNullOrEmpty()) {
                    loginCard.miscText.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null, ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_time_24),null)
                    if (login.loginData?.email == login.name) loginCard.usernameText.visibility = View.GONE
                    loginCard.miscText.text = "2FA only"
                }

                var otpCode: String? = null
                if (!login.loginData?.totp?.secret.isNullOrEmpty()) {
                    loginCard.setIsRecyclable(false)
                    try {
                        otpCode = GoogleAuthenticator(base32secret = login.loginData?.totp!!.secret!!).generate()
                        runOnUiThread { loginCard.mfaText.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                        mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                            override fun run() {
                                val currentSeconds = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                                var halfMinuteElapsed = abs((60-currentSeconds))
                                if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                                try { loginCard.mfaProgress.progress = halfMinuteElapsed } catch (_: Exception) {  }
                                if (halfMinuteElapsed == 29) {
                                    otpCode = GoogleAuthenticator(base32secret = login.loginData.totp.secret.toString()).generate()
                                    runOnUiThread { loginCard.mfaText.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                                }
                            }
                        }, 0, 1000) // 1000 milliseconds = 1 second
                    } catch (timerError: IllegalStateException) { }

                } else loginCard.mfaLayout.visibility = View.GONE

                // handle clicks

                // tap on totp / mfa / 2fa
                loginCard.mfaLayout.setOnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Keyspace", otpCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(applicationContext, "Code copied!", Toast.LENGTH_LONG).show()
                }

                // tap on card
                loginCard.accountInformation.setOnClickListener {
                    loginData = login
                    dialog.cancel()
                }

            }

            override fun getItemCount(): Int {  // return the number of the items in the list
                return logins.size
            }

            inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {  // Holds the views for adding it to image and text
                val siteIcon: ImageView = itemView.findViewById(R.id.SiteIcon)
                val siteName: TextView = itemView.findViewById(R.id.SiteName)

                val tagText: TextView = itemView.findViewById(R.id.TagText)

                val usernameText: TextView = itemView.findViewById(R.id.usernameText)

                val mfaLayout: LinearLayout = itemView.findViewById(R.id.mfa)
                val mfaProgress: CircularProgressIndicator = itemView.findViewById(R.id.mfaProgress)
                val mfaText: TextView = itemView.findViewById(R.id.mfaText)

                val miscText: TextView = itemView.findViewById(R.id.MiscText)

                val accountInformation: LinearLayout = itemView.findViewById(R.id.LoginInformation)
            }
        }

        var loginsAdapter = LoginsAdapter(vault.login!!)
        var autofillLogins = dialog.findViewById<RecyclerView>(R.id.autofillLoginsRecycler) as RecyclerView
        autofillLogins.layoutManager = LinearLayoutManager(this@AutofillAccessibilityActivity)
        autofillLogins.adapter = loginsAdapter
        autofillLogins.layoutAnimation = AnimationUtils.loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
        loginsAdapter.notifyDataSetChanged()

        dialog.findViewById<Chip>(R.id.showLoginsButton)?.setOnClickListener {
            //autofillCards.visibility = View.GONE
            autofillLogins.visibility = View.VISIBLE
            loginsAdapter = LoginsAdapter(vault.login!!)
            autofillLogins = dialog.findViewById<RecyclerView>(R.id.autofillLoginsRecycler) as RecyclerView
            autofillLogins.layoutManager = LinearLayoutManager(this@AutofillAccessibilityActivity)
            autofillLogins.adapter = loginsAdapter
            autofillLogins.layoutAnimation = AnimationUtils.loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
            loginsAdapter.notifyDataSetChanged()
        }
        dialog.findViewById<Chip>(R.id.showLoginsButton)?.performClick()

        dialog.findViewById<Chip>(R.id.show2faButton)?.setOnClickListener {
            //autofillCards.visibility = View.GONE
            autofillLogins.visibility = View.VISIBLE

            val loginsContaining2faOnly = mutableListOf<IOUtilities.Login>()
            for (login in vault.login!!) {
                if (!login.loginData?.totp?.secret.isNullOrEmpty()) {
                    loginsContaining2faOnly.add (login)
                }
            }

            loginsAdapter = LoginsAdapter(loginsContaining2faOnly)
            autofillLogins = dialog.findViewById<RecyclerView>(R.id.autofillLoginsRecycler) as RecyclerView
            autofillLogins.layoutManager = LinearLayoutManager(this@AutofillAccessibilityActivity)
            autofillLogins.adapter = loginsAdapter
            autofillLogins.layoutAnimation = AnimationUtils.loadLayoutAnimation(applicationContext, R.anim.slide_right_anim_controller)
            loginsAdapter.notifyDataSetChanged()
        }

        dialog.findViewById<Chip>(R.id.openKeyspaceButton)!!.setOnClickListener {
            dialog.cancel()
            biometricPrompt.cancelAuthentication()
            killApp()
            val intent = Intent(this, StartHere::class.java)
            this.startActivity(intent)
        }

        dialog.setOnCancelListener {
            biometricPrompt.cancelAuthentication()
            killApp()
        }

    }

    private fun killApp () {
        overridePendingTransition(0, 0)

        // force wipe keyring
        try {
            keyring.XCHACHA_POLY1305_KEY?.fill(0)
            keyring.ED25519_PUBLIC_KEY?.fill(0)
            keyring.ED25519_PRIVATE_KEY?.fill(0)
        } catch (failedUnlock: UninitializedPropertyAccessException) {}

        System.gc()

        //mfaCodesTimer.cancel()
        //mfaCodesTimer.purge()
        finishAffinity()
        finishAndRemoveTask()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        try { biometricPrompt.cancelAuthentication() } catch (noPrompt: UninitializedPropertyAccessException) { }
        killApp()
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(applicationContext, AutofillAccessibilityService::class.java)
        val mapper = jacksonObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // In case the vault isn't updated, ignore new properties in json

        try {
            when {
                dataType.contains("login") -> {
                    intent.putExtra("dataFromAutofillActivity", mapper.writer().withDefaultPrettyPrinter().writeValueAsBytes(loginData))
                    intent.putExtra("dataType", "login")
                }

                dataType.contains("card") -> {
                    intent.putExtra("dataFromAutofillActivity", mapper.writer().withDefaultPrettyPrinter().writeValueAsBytes(cardData))
                    intent.putExtra("dataType", "card")
                }
                dataType.contains("smsOtp") -> {
                    intent.putExtra("dataFromAutofillActivity", smsOtp!!.toByteArray(StandardCharsets.UTF_8))
                    intent.putExtra("dataType", "smsOtp")
                }
                dataType.contains("2faOtp") -> {
                    intent.putExtra("dataFromAutofillActivity", _2faOtp!!.toByteArray(StandardCharsets.UTF_8))
                    intent.putExtra("dataType", "2faOtp")
                }

                else -> {  }
            }

            startService(intent)

            try {
                for (key in intent.extras!!.keySet()) intent.removeExtra (key)
            } catch (nothingLeft: NullPointerException) {
                Log.d("Keyspace", "Nothing left in intent because it was wiped successfully. You're in good hands... :)")
            }

            intent.action = null
            intent.data = null
            intent.replaceExtras(Bundle())
            intent.flags = 0

            try {
                biometricPrompt.cancelAuthentication()
                killApp()
            } catch (alreadyDead: java.lang.IllegalStateException) {}

        } catch (failedUnlock: UninitializedPropertyAccessException) {}

    }

}



