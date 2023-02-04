package cloud.keyspace.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.view.animation.AnimationUtils.loadAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budiyev.android.codescanner.*
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.keyspace.keyspacemobile.NetworkUtilities
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs


class AddLogin : AppCompatActivity() {
    @SuppressLint("SetTextI18n")

    lateinit var utils: MiscUtilities
    lateinit var crypto: CryptoUtilities
    lateinit var misc: MiscUtilities
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities

    lateinit var siteNameLayout: TextInputLayout
    lateinit var siteNameInput: TextInputEditText
    lateinit var siteNameInputIcon: ImageView
    lateinit var siteNameIconPicker: ImageView

    lateinit var userNameInputLayout: TextInputLayout
    lateinit var userNameInput: TextInputEditText

    lateinit var emailInputLayout: TextInputLayout
    lateinit var emailInput: TextInputEditText

    lateinit var emailAsUsername: MaterialSwitch

    lateinit var passwordInputLayout: TextInputLayout
    lateinit var passwordInput: TextInputEditText
    lateinit var clearButton: ImageView

    lateinit var passwordHistoryData: MutableList<IOUtilities.Password>
    lateinit var passwordHistoryView: RecyclerView
    lateinit var passwordHistoryAdapter: PasswordHistoryAdapter
    lateinit var passwordHistoryButton: Button

    lateinit var uppercaseSwitch: MaterialSwitch
    lateinit var lowercaseSwitch: MaterialSwitch
    lateinit var numbersSwitch: MaterialSwitch
    lateinit var symbolsSwitch: MaterialSwitch
    lateinit var phrasesSwitch: MaterialSwitch
    lateinit var passwordLength: Slider
    lateinit var length: TextView
    lateinit var refreshPassword: ImageView
    lateinit var copyPassword: Button

    lateinit var mfaTokenBox: LinearLayout
    lateinit var secretInputLayout: TextInputLayout
    lateinit var secretInput: TextInputEditText
    lateinit var tokenPreview: TextView
    lateinit var mfaProgress: ProgressBar
    lateinit var qrCodeButton: ImageView
    lateinit var qrCodeDialog: AlertDialog

    lateinit var backupCodesInputLayout: TextInputLayout
    lateinit var backupCodesInput: BackupCodesEditText
    lateinit var backupCodesHelpButton: ImageView

    lateinit var siteUrlsData: MutableList<String>
    lateinit var siteUrlsView: RecyclerView
    lateinit var siteUrlsAdapter: SiteUrlsAdapter
    lateinit var addSiteUrlButton: MaterialButton
    lateinit var siteUrlsHelpButton: ImageView

    lateinit var notesInputLayout: TextInputLayout
    lateinit var notesInput: TextInputEditText

    lateinit var customFieldsData: MutableList<IOUtilities.CustomField>
    lateinit var customFieldsView: RecyclerView
    lateinit var customFieldsAdapter: CustomFieldsAdapter
    lateinit var addCustomFieldButton: MaterialButton

    var iconFileName: String? = null

    var passwordCopied = false

    lateinit var tagButton: ImageView
    private lateinit var tagPicker: AddTag
    var tagId: String? = null
    val tagIdGrabber = Handler(Looper.getMainLooper())

    lateinit var favoriteButton: ImageView
    var favorite: Boolean = false

    lateinit var doneButton: ImageView
    lateinit var backButton: ImageView
    lateinit var deleteButton: ImageView
    var deleted: Boolean = false

    lateinit var keyring: CryptoUtilities.Keyring
    private var itemId: String? = null
    private lateinit var vault: IOUtilities.Vault
    private lateinit var login: IOUtilities.Login

    private var timer = Timer()
    var currentSeconds: Int = 0
    var otpCode: String? = null

