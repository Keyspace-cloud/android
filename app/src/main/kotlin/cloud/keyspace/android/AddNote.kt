package cloud.keyspace.android

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils.loadAnimation
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keyspace.keyspacemobile.NetworkUtilities
import com.yahiaangelo.markdownedittext.MarkdownEditText
import com.yydcdut.markdown.MarkdownConfiguration
import com.yydcdut.markdown.MarkdownProcessor
import com.yydcdut.markdown.callback.OnTodoClickCallback
import com.yydcdut.markdown.loader.DefaultLoader
import com.yydcdut.markdown.syntax.edit.EditFactory
import com.yydcdut.markdown.syntax.text.TextFactory
import com.yydcdut.markdown.theme.Theme
import com.yydcdut.markdown.theme.ThemeDefault
import com.yydcdut.markdown.theme.ThemeDesert
import java.time.Instant
import java.util.*
import java.util.concurrent.Executor
import kotlin.properties.Delegates

class AddNote : AppCompatActivity() {

    lateinit var utils: MiscUtilities
    lateinit var crypto: CryptoUtilities
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities

    lateinit var dateAndTime: TextView
    var timestamp by Delegates.notNull<Long>()

    lateinit var noteViewer: com.yydcdut.markdown.MarkdownEditText
    lateinit var noteEditorScrollView: MarkdownEditText
    lateinit var noteViewerScrollView: MarkdownEditText

    lateinit var tagButton: ImageView
    private lateinit var tagPicker: AddTag
    private var tagId: String? = null
    val tagIdGrabber = Handler(Looper.getMainLooper())

    var favorite: Boolean = false
    lateinit var favoriteButton: ImageView

    var noteColor: String? = null
    lateinit var colorButton: ImageView

    private var frequencyAccessed = 0L
    private var previousTimestamp = 0L

    lateinit var doneButton: ImageView
    lateinit var backButton: ImageView
    lateinit var deleteButton: ImageView

    var unrenderedText = ""
    var markdownToolbar = true
    lateinit var theme: Theme

    lateinit var keyring: CryptoUtilities.Keyring
    private var itemId: String? = null
    private lateinit var vault: IOUtilities.Vault
    private lateinit var note: IOUtilities.Note