    lateinit var configData: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_login)

        configData = getSharedPreferences (applicationContext.packageName + "_configuration_data", MODE_PRIVATE)
        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)
        misc = MiscUtilities(applicationContext)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_add_login),
            intent = intent
        )

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        keyring = intentData.first
        network = NetworkUtilities(applicationContext, this, keyring)
        itemId = intentData.second

        io = IOUtilities(applicationContext, this, keyring)

        initializeUI()

        vault = io.getVault()
        if (itemId != null) {
            login = io.decryptLogin(io.getLogin(itemId!!, vault)!!)
            loadLogin (login)
        }
    }

    private fun initializeUI (): Boolean {
        doneButton = findViewById (R.id.done)
        doneButton.setOnClickListener {
            if (siteNameInput.text.isNullOrBlank()) {
                siteNameInput.error = "Please enter a username"
                return@setOnClickListener
            }

            if (emailInput.text.toString().isNotBlank()) {
                if (!misc.isValidEmail(emailInput.text.toString())) {
                    emailInput.error = "Please enter a valid email"
                    return@setOnClickListener
                }
            }

            saveItem()

        }

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        deleteButton = findViewById (R.id.delete)
        if (itemId != null) {
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                deleted = !deleted
                saveItem()
            }
        } else deleteButton.visibility = View.GONE

        tagButton = findViewById (R.id.tag)
        tagPicker = AddTag (tagId, applicationContext, this@AddLogin, keyring)
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

        siteNameInput = findViewById (R.id.siteNameInput)
        siteNameInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
        siteNameLayout = findViewById (R.id.siteNameInputLayout)

        siteNameInputIcon = findViewById (R.id.siteNameInputIcon)
        siteNameInputIcon.setOnClickListener {
            iconFilePicker()
        }

        siteNameIconPicker = findViewById (R.id.pickIcon)
        siteNameIconPicker.setOnClickListener {
            iconFilePicker()
        }

        siteNameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { }
            override fun beforeTextChanged(siteName: CharSequence, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(siteName: CharSequence, start: Int, before: Int, count: Int) {
                thread {
                    val siteLogo = misc.getSiteIcon(siteName.toString(), siteNameInput.currentTextColor)
                    if (siteLogo != null/* && iconFileName == null*/) {
                        iconFileName = siteName.toString()
                        runOnUiThread {
                            siteNameInputIcon.setImageDrawable(siteLogo)
                        }
                    }
                }
            }
        })

        userNameInput = findViewById (R.id.userNameInput)
        userNameInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
        userNameInputLayout = findViewById (R.id.userNameInputLayout)
        userNameInputLayout.visibility = View.GONE

        emailAsUsername = findViewById (R.id.emailAsUsername)
        emailAsUsername.isChecked = true
        emailAsUsername.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) userNameInputLayout.visibility = View.GONE
            else userNameInputLayout.visibility = View.VISIBLE
        }

        emailInput = findViewById (R.id.emailInput)
        emailInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING

        passwordInput = findViewById (R.id.passwordInput)
        passwordInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
        passwordHistoryButton = findViewById (R.id.passwordHistoryButton)
        passwordInputLayout = findViewById (R.id.passwordInputLayout)

        secretInput = findViewById (R.id.secretInput)
        secretInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING

        backupCodesInput = findViewById(R.id.backupCodesInput)
        backupCodesInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
        backupCodesHelpButton = findViewById (R.id.backupCodesHelpButton)

        siteUrlsHelpButton = findViewById (R.id.siteUrlsHelpButton)

        notesInput = findViewById (R.id.notesInput)
        notesInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING

        uppercaseSwitch = findViewById (R.id.uppercaseSwitch)
        lowercaseSwitch = findViewById (R.id.lowercaseSwitch)
        numbersSwitch = findViewById (R.id.numbersSwitch)
        symbolsSwitch = findViewById (R.id.symbolsSwitch)
        phrasesSwitch = findViewById (R.id.phrasesSwitch)

        length = findViewById (R.id.length)
        refreshPassword = findViewById (R.id.refresh)
        passwordLength = findViewById (R.id.passwordLength)

        copyPassword = findViewById (R.id.copyPassword)

        mfaTokenBox = findViewById (R.id.mfaTokenBox)
        tokenPreview = findViewById (R.id.tokenPreview)
        mfaProgress = findViewById (R.id.mfaProgress)

        secretInput = findViewById (R.id.secretInput)
        secretInput.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
        qrCodeButton = findViewById(R.id.qrCodeButton)

        passwordHistoryButton.visibility = View.GONE

        // password box logic
        var upper = false; var lower = false; var numbers = false; var symbols = false; var passwordLengthInt = 32

            uppercaseSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    upper = true
                    phrasesSwitch.isChecked = false
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                } else {
                    uppercaseSwitch.isChecked = false
                    lowercaseSwitch.isChecked = false
                    numbersSwitch.isChecked = false
                    symbolsSwitch.isChecked = false
                    upper = false
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                }
            }
            uppercaseSwitch.isChecked = true

            lowercaseSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    uppercaseSwitch.isChecked = true
                    lower = true
                    phrasesSwitch.isChecked = false
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                } else {
                    uppercaseSwitch.isChecked = false
                    lowercaseSwitch.isChecked = false
                    numbersSwitch.isChecked = false
                    symbolsSwitch.isChecked = false
                    lower = false
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                }
            }
            lowercaseSwitch.isChecked = true

            numbersSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    uppercaseSwitch.isChecked = true
                    lowercaseSwitch.isChecked = true
                    numbers = true
                    uppercaseSwitch.isEnabled
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                } else {
                    uppercaseSwitch.isChecked = false
                    lowercaseSwitch.isChecked = false
                    numbers = false
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                }
            }

            symbolsSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    uppercaseSwitch.isChecked = true
                    lowercaseSwitch.isChecked = true
                    numbersSwitch.isChecked = true
                    symbols = true
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                } else {
                    uppercaseSwitch.isChecked = false
                    lowercaseSwitch.isChecked = false
                    numbersSwitch.isChecked = false
                    symbols = false
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                }
            }

            phrasesSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    uppercaseSwitch.isChecked = false
                    lowercaseSwitch.isChecked = false
                    numbersSwitch.isChecked = false
                    symbolsSwitch.isChecked = false

                    passwordLength.valueFrom = 2F
                    passwordLength.valueTo = 24F
                    passwordLength.value = 3F

                    length.text = "${passwordLengthInt} words"

                    passwordInput.setText(utils.passphraseGenerator(passwordLengthInt))
                } else {
                    uppercaseSwitch.isChecked = true
                    lowercaseSwitch.isChecked = true
                    numbersSwitch.isChecked = true
                    symbolsSwitch.isChecked = true

                    passwordLength.valueFrom = 4F
                    passwordLength.valueTo = 128F
                    passwordLength.value = 16F

                    length.text = "${passwordLengthInt} characters"
                    try {
                        passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                    } catch (weirdArgs: IllegalArgumentException) { }
                }
            }



        passwordInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                passwordInput.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable) { }

                    override fun beforeTextChanged(input: CharSequence, start: Int, count: Int, after: Int) { }

                    override fun onTextChanged(input: CharSequence, start: Int, before: Int, count: Int) {
                        length.text = "${input.length} characters"
                    }
                })
            }
        }

        passwordLength.addOnChangeListener (Slider.OnChangeListener { _, value, _ ->
            passwordLengthInt = value.toInt()
            if (phrasesSwitch.isChecked) {
                passwordLength.valueFrom = 3F
                passwordLength.valueTo = 24F
                length.text = "${passwordLengthInt} words"
                passwordInput.setText(utils.passphraseGenerator (passwordLengthInt))
            } else {
                passwordLength.valueFrom = 4F
                passwordLength.valueTo = 128F
                length.text = "${passwordLengthInt} characters"
                try {
                    passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                } catch (weirdArgs: IllegalArgumentException) {
                    lowercaseSwitch.isChecked = true
                }
            }
        })

        refreshPassword.setOnClickListener {
            refreshPassword.startAnimation(loadAnimation(applicationContext, R.anim.spin))
            refreshPassword.performHapticFeedback(HapticFeedbackConstants.REJECT)
            Toast.makeText(applicationContext, "Password changed!", Toast.LENGTH_SHORT).show()

            if (phrasesSwitch.isChecked) {
                passwordInput.setText(utils.passphraseGenerator (passwordLengthInt))
            }

            if (!phrasesSwitch.isChecked) {
                try {
                    passwordInput.setText(utils.passwordGenerator (passwordLengthInt, upper, lower, numbers, symbols))
                } catch (weirdArgs: IllegalArgumentException) {
                    lowercaseSwitch.isChecked = true
                }
            }

        }

        clearButton = findViewById(R.id.clearButton)
        clearButton.setOnClickListener {
            passwordInput.text = null
            passwordInput.clearFocus()
            refreshPassword.performHapticFeedback(HapticFeedbackConstants.REJECT)
            Toast.makeText(applicationContext, "Password cleared!", Toast.LENGTH_SHORT).show()
        }

        copyPassword.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Keyspace", passwordInput.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Copied!", Toast.LENGTH_SHORT).show()
        }

        fun codeScannerDialog () {
            val inflater = layoutInflater
            val dialogView: View = inflater.inflate (R.layout.qr_code_scanner, null)
            val dialogBuilder = MaterialAlertDialogBuilder(this)
            dialogBuilder
                .setView(dialogView)
                .setBackground(ColorDrawable(Color.TRANSPARENT))
                .setCancelable(true)
            qrCodeDialog = dialogBuilder.show()
            val viewfinder = qrCodeDialog.findViewById<View>(R.id.code_scanner) as CodeScannerView
            var scannedData: String

            val codeScanner = CodeScanner(this, viewfinder)

            codeScanner.camera = CodeScanner.CAMERA_BACK
            codeScanner.formats = CodeScanner.TWO_DIMENSIONAL_FORMATS
            codeScanner.autoFocusMode = AutoFocusMode.SAFE
            codeScanner.scanMode = ScanMode.SINGLE
            codeScanner.isAutoFocusEnabled = true
            codeScanner.isFlashEnabled = false
            codeScanner.isTouchFocusEnabled = true

            codeScanner.decodeCallback = DecodeCallback {
                runOnUiThread {
                    scannedData = it.text
                    qrCodeDialog.dismiss()
                    try {
                        val decoded2faData = utils.decodeOTPAuthURL(scannedData)
                        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()

                        if (decoded2faData?.secret != null) {
                            secretInput.setText(decoded2faData.secret)
                            if (siteNameInput.text.isNullOrBlank()) siteNameInput.setText(decoded2faData.issuer ?: decoded2faData.account ?: decoded2faData.label )
                            if (emailInput.text.isNullOrBlank() && misc.isValidEmail(decoded2faData.issuer ?: decoded2faData.account ?: decoded2faData.label)) emailInput.setText(decoded2faData.account)
                            mfaDialog()
                        } else {
                            alertDialog.setTitle("Invalid QR code")
                            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Go back") { dialog, _ ->
                                dialog.dismiss()
                            }
                            alertDialog.setMessage ("This QR Code does not contain valid two-factor authentication data.")
                            alertDialog.show()
                        }
                    } catch (noSecret: Exception) {
                        when (noSecret) {
                            is IllegalStateException, is NullPointerException -> {
                                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                                alertDialog.setTitle("Invalid QR code")
                                alertDialog.setMessage("This QR Code does not contain valid two-factor authentication data.")
                                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Go back") { _, _ -> alertDialog.dismiss() }
                                alertDialog.show()
                                secretInput.text = null
                            }
                        }
                    }
                }
            }

            codeScanner.errorCallback = ErrorCallback { // or ErrorCallback.SUPPRESS
                runOnUiThread {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
                    qrCodeDialog.dismiss()
                }
            }

            codeScanner.startPreview()

            val textHint = dialogView.findViewById<View>(R.id.textHint) as TextView
            textHint.text = "Put your 2FA QR Code in frame"
            val closeButton = dialogView.findViewById<View>(R.id.closeButton) as Button
            closeButton.setOnClickListener {
                qrCodeDialog.dismiss()
            }

            qrCodeDialog.setOnDismissListener (object : PopupMenu.OnDismissListener, DialogInterface.OnDismissListener {
                override fun onDismiss(menu: PopupMenu?) { qrCodeDialog.dismiss() }
                override fun onDismiss(p0: DialogInterface?) { qrCodeDialog.dismiss() }
            })

            qrCodeDialog.setOnDismissListener (object : PopupMenu.OnDismissListener, DialogInterface.OnDismissListener {
                override fun onDismiss(menu: PopupMenu?) {
                    codeScanner.stopPreview()
                    codeScanner.releaseResources()
                }

                override fun onDismiss(p0: DialogInterface?) {
                    codeScanner.stopPreview()
                    codeScanner.releaseResources()
                }
            })
        }

        qrCodeButton.setOnClickListener {
            codeScannerDialog ()
        }

        mfaTokenBox.visibility = View.GONE
        mfaTokenBox.setOnClickListener {
            mfaDialog()
        }

        secretInput.addTextChangedListener (object : TextWatcher {
            override fun afterTextChanged (s: Editable) { }
            override fun beforeTextChanged (s: CharSequence, start: Int, count: Int, after: Int) { }
            override fun onTextChanged (s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.length >= 8) {
                    try {
                        otpCode = GoogleAuthenticator(base32secret = secretInput.text.toString()).generate()
                        runOnUiThread { tokenPreview.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                        timer.scheduleAtFixedRate(object : TimerTask() {
                            override fun run() {
                                val currentSeconds = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                                var halfMinuteElapsed = abs((60-currentSeconds))
                                if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                                try { mfaProgress.progress = halfMinuteElapsed } catch (_: Exception) {  }
                                if (halfMinuteElapsed == 30) {
                                    otpCode = GoogleAuthenticator(base32secret = secretInput.text.toString()).generate()
                                    runOnUiThread { tokenPreview.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                                }
                            }
                        }, 0, 1000) // 1000 milliseconds = 1 second
                    } catch (timerError: IllegalStateException) { }

                    mfaTokenBox.visibility = View.VISIBLE

                } else {
                    mfaTokenBox.visibility = View.GONE
                }
            }
        })

        backupCodesHelpButton.setOnClickListener {
            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
            alertDialog.setTitle("Backup codes")
            alertDialog.setMessage("Some websites give you a set of backup 2FA codes in case you lose your 2FA-capable device.\n\nJust drag your cursor across them, then copy and paste them into this box. You can also manually type them in.\n\nKeyspace will auto-trim any whitespaces, numbers and commas.")
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Got it") { _, _ -> alertDialog.dismiss() }
            alertDialog.show()
        }

        val backupCodesAlertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()

        backupCodesInput.setUpdateListener(object : BackupCodesEditText.UpdateListener {
            override fun onCut() {}
            override fun onCopy() {}
            override fun onPaste() {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                var pasteData = ""
                if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription!!.hasMimeType(MIMETYPE_TEXT_PLAIN)) {
                    val item = clipboard.primaryClip!!.getItemAt(0)
                    pasteData = item.text.toString()
                }

                if (pasteData.length > 8) {
                    val trimmedBackupCodes = utils.backup2faCodesToList(pasteData)
                    backupCodesAlertDialog.setTitle("Backup codes")
                    backupCodesAlertDialog.setMessage("Confirm before saving:\n\n${trimmedBackupCodes.joinToString("\n\n")}")
                    backupCodesAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Trim and paste") { dialog, _ ->
                        backupCodesInput.setText(trimmedBackupCodes.joinToString(",\n"))
                        dialog.dismiss()
                    }
                    backupCodesAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Paste without trimming") { dialog, _ ->
                        backupCodesInput.setText(pasteData)
                        dialog.dismiss()
                    }
                    backupCodesAlertDialog.show()
                }
            }
        })

        customFieldsView = findViewById(R.id.custom_fields)
        customFieldsView.layoutManager = LinearLayoutManager(this)

        customFieldsData = mutableListOf()
        customFieldsAdapter = CustomFieldsAdapter (customFieldsData)
        customFieldsView.adapter = customFieldsAdapter

        addCustomFieldButton = findViewById(R.id.addCustomFieldButton)
        addCustomFieldButton.isEnabled = true
        addCustomFieldButton.setOnClickListener {
            customFieldsData.add(IOUtilities.CustomField("", "", false))
            customFieldsAdapter.notifyItemInserted(customFieldsData.size)
            customFieldsView.invalidate()
            customFieldsView.refreshDrawableState()
            customFieldsView.scheduleLayoutAnimation()
        }

        siteUrlsHelpButton.setOnClickListener {
            val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
            alertDialog.setTitle("Site URLs")
            alertDialog.setMessage(Html.fromHtml("""
                Keyspace will automatically fill in your credentials on the websites in this list. By default, Keyspace uses an exact URL match.<br><br>
                You can also use regular expressions to do specific matches. For example, 
                    <br><br><tt>[a-zA-Z0-9]*.?google.com</tt><br><br>
                will match <tt>google.com</tt>, <tt>mail.google.com</tt>, and so on.<br><br>
                <a href="https://github.com/ziishaned/learn-regex/blob/master/README.md">Tap here</a> to learn more about regular expressions.
                """.trimIndent()))
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Got it") { _, _ -> alertDialog.dismiss() }
            alertDialog.show()
            val textView: TextView = alertDialog.findViewById(android.R.id.message)!!
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        siteUrlsView = findViewById(R.id.siteUrls)
        siteUrlsView.layoutManager = LinearLayoutManager(this)

        siteUrlsData = mutableListOf()
        siteUrlsAdapter = SiteUrlsAdapter (siteUrlsData)
        siteUrlsView.adapter = siteUrlsAdapter

        addSiteUrlButton = findViewById(R.id.addSiteUrlButton)
        addSiteUrlButton.isEnabled = true
        addSiteUrlButton.setOnClickListener {
            siteUrlsData.add("")
            siteUrlsAdapter.notifyItemInserted(siteUrlsData.size)
            siteUrlsView.invalidate()
            siteUrlsView.refreshDrawableState()
            siteUrlsView.scheduleLayoutAnimation()
        }

        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is TextInputEditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    val view = when (v) {
                        is TextInputEditText -> v
                        else -> null
                    }
                    view?.setTextIsSelectable(false)
                    view?.refreshDrawableState()
                    view?.setTextIsSelectable(true)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadLogin(login: IOUtilities.Login): Boolean {

        favorite = if (login.favorite) {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_24)); true
        } else {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_border_24)); false
        }

        tagId = login.tagId
        tagPicker = AddTag (tagId, applicationContext, this@AddLogin, keyring)

        siteNameInput.setText(login.name)

        emailInput.setText(login.loginData?.email)

        if (!login.loginData!!.username.isNullOrBlank()) {
            userNameInput.setText(login.loginData.username)
            emailAsUsername.isChecked = false
        } else {
            userNameInputLayout.visibility = View.GONE
            emailAsUsername.isChecked = true
        }

        if (!login.loginData.password.isNullOrEmpty()) {
            val loginPasswordLength = login.loginData.password.length
            if (loginPasswordLength >= 128) {
                passwordLength.value = 128F
            } else {
                passwordLength.value = loginPasswordLength.toFloat()
            }

            length.text = "$loginPasswordLength characters"
            passwordInput.setText(login.loginData.password)
        } else passwordInput.text?.clear()

        secretInput.setText(login.loginData.totp!!.secret)

        backupCodesInput.setText(login.loginData.totp!!.backupCodes?.joinToString(",\n"))

        passwordHistoryData = mutableListOf()
        if (login.loginData.passwordHistory != null) {
            passwordHistoryData = login.loginData.passwordHistory
            passwordHistoryButton.visibility = View.VISIBLE

            passwordHistoryButton.setOnClickListener {
                val inflater = layoutInflater
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle("Password history")
                builder.setCancelable(true)

                val passwordHistoryBox: View = inflater.inflate(R.layout.password_history, null)
                builder.setView(passwordHistoryBox)

                passwordHistoryBox.startAnimation(loadAnimation(applicationContext, R.anim.from_top))

                passwordHistoryView = passwordHistoryBox.findViewById(R.id.passwordHistoryRecycler) as RecyclerView
                passwordHistoryView.layoutManager = LinearLayoutManager(this)

                passwordHistoryAdapter = PasswordHistoryAdapter (passwordHistoryData)
                passwordHistoryView.adapter = passwordHistoryAdapter
                passwordHistoryAdapter.notifyItemInserted(passwordHistoryData.size)
                passwordHistoryView.invalidate()
                passwordHistoryView.refreshDrawableState()
                passwordHistoryView.scheduleLayoutAnimation()

                val dialog = builder.create()
                dialog.show()
            }

        }

        if (login.loginData.siteUrls != null) {
            siteUrlsData = login.loginData.siteUrls
            siteUrlsAdapter = SiteUrlsAdapter (siteUrlsData)
            siteUrlsView.adapter = siteUrlsAdapter
            siteUrlsAdapter.notifyItemInserted(siteUrlsData.size)
            siteUrlsView.invalidate()
            siteUrlsView.refreshDrawableState()
            siteUrlsView.scheduleLayoutAnimation()
        }

        notesInput.setText(login.notes)

        if (login.customFields != null) {
            customFieldsData = login.customFields
            customFieldsAdapter = CustomFieldsAdapter (customFieldsData)
            customFieldsView.adapter = customFieldsAdapter
            customFieldsAdapter.notifyItemInserted(customFieldsData.size)
            customFieldsView.invalidate()
            customFieldsView.refreshDrawableState()
            customFieldsView.scheduleLayoutAnimation()
        }

        Handler().postDelayed({ runOnUiThread {
            iconFileName = login.iconFile
            if (iconFileName != null) siteNameInputIcon.setImageDrawable(misc.getSiteIcon(iconFileName!!, siteNameInput.currentTextColor))
            else siteNameInputIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_website_24))
        } }, 100)

        return true
    }

    private fun mfaDialog () {
        if (secretInput.text.toString().trim().isNotEmpty()) {
            var otpCode = GoogleAuthenticator(base32secret = secretInput.text.toString()).generate()

            val builder = MaterialAlertDialogBuilder(this)
            builder.setCancelable(true)
            val accountInfoBox: View = layoutInflater.inflate(R.layout.mini_2fa_dialog, null)
            builder.setView(accountInfoBox)
            accountInfoBox.startAnimation(loadAnimation(applicationContext, R.anim.from_bottom))

            val qrCode = accountInfoBox.findViewById<ImageView>(R.id.qrCode)

            val mfaLabel = accountInfoBox.findViewById<TextView>(R.id.mfaLabel)
            if (siteNameInput.text.toString().trim().isNotEmpty()) mfaLabel.text = siteNameInput.text
            else  mfaLabel.visibility = View.GONE

            val mfaCode = accountInfoBox.findViewById<TextView>(R.id.mfaCode)
            mfaCode.text = otpCode.replace("...".toRegex(), "$0 ")

            val accountName = accountInfoBox.findViewById<TextView>(R.id.accountName)
            if (emailInput.text.toString().trim().isNotEmpty()) accountName.text = emailInput.text.toString()
            else accountName.visibility = View.GONE

            val secret = accountInfoBox.findViewById<TextView>(R.id.secret)
            val mfaProgress = accountInfoBox.findViewById<ProgressBar>(R.id.mfaProgress)
            secret.text = secretInput.text.toString().trim()

            try {
                otpCode = GoogleAuthenticator(base32secret = secretInput.text.toString()).generate()
                runOnUiThread { mfaCode.text = otpCode!!.replace("...".toRegex(), "$0 ") }
                timer.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        val currentSeconds = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                        var halfMinuteElapsed = abs((60-currentSeconds))
                        if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                        try { mfaProgress.progress = halfMinuteElapsed } catch (_: Exception) {  }
                        if (halfMinuteElapsed == 30) {
                            otpCode = GoogleAuthenticator(base32secret = secretInput.text.toString()).generate()
                            runOnUiThread { mfaCode.text = otpCode.replace("...".toRegex(), "$0 ") }
                        }
                    }
                }, 0, 1000) // 1000 milliseconds = 1 second
            } catch (timerError: IllegalStateException) { }

            val mfaMode = accountInfoBox.findViewById<TextView>(R.id.mfaMode)
            mfaMode.visibility = View.GONE

            val mfaAccountName: String? = if (siteNameInput.text.toString().trim().isNotEmpty()) {
                siteNameInput.text.toString()
            } else if (emailInput.text.toString().trim().isNotEmpty()) {
                emailInput.text.toString()
            } else  {
                "Unknown account"
            }

            val qrCodeData = MiscUtilities.MfaCode(
                type = "otp",
                mode = "totp",
                issuer = siteNameInput.text.toString(),
                account = mfaAccountName,
                secret = secretInput.text.toString(),
                algorithm = null,
                digits = null,
                period = null,
                lock = null,
                counter = null,
                label = null
            )

            qrCode.setImageBitmap(utils.generateQrCode(utils.encodeOTPAuthURL(qrCodeData)!!))

            val mini2faDialog = builder.create()
            mini2faDialog.show()
        }
    }

    private fun saveItem () {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = io.getLogin(itemId!!, vault)?.dateCreated!!
            vault.login?.remove(io.getLogin(itemId!!, vault))

            if (login.loginData != null) {
                if (!login.loginData!!.password.isNullOrEmpty()) {
                    if (passwordInput.text.toString() != login.loginData?.password) {
                        passwordHistoryData.add (
                            IOUtilities.Password(
                                password = passwordInput.text.toString(),
                                created = Instant.now().epochSecond
                            )
                        )
                    }
                }
            }

        } else {
            passwordHistoryData = mutableListOf()
            passwordHistoryData.add (
                IOUtilities.Password(
                    password = passwordInput.text.toString(),
                    created = Instant.now().epochSecond
                )
            )
        }

        val data = IOUtilities.Login(
            id = itemId ?: UUID.randomUUID().toString(),
            organizationId = null,
            type = io.TYPE_LOGIN,
            name = siteNameInput.text.toString(),
            notes = notesInput.text.toString(),
            favorite = favorite,
            deleted = deleted,
            tagId = tagPicker.getSelectedTagId() ?: tagId,
            loginData = IOUtilities.LoginData(
                username = userNameInput.text.toString(),
                password = passwordInput.text.toString(),
                passwordHistory = if (passwordHistoryData.size > 0) passwordHistoryData else null,
                email = emailInput.text.toString(),
                totp = IOUtilities.Totp(
                    secret = secretInput.text.toString(),
                    backupCodes = backupCodesInput.text?.toString()?.replace("\n", "")?.split(",")?.toMutableList()
                ),
                siteUrls = if (siteUrlsData.size > 0) siteUrlsData else null
            ),
            dateCreated = dateCreated,
            dateModified = Instant.now().epochSecond,
            frequencyAccessed = 0,
            customFields = customFieldsData,
            iconFile = iconFileName
        )

        val encryptedLogin = io.encryptLogin(data)

        vault.login?.add (encryptedLogin)
        io.writeVault(vault)

        if (itemId != null) network.writeQueueTask (encryptedLogin, mode = network.MODE_PUT)
        else network.writeQueueTask (encryptedLogin, mode = network.MODE_POST)

        crypto.secureStartActivity (
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
            keyring = keyring,
            itemId = null
        )

    }

    override fun onBackPressed () {
        try { qrCodeDialog.dismiss() } catch (qrCodeBoxUninitialized: UninitializedPropertyAccessException) {  }

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

    override fun onStop() {
        super.onStop()
        timer.cancel()
        timer.purge()

        // force wipe keyring
        keyring.XCHACHA_POLY1305_KEY?.fill(0)
        keyring.ED25519_PUBLIC_KEY?.fill(0)
        keyring.ED25519_PRIVATE_KEY?.fill(0)

        // force gc to clear keyring
        System.gc()
        finish()
        finishAffinity()
        try { qrCodeDialog.dismiss() } catch (qrCodeBoxUninitialized: UninitializedPropertyAccessException) {  }
    }

    override fun onPause() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()

            // force wipe keyring
            keyring.XCHACHA_POLY1305_KEY?.fill(0)
            keyring.ED25519_PUBLIC_KEY?.fill(0)
            keyring.ED25519_PRIVATE_KEY?.fill(0)

            // force gc to clear keyring
            System.gc()

            timer.cancel()
            timer.purge()
        }
        super.onPause()
    }

    override fun onUserLeaveHint() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()

            // force wipe keyring
            keyring.XCHACHA_POLY1305_KEY?.fill(0)
            keyring.ED25519_PUBLIC_KEY?.fill(0)
            keyring.ED25519_PRIVATE_KEY?.fill(0)

            // force gc to clear keyring
            System.gc()

            timer.cancel()
            timer.purge()
        }
        super.onUserLeaveHint()
    }

    inner class CustomFieldsAdapter (private val customFields: MutableList<IOUtilities.CustomField>) : RecyclerView.Adapter<CustomFieldsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val customFieldsView: View = LayoutInflater.from(parent.context).inflate(R.layout.custom_field, parent, false)
            customFieldsView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ViewHolder(customFieldsView)
        }

        override fun onBindViewHolder(customFieldView: ViewHolder, position: Int) {
            val customField = customFieldsData[customFieldView.adapterPosition]

            var hidden = false

            customFieldView.fieldName.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            customFieldView.fieldValue.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING

            customFieldView.fieldName.setText (customField.name)
            customFieldView.fieldValue.setText (customField.value)

            if (customField.hidden) {
                customFieldView.fieldValue.transformationMethod = PasswordTransformationMethod()
                customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
                hidden = true
            } else {
                customFieldView.fieldValue.transformationMethod = null
                customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_off_24))
                hidden = false
            }

            customFieldView.fieldName.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(data: CharSequence, start: Int, before: Int, count: Int) {
                    addCustomFieldButton.isEnabled = data.isNotEmpty()
                    customFieldsData[customFieldView.adapterPosition].name = data.toString()
                }
            })

            customFieldView.fieldValue.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(data: CharSequence, start: Int, before: Int, count: Int) {
                    addCustomFieldButton.isEnabled = data.isNotEmpty()
                    customFieldsData[customFieldView.adapterPosition].value = data.toString()
                }
            })

            customFieldView.deleteIcon.setOnClickListener { view ->
                addCustomFieldButton.isEnabled = true
                Toast.makeText(applicationContext, "Deleted \"${customFieldView.fieldName.text}\"", Toast.LENGTH_SHORT).show()
                customFieldView.fieldName.clearFocus()
                customFieldView.fieldValue.clearFocus()
                try {
                    customFieldsData.remove(customFieldsData[customFieldView.adapterPosition])
                } catch (noItemsLeft: IndexOutOfBoundsException) { }
                customFieldsAdapter.notifyItemRemoved(position)
                customFieldsView.invalidate()
                customFieldsView.refreshDrawableState()
            }

            customFieldView.hideIcon.setOnClickListener { view ->
                if (!hidden) {
                    customFieldView.fieldValue.transformationMethod = PasswordTransformationMethod()
                    customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
                    hidden = true
                } else {
                    customFieldView.fieldValue.transformationMethod = null
                    customFieldView.hideIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_off_24))
                    hidden = false
                }
                val tempName = customFieldsData[customFieldView.adapterPosition].name
                val tempValue = customFieldsData[customFieldView.adapterPosition].value
                customFieldsData.remove(customFieldsData[customFieldView.adapterPosition])
                customFieldsData.add(IOUtilities.CustomField(tempName, tempValue, hidden))
            }

        }

        override fun getItemCount(): Int {
            return customFields.size
        }

        inner class ViewHolder (itemLayoutView: View) : RecyclerView.ViewHolder(itemLayoutView) {
            var fieldName: EditText = itemLayoutView.findViewById<View>(R.id.field_name) as EditText
            var fieldValue: EditText = itemLayoutView.findViewById<View>(R.id.field_value) as EditText
            var deleteIcon: ImageView = itemLayoutView.findViewById<View>(R.id.deleteCustomFieldButton) as ImageView
            var hideIcon: ImageView = itemLayoutView.findViewById<View>(R.id.hideCustomFieldButton) as ImageView
        }
    }

    inner class SiteUrlsAdapter (private val siteUrls: MutableList<String>) : RecyclerView.Adapter<SiteUrlsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val siteUrlsView: View = LayoutInflater.from(parent.context).inflate(R.layout.site_url, parent, false)
            siteUrlsView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ViewHolder(siteUrlsView)
        }

        override fun onBindViewHolder(siteUrlView: ViewHolder, position: Int) {
            val siteUrl = siteUrlsData[siteUrlView.adapterPosition]

            siteUrlView.siteUrl.imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
            siteUrlView.siteUrl.setText (siteUrl)

            siteUrlView.siteUrl.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) { }
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(data: CharSequence, start: Int, before: Int, count: Int) {
                    addSiteUrlButton.isEnabled = data.isNotEmpty()
                    siteUrlsData[siteUrlView.adapterPosition] = data.toString()
                }
            })

            siteUrlView.deleteIcon.setOnClickListener { view ->
                addSiteUrlButton.isEnabled = true
                siteUrlView.siteUrl.clearFocus()
                Toast.makeText(applicationContext, "Deleted \"${siteUrlView.siteUrl.text}\"", Toast.LENGTH_SHORT).show()
                try {
                    siteUrlsData.remove(siteUrlsData[siteUrlView.adapterPosition])
                } catch (noItemsLeft: IndexOutOfBoundsException) { }
                siteUrlsAdapter.notifyItemRemoved(siteUrlView.adapterPosition)
                siteUrlsView.invalidate()
                siteUrlsView.refreshDrawableState()
            }

        }

        override fun getItemCount(): Int {
            return siteUrls.size
        }

        inner class ViewHolder (itemLayoutView: View) : RecyclerView.ViewHolder(itemLayoutView) {
            var siteUrl: EditText = itemLayoutView.findViewById<View>(R.id.siteUrlInput) as EditText
            var deleteIcon: ImageView = itemLayoutView.findViewById<View>(R.id.deleteSiteUrlButton) as ImageView
        }
    }

    inner class PasswordHistoryAdapter (private val oldPasswords: MutableList<IOUtilities.Password>) : RecyclerView.Adapter<PasswordHistoryAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val passwordHistoryView: View = LayoutInflater.from(parent.context).inflate(R.layout.password_history_card, parent, false)
            passwordHistoryView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ViewHolder(passwordHistoryView)
        }

        override fun onBindViewHolder(passwordHistoryView: ViewHolder, position: Int) {
            val passwordHistory = oldPasswords[passwordHistoryView.adapterPosition]

            val calendar = Calendar.getInstance(Locale.getDefault())
            calendar.timeInMillis = passwordHistory.created * 1000L
            val date = DateFormat.format("MMM dd, yyyy",calendar).toString()
            val time = DateFormat.format("HH:mm",calendar).toString()

            passwordHistoryView.oldPassword.text = passwordHistory.password
            passwordHistoryView.createdDate.text = date
            passwordHistoryView.createdTime.text = time

            passwordHistoryView.copyOldPasswordButton.setOnClickListener { view ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Keyspace", passwordHistoryView.oldPassword.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Copied!", Toast.LENGTH_SHORT).show()
                passwordCopied = true
            }

        }

        override fun getItemCount(): Int {
            return oldPasswords.size
        }

        inner class ViewHolder (itemLayoutView: View) : RecyclerView.ViewHolder(itemLayoutView) {
            var oldPassword: TextView = itemLayoutView.findViewById<View>(R.id.oldPassword) as TextView
            var copyOldPasswordButton: Button = itemLayoutView.findViewById<View>(R.id.copyOldPasswordButton) as Button
            var createdDate: TextView = itemLayoutView.findViewById<View>(R.id.createdDate) as TextView
            var createdTime: TextView = itemLayoutView.findViewById<View>(R.id.createdTime) as TextView
        }
    }

    class BackupCodesEditText : TextInputEditText {
        var listener: UpdateListener? = null

        constructor(context: Context?) : super(context!!)
        constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
        constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr)

        fun setUpdateListener(listener: UpdateListener?) {
            this.listener = listener
        }

        override fun onTextContextMenuItem(id: Int): Boolean {
            val consumed = super.onTextContextMenuItem(id)
            when (id) {
                android.R.id.cut -> if (listener != null) listener!!.onCut()
                android.R.id.copy -> if (listener != null) listener!!.onCopy()
                android.R.id.paste -> if (listener != null) listener!!.onPaste()
            }
            return consumed
        }

        interface UpdateListener {
            fun onCut()
            fun onCopy()
            fun onPaste()
        }
    }

    private fun iconFilePicker () {

        val builder = MaterialAlertDialogBuilder(this@AddLogin)
        builder.setCancelable(true)
        val iconsBox: View = layoutInflater.inflate(R.layout.icon_picker_dialog, null)
        builder.setView(iconsBox)
        iconsBox.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.from_bottom))

        val dialog = builder.create()
        dialog.show()

        val iconFileNames = misc.getSiteIconFilenames()
        class GridAdapter(var context: Context, filenames: ArrayList<String>) : BaseAdapter() {
            var listFiles: ArrayList<String>
            init { listFiles = filenames }
            override fun getCount(): Int { return listFiles.size }
            override fun getItem(position: Int): Any { return listFiles[position] }
            override fun getItemId(position: Int): Long { return position.toLong() }
            @SuppressLint("ViewHolder")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.site_icon, null)

                val icon = view.findViewById<ImageView>(R.id.icon)
                icon.setImageDrawable(misc.getSiteIcon(listFiles[position], siteNameInput.currentTextColor))

                val iconName = view.findViewById<TextView>(R.id.iconName)
                iconName.text = listFiles[position].replace("_", "")

                icon.setOnClickListener {
                    iconFileName = listFiles[position]
                    siteNameInputIcon.setImageDrawable(misc.getSiteIcon(listFiles[position], siteNameInput.currentTextColor))
                    dialog.dismiss()
                }

                return view!!
            }
        }

        val icons = dialog.findViewById<GridView>(R.id.icons) as GridView
        icons.adapter = GridAdapter(this@AddLogin, iconFileNames)

        val searchResults = arrayListOf<String>()
        val searchBar = iconsBox.findViewById<EditText>(R.id.searchBar)
        searchBar.doOnTextChanged { searchTerm, start, count, after ->
            searchResults.clear()
            for (filename in iconFileNames) {
                if (filename.contains(searchTerm.toString().lowercase().replace(" ", ""))) {
                    searchResults.add(filename)
                    icons.adapter = GridAdapter(this@AddLogin, searchResults)
                }
            }
        }

        val resetButton = iconsBox.findViewById<MaterialButton>(R.id.resetButton)
        resetButton.setOnClickListener {
            iconFileName = "website"
            siteNameInputIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_website_24))
            dialog.dismiss()
        }

    }

}