    lateinit var configData: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_note)

        configData = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)

        val intent = intent
        val action = intent.action
        val type = intent.type

        if ("android.intent.action.SEND" == action && type != null && "text/plain" == type) {

            val biometricPromptThread = Handler(Looper.getMainLooper())
            val executor: Executor = ContextCompat.getMainExecutor(this@AddNote) // execute on different thread awaiting response

            try {
                val biometricManager = BiometricManager.from(this@AddNote)
                val canAuthenticate =
                    biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    Log.d("Keyspace", "Device lock found")
                } else {
                    Log.d("Keyspace", "Device lock not set")
                    throw NoSuchMethodError()
                }

                biometricPromptThread.removeCallbacksAndMessages(null)

                val decryptingDialogBuilder = MaterialAlertDialogBuilder(this@AddNote)
                val decryptingDialogBox = layoutInflater.inflate(R.layout.biometrics_screen, null)
                decryptingDialogBuilder.setCancelable(false).setView(decryptingDialogBox)
                val decryptingDialog = decryptingDialogBuilder.create()
                decryptingDialog.show()

                decryptingDialogBox.findViewById<MaterialButton>(R.id.authenticateButton).visibility = View.GONE
                val authenticateDescription = decryptingDialogBox.findViewById<TextView>(R.id.fingerprint_description)
                authenticateDescription.text = "Enter credentials to continue"

                val authenticationIcon = decryptingDialogBox.findViewById<ImageView>(R.id.fingerprint_icon)
                authenticationIcon.setPadding(0, 50, 0, 0)

                val authenticateTitle = decryptingDialogBox.findViewById<TextView>(R.id.fingerprint_title)
                val keystoreProgress = decryptingDialogBox.findViewById<ProgressBar>(R.id.keystoreProgress)

                val keyguardToUnlock = AnimatedVectorDrawableCompat.create(applicationContext, R.drawable.keyguardtolock)
                val fingerprintToUnlock = AnimatedVectorDrawableCompat.create(applicationContext, R.drawable.fingerprinttolock)
                val zoomSpin = loadAnimation(applicationContext, R.anim.zoom_spin)

                val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { // Authentication succeeded

                        Handler().postDelayed({
                            authenticateDescription.text = "Ed25519 public key"
                            Handler().postDelayed({ authenticateDescription.text = "Ed25519 private key"
                                Handler().postDelayed({ authenticateDescription.text = "XChaCha20-Poly1305 symmetric key" }, 50) }, 100) }, 100)

                        val keyringThread = Thread { keyring = crypto.retrieveKeys(crypto.getKeystoreMasterKey())!! }
                        keyringThread.start()

                        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if strongbox exists
                            authenticateTitle.text = "Reading Strongbox"
                        } else if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if hardware keystore exists
                            authenticateTitle.text = "Reading HAL Keystore"
                        } else authenticateTitle.text = "Reading Keystore"

                        if (utils.biometricsExist()) authenticationIcon.setImageDrawable(fingerprintToUnlock)
                        else authenticationIcon.setImageDrawable(keyguardToUnlock)

                        fingerprintToUnlock!!.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                            override fun onAnimationEnd(drawable: Drawable) {
                                authenticationIcon.setImageDrawable(applicationContext.getDrawable(R.drawable.ic_baseline_check_24))
                                keyringThread.join()

                                zoomSpin.setAnimationListener(object : Animation.AnimationListener {

                                    override fun onAnimationStart(animation: Animation) {
                                        keystoreProgress.visibility = View.INVISIBLE
                                        authenticateTitle.text = "All done!"
                                    }

                                    override fun onAnimationRepeat(animation: Animation) {  }

                                    @SuppressLint("UseCompatLoadingForDrawables")
                                    override fun onAnimationEnd(animation: Animation) {

                                        keyringThread.join()

                                        network = NetworkUtilities(applicationContext, this@AddNote, keyring)

                                        network = NetworkUtilities(applicationContext, this@AddNote, keyring)

                                        io = IOUtilities(applicationContext, this@AddNote, keyring)

                                        initializeUI()

                                        vault = io.getVault()

                                        if (itemId != null) {
                                            note = io.decryptNote(io.getNote(itemId!!, vault)!!)
                                            loadNote (note)

                                            frequencyAccessed = note.frequencyAccessed!!
                                        }

                                        decryptingDialog.dismiss()

                                        biometricPromptThread.removeCallbacksAndMessages(null)

                                        noteViewer.setText(intent.getStringExtra("android.intent.extra.TEXT").toString())

                                    }

                                })

                                authenticationIcon.startAnimation(zoomSpin)

                            }
                        })

                        fingerprintToUnlock.start()

                        Log.d("Keyspace", "Authentication successful")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { // Authentication error. Verify error code and message
                        biometricPromptThread.removeCallbacksAndMessages(null)
                        (applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(150)
                        Log.d("Keyspace", "Authentication canceled")
                        Toast.makeText(applicationContext, "Couldn't unlock Keyspace due to incorrect credentials.", Toast.LENGTH_LONG).show()
                        finishAffinity()
                    }

                    override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                        biometricPromptThread.removeCallbacksAndMessages(null)
                        (applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(150)
                        Log.d("Keyspace", "Incorrect credentials supplied")
                    }
                }

                val biometricPrompt = BiometricPrompt(this@AddNote, executor, authenticationCallback)

                val builder = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(resources.getString(R.string.app_name))
                    .setSubtitle(resources.getString(R.string.biometrics_generic_subtitle))
                    .setDescription(resources.getString(R.string.biometrics_share_sheet_item_description))
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                builder.setConfirmationRequired(true)

                val promptInfo = builder.build()
                biometricPrompt.authenticate(promptInfo)

                biometricPromptThread.postDelayed({
                    biometricPrompt.cancelAuthentication()
                    biometricPromptThread.removeCallbacksAndMessages(null)
                    val timeoutDialogBuilder = MaterialAlertDialogBuilder(this@AddNote)
                    timeoutDialogBuilder.setTitle("Authentication error")
                    timeoutDialogBuilder.setMessage("Authentication timed out because you waited too long.\n\nPlease try again.")
                    timeoutDialogBuilder.setNegativeButton("Retry") { _, _ ->
                        biometricPrompt.authenticate(promptInfo)
                    }

                    try {
                        val timeoutDialog: AlertDialog = timeoutDialogBuilder.create()
                        timeoutDialog.setCancelable(true)
                        timeoutDialog.show()
                    } catch (_: WindowManager.BadTokenException) { }

                }, (crypto.DEFAULT_AUTHENTICATION_DELAY - 2).toLong() * 1000)


            } catch (noLockSet: NoSuchMethodError) {
                biometricPromptThread.removeCallbacksAndMessages(null)
                noLockSet.printStackTrace()
                val builder = MaterialAlertDialogBuilder(this@AddNote)
                builder.setTitle("No biometric hardware")
                builder.setMessage("Your biometric sensors (fingerprint, face ID or iris scanner) could not be accessed. Please add biometrics from your phone's settings to continue.\n\nTry restarting your phone if you have already enrolled biometrics.")
                builder.setNegativeButton("Exit") { _, _ ->
                    this@AddNote.finishAffinity()
                }
                val errorDialog: AlertDialog = builder.create()
                errorDialog.setCancelable(true)
                errorDialog.show()

                Log.e("Keyspace", "Please set a screen lock.")
                noLockSet.stackTrace

            } catch (incorrectCredentials: Exception) {
                biometricPromptThread.removeCallbacksAndMessages(null)
                incorrectCredentials.printStackTrace()
                val builder = MaterialAlertDialogBuilder(this@AddNote)
                builder.setTitle("Authentication failed")
                builder.setMessage("Your identity couldn't be verified. Please try again after a while.")
                builder.setNegativeButton("Exit") { _, _ ->
                    this@AddNote.finishAffinity()
                }
                val errorDialog: AlertDialog = builder.create()
                errorDialog.setCancelable(true)
                errorDialog.show()

                Log.e("Keyspace", "Your identity could not be verified.")
                incorrectCredentials.stackTrace

            }


        } else {

            val intentData = crypto.receiveKeyringFromSecureIntent (
                currentActivityClassNameAsString = getString(R.string.title_activity_add_note),
                intent = intent
            )

            keyring = intentData.first

            itemId = intentData.second

            /////

            network = NetworkUtilities(applicationContext, this, keyring)

            network = NetworkUtilities(applicationContext, this, keyring)

            io = IOUtilities(applicationContext, this, keyring)

            initializeUI()

            vault = io.getVault()

            if (itemId != null) {
                note = io.decryptNote(io.getNote(itemId!!, vault)!!)
                loadNote (note)

                frequencyAccessed = note.frequencyAccessed!!
            }

        }

    }

    @SuppressLint("UseCompatLoadingForDrawables", "ClickableViewAccessibility", "SetTextI18n")
    private fun initializeUI (): Boolean {
        theme = when (applicationContext.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> ThemeDesert()
            Configuration.UI_MODE_NIGHT_NO -> ThemeDefault()
            Configuration.UI_MODE_NIGHT_UNDEFINED -> ThemeDefault()
            else -> ThemeDefault()
        }

        noteViewer = findViewById(R.id.noteViewer)
        noteViewer.isActivated = true
        noteViewer.isPressed = true

        val markdownConfig = MarkdownConfiguration.Builder(applicationContext)
            .setTheme(theme)
            .showLinkUnderline(true)
            .setOnLinkClickCallback { _, link ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
            .setLinkFontColor(R.attr.colorControlActivated)
            .setOnTodoClickCallback(object : OnTodoClickCallback {
                override fun onTodoClicked(view: View?, line: String?, lineNumber: Int): CharSequence { return "" }
            })
            .setRxMDImageLoader(DefaultLoader(applicationContext))
            .build()

        val markdownProcessor = MarkdownProcessor(this)
        markdownProcessor.config(markdownConfig)
        markdownProcessor.factory(EditFactory.create())
        markdownProcessor.live(noteViewer)

        timestamp = Instant.now().epochSecond

        dateAndTime = findViewById(R.id.dateAndTime)
        dateAndTime.visibility = View.GONE

        // Load toolbar
        val noteToolbar = findViewById<HorizontalScrollView>(R.id.noteToolbar)

        noteToolbar.visibility = View.VISIBLE

        findViewById<ImageView>(R.id.helpButton).setOnClickListener {
            val inflater = layoutInflater
            val dialogView: View = inflater.inflate (R.layout.markdown_help, null)
            val dialogBuilder = MaterialAlertDialogBuilder(this)
            dialogBuilder
                .setView(dialogView)
                .setTitle("Markdown guide")
                .setIcon(getDrawable(R.drawable.markdown))
                .setCancelable(true)
            val markdownDialog = dialogBuilder.show()
            val markdownUnrendered = markdownDialog.findViewById<View>(R.id.guide) as TextView
            val markdownRendered = markdownDialog.findViewById<View>(R.id.guideRendered) as com.yydcdut.markdown.MarkdownEditText
            val markdownConfig = MarkdownConfiguration.Builder(applicationContext)
                .setTheme(theme)
                .showLinkUnderline(true)
                .setOnLinkClickCallback { view, link ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }
                .setOnLinkClickCallback { _, link ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }
                .setLinkFontColor(R.attr.colorControlActivated)
                .setOnTodoClickCallback(object : OnTodoClickCallback {
                    override fun onTodoClicked(view: View?, text: String?, lineNumber: Int): CharSequence {
                        return text.toString()
                    }
                })
                .setRxMDImageLoader(DefaultLoader(applicationContext))
                .build()
            var markdownProcessor = MarkdownProcessor(this)
            markdownProcessor.config(markdownConfig)
            markdownProcessor.factory(TextFactory.create())
            markdownProcessor.live(markdownRendered)
            markdownUnrendered.visibility = View.VISIBLE
            markdownRendered.visibility = View.GONE
            markdownUnrendered.startAnimation(loadAnimation(applicationContext, R.anim.from_top))
            val renderButton = markdownDialog.findViewById<View>(R.id.renderButton) as MaterialButton
            var rendered = false
            renderButton.setOnClickListener {
                if (!rendered) {
                    rendered = true
                    markdownUnrendered.visibility = View.GONE
                    markdownRendered.visibility = View.VISIBLE
                    renderButton.text = "Tap to view raw"
                    renderButton.icon = getDrawable(R.drawable.ic_baseline_visibility_off_24)
                } else {
                    rendered = false
                    markdownUnrendered.visibility = View.VISIBLE
                    markdownRendered.visibility = View.GONE
                    renderButton.text = "Tap to render"
                    renderButton.icon = getDrawable(R.drawable.ic_baseline_visibility_24)
                }
            }
            val backButton =  markdownDialog.findViewById<View>(R.id.backButton) as MaterialButton
            backButton.setOnClickListener { markdownDialog.dismiss() }
            dialogBuilder.create()
        }

        findViewById<ImageView>(R.id.numberListButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteViewer.setText(noteViewer.text.toString().replace(selectedText, utils.stringToNumberedString(selectedText)))
            else noteViewer.append(utils.stringToNumberedString(selectedText))
            noteViewer.setSelection(noteViewer.text.toString().length)
        }

        findViewById<ImageView>(R.id.bulletListButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteViewer.setText(noteViewer.text.toString().replace(selectedText, utils.stringToBulletedString(selectedText)))
            else noteViewer.append(utils.stringToBulletedString(selectedText))
            noteViewer.setSelection(noteViewer.text.toString().length)
        }

        findViewById<ImageView>(R.id.linkButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "[${selectedText}]()"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length + 2)
            } else {
                val markdown = "[text](url)"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.italicButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "_${selectedText}_"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "_text_"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.checkedButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteViewer.setText(noteViewer.text.toString().replace(selectedText, utils.stringToCheckedString(selectedText)))
            else noteViewer.append(utils.stringToCheckedString(selectedText))
            noteViewer.setSelection(noteViewer.text.toString().length)
        }

        findViewById<ImageView>(R.id.uncheckedButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteViewer.setText(noteViewer.text.toString().replace(selectedText, utils.stringToUncheckedString(selectedText)))
            else noteViewer.append(utils.stringToUncheckedString(selectedText))
            noteViewer.setSelection(noteViewer.text.toString().length)
        }

        findViewById<ImageView>(R.id.imageButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "![${selectedText}]()"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length + 2)
            } else {
                val markdown = "![caption](url)"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.lineButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "$selectedText\n****"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n****"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.quoteButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "> $selectedText"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n> "
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "> ", 0, "> ".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.strikethroughButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "~~$selectedText~~"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "~~text~~"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.codeButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "\n```\n$selectedText\n```"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "```\ntext\n```"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "```\ntext\n```", 0, "```\ntext\n```".length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.boldButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteViewer.setText(noteViewer.text.toString().replace(selectedText, "**$selectedText**"))
                noteViewer.setSelection(noteViewer.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "**text**"
                try {
                    noteViewer.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteViewer.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.titleButton).setOnClickListener {
            val start = noteViewer.selectionStart.coerceAtLeast(0)
            val end = noteViewer.selectionEnd.coerceAtLeast(0)
            val selectedText = noteViewer.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteViewer.setText(noteViewer.text.toString().replace(selectedText, utils.stringToTitledStrings(selectedText)))
            else noteViewer.append(utils.stringToTitledStrings(selectedText))
            noteViewer.setSelection(noteViewer.text.toString().length)
        }

        doneButton = findViewById (R.id.done)
        doneButton.setOnClickListener {
            if (noteViewer.text.isNullOrBlank() || noteViewer.text.toString().length <= 1) {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Blank Note")
                alertDialog.setMessage("Note can't be blank")
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
                alertDialog.show()
            } else saveNote()

        }

        findViewById<TextView>(R.id.toolbarTitle).visibility = View.VISIBLE

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        deleteButton = findViewById (R.id.delete)
        if (itemId != null) {
            deleteButton.setOnClickListener {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Delete")
                alertDialog.setMessage("Would you like to delete this note?")
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delete") { dialog, _ ->

                    vault.note!!.remove(io.getNote(itemId!!, vault))
                    io.writeVault(vault)

                    network.writeQueueTask (itemId!!, mode = network.MODE_DELETE)
                    crypto.secureStartActivity (
                        nextActivity = Dashboard(),
                        nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                        keyring = keyring,
                        itemId = null
                    )

                }
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
                alertDialog.show()

            }
        } else {
            deleteButton.visibility = View.GONE
        }

        tagButton = findViewById (R.id.tag)
        tagPicker = AddTag (tagId, applicationContext, this@AddNote, keyring)

        tagButton.setOnClickListener {
            tagPicker.showPicker(tagId)
            tagIdGrabber.post(object : Runnable {
                override fun run() {
                    tagId = tagPicker.getSelectedTagId()
                    tagIdGrabber.postDelayed(this, 100)
                }
            })
        }

        favoriteButton = findViewById(R.id.favoriteButton)
        favoriteButton.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
        favoriteButton.setOnClickListener {
            favorite = if (!favorite) {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24))
                favoriteButton.startAnimation(loadAnimation(applicationContext, R.anim.heartbeat))
                true
            } else {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
                false
            }
        }

        colorButton = findViewById(R.id.colorButton)
        colorButton.setOnClickListener {
            MaterialColorPickerDialog.Builder(this@AddNote)
                .setColors(resources.getStringArray(R.array.vault_item_colors))
                .setTickColorPerCard(true)
                .setDefaultColor(noteColor.toString())
                .setColorListener(object : ColorListener {
                    override fun onColorSelected(color: Int, colorHex: String) {
                        noteColor = colorHex
                        noteViewer.setBackgroundColor(Color.parseColor(noteColor))
                        if (noteColor != null) {
                            val intColor: Int = noteColor!!.replace("#", "").toInt(16)
                            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
                            if (g >= 200 || b >= 200) {
                                noteViewer.setTextColor (Color.BLACK)
                                noteViewer.setHintTextColor(Color.BLACK)
                            } else {
                                noteViewer.setTextColor(Color.WHITE)
                                noteViewer.setHintTextColor(Color.WHITE)
                            }
                        }
                    }
                })
                .show()
        }

        return true
    }

    private fun loadNote (note: IOUtilities.Note): Boolean {
        favorite = if (note.favorite) {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_24)); true
        } else {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_border_24)); false
        }

        tagId = note.tagId
        tagPicker = AddTag (tagId, applicationContext, this@AddNote, keyring)

        dateAndTime.visibility = View.VISIBLE

        previousTimestamp = note.dateModified!!

        val time = Calendar.getInstance(Locale.ENGLISH)
        time.timeInMillis = previousTimestamp?.times(1000L)!!
        dateAndTime.text = "Last edited on " + DateFormat.format("MMM dd, yyyy ⋅  hh:mm a", time).toString()

        if (!note.notes.isNullOrEmpty()) {
            noteViewer.setText (note.notes)
        }

        if (!note.color.isNullOrEmpty()) {
            noteColor = note.color
            noteViewer.setBackgroundColor(Color.parseColor(noteColor))
            val intColor: Int = noteColor!!.replace("#", "").toInt(16)
            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
            if (g >= 200 || b >= 200) {
                noteViewer.setTextColor (Color.BLACK)
                noteViewer.setHintTextColor(Color.BLACK)
            } else {
                noteViewer.setTextColor(Color.WHITE)
                noteViewer.setHintTextColor(Color.WHITE)
            }

        }

        return true
    }

    private fun saveNote () {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = note.dateCreated!!
            vault.note?.remove(io.getNote(itemId!!, vault))
        }

        val data = IOUtilities.Note (
            id = itemId ?: UUID.randomUUID().toString(),
            organizationId = null,
            type = io.TYPE_NOTE,
            notes = noteViewer.text.toString(),
            color = noteColor,
            favorite = favorite,
            tagId = tagPicker.getSelectedTagId() ?: tagId,
            dateCreated = dateCreated,
            dateModified = timestamp,
            frequencyAccessed = frequencyAccessed
        )

        val encryptedNote = io.encryptNote(data)

        vault.note?.add (encryptedNote)
        io.writeVault(vault)

        if (itemId != null) network.writeQueueTask (encryptedNote, mode = network.MODE_PUT)
        else network.writeQueueTask (encryptedNote, mode = network.MODE_POST)

        crypto.secureStartActivity (
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
            keyring = keyring,
            itemId = null
        )

    }

    private fun getKeyringForShareSheet () {

    }

    override fun onBackPressed () {
        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
        alertDialog.setTitle("Confirm exit")
        alertDialog.setMessage("Would you like to go back to the Dashboard?")
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Exit") { dialog, _ ->
            crypto.secureStartActivity (
                nextActivity = Dashboard(),
                nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                keyring = keyring,
                itemId = null
            )
            super.onBackPressed()
            tagIdGrabber.removeCallbacksAndMessages(null)
        }
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }

    override fun onUserLeaveHint() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()
        }
        super.onUserLeaveHint()

    }

}