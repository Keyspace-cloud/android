package cloud.keyspace.android

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.yahiaangelo.markdownedittext.MarkdownEditText
import java.util.*


class Settings : AppCompatActivity() {

    lateinit var crypto: CryptoUtilities
    lateinit var keyring: CryptoUtilities.Keyring

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        crypto = CryptoUtilities(applicationContext, this)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_settings),
            intent = intent
        )
        keyring = intentData.first

        val configData = getSharedPreferences (applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        val keyspaceAccountPicture: ImageView = findViewById(R.id.keyspaceAccountPicture)
        keyspaceAccountPicture.setImageDrawable(MiscUtilities(applicationContext).generateProfilePicture(configData.getString("userEmail", null)!!))

        /*val autofill: LinearLayout = findViewById(R.id.autofillSettings)

        autofill.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, PermissionCodes().AUTOFILL_CODE)
        }

        val accessibility: LinearLayout = findViewById(R.id.accessibilitySettings)

        accessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        val fastAutofill: MaterialSwitch = findViewById(R.id.fastAutofillButton)
        fastAutofill.isChecked = configData.getBoolean("fastAutofill", false)
        fastAutofill.setOnCheckedChangeListener { _, isChecked ->
            configData.edit().remove("fastAutofill").commit()
            if (isChecked) configData.edit().putBoolean("fastAutofill", true).commit() else configData.edit().putBoolean("fastAutofill", false).commit()
        }*/

        val strongBox: LinearLayout = findViewById(R.id.strongBox)
        val strongBoxText: TextView = findViewById(R.id.strongBoxText)
        val strongBoxIcon: ImageView = findViewById(R.id.strongBoxTypeIcon)

        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if strongbox / hardware keystore exists
            strongBoxText.text = "Keys are encrypted using tamper-resistant Strongbox hardware."
            strongBoxIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_chip_24))
        } else {
            if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE)) {
                strongBoxText.text = "Keys are encrypted using Hardware Abstraction Layer (HAL) Keystore."
                strongBoxIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_code_24))
            } else {
                strongBoxText.text = "Keys are encrypted using container-based Keystore."
                strongBoxIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_insert_drive_file_24))
            }
        }

        val notesGridButton: MaterialSwitch = findViewById(R.id.notesGridButton)
        notesGridButton.isChecked = configData.getBoolean("notesGrid", true)
        notesGridButton.setOnCheckedChangeListener { _, isChecked ->
            configData.edit().remove("notesGrid").commit()
            if (isChecked) configData.edit().putBoolean("notesGrid", true).commit() else configData.edit().putBoolean("notesGrid", false).commit()
        }

        val notesPreviewButton: MaterialSwitch = findViewById(R.id.notesPreviewButton)
        notesPreviewButton.isChecked = configData.getBoolean("notesPreview", false)
        notesPreviewButton.setOnCheckedChangeListener { _, isChecked ->
            configData.edit().remove("notesPreview").commit()
            if (isChecked) configData.edit().putBoolean("notesPreview", true).commit() else configData.edit().putBoolean("notesPreview", false).commit()
        }

        val importButton: LinearLayout = findViewById(R.id.importButton)
        importButton.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = ImportAccounts(),
                nextActivityClassNameAsString = getString(R.string.title_activity_import_accounts),
                keyring = keyring,
                itemId = null
            )
        }

        val lockAppButton: MaterialSwitch = findViewById(R.id.lockAppButton)
        lockAppButton.isChecked = configData.getBoolean("lockApp", false)
        lockAppButton.setOnCheckedChangeListener { _, isChecked ->
            configData.edit().remove("lockApp").commit()
            if (isChecked) configData.edit().putBoolean("lockApp", true).commit() else configData.edit().putBoolean("lockApp", false).commit()
        }

        val intervalGroup: RadioGroup = findViewById(R.id.intervalButtonGroup)
        val instantButton: MaterialRadioButton = findViewById(R.id.instantButton)
        val manualButton: MaterialRadioButton = findViewById(R.id.manualButton)
        val syncTimeoutEditText: TextInputEditText = findViewById(R.id.syncTimeoutEditText)

        intervalGroup.setOnCheckedChangeListener { _, i ->
            when (i) {
                R.id.instantButton -> {
                    configData.edit().remove("refreshInterval").commit()
                    configData.edit().putLong ("refreshInterval", 0L).commit()
                }
                R.id.manualButton -> {
                    configData.edit().remove("refreshInterval").commit()
                    configData.edit().putLong ("refreshInterval", -1L).commit()
                }
            }
        }

        if (configData.getLong ("refreshInterval", 0L) == 0L) {
            syncTimeoutEditText.text?.clear()
            syncTimeoutEditText.text = null
            instantButton.isChecked = true
            manualButton.isChecked = false
        } else if (configData.getLong ("refreshInterval", 0L) == -1L) {
            syncTimeoutEditText.text?.clear()
            syncTimeoutEditText.text = null
            instantButton.isChecked = false
            manualButton.isChecked = true
        } else {
            syncTimeoutEditText.setText(configData.getLong ("refreshInterval", 0L).toString())
        }

        syncTimeoutEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (syncTimeoutEditText.text.toString().isEmpty()) {
                    instantButton.isChecked = true
                    configData.edit().remove("refreshInterval").commit()
                    configData.edit().putLong ("refreshInterval", 0L).commit()
                } else {
                    if (syncTimeoutEditText.text.toString().toLong() <= 5000L) {
                        syncTimeoutEditText.error = "Please set a value more than 5000 ms"
                    } else {
                        instantButton.isChecked = false
                        manualButton.isChecked = false
                        configData.edit().remove("refreshInterval").commit()
                        configData.edit().putLong ("refreshInterval", syncTimeoutEditText.text.toString().toLong()).commit()
                    }
                }
            }
        })

        val openSourceLicensesLayout = findViewById<LinearLayout>(R.id.openSourceLicensesLayout)
        openSourceLicensesLayout.setOnClickListener {
            licenseDialog()
        }

        val buildLabel = findViewById<TextView>(R.id.buildLabel)
        val keyspacerLayout = findViewById<LinearLayout>(R.id.keyspacerLayout)
        buildLabel.text = "v" + applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName.toString()
        val buildVersionButton: LinearLayout = findViewById(R.id.buildVersionLayout)
        val clicksNeeded = 12
        val maxInterval = 1000L
        var count = 0
        val continuousClicksHandler = ContinuousClicksHandler (clicksNeeded, maxInterval)
        continuousClicksHandler.registerCallback (object : ContinuousClicksHandler.ContinuousClicksCallback {
            override fun onContinuousClicksSuccessful() { }
            override fun onContinuousClicksFailed() { }
        })
        var toast: Toast? = null
        buildVersionButton.setOnClickListener {
            count = continuousClicksHandler.click()
            toast?.cancel()
            when (clicksNeeded-count) {
                clicksNeeded -> {
                    toast = Toast.makeText (applicationContext, "You are now a Keyspacer!", Toast.LENGTH_SHORT)
                    toast?.show()
                    configData.edit().remove("keyspacerOptions").commit()
                    configData.edit().putBoolean ("keyspacerOptions", true).commit()
                    keyspacerLayout.visibility = View.VISIBLE
                    buildVersionButton.setOnClickListener {
                        toast = Toast.makeText (applicationContext, "No need, you are already a Keyspacer!", Toast.LENGTH_SHORT)
                        toast?.show()
                    }
                }
                1 -> {
                    toast = Toast.makeText (applicationContext, "You are just ${clicksNeeded-count} step away from being a Keyspacer.", Toast.LENGTH_SHORT)
                    toast?.show()
                }
                else -> {
                    if (clicksNeeded-count > 1) {
                        toast = Toast.makeText (applicationContext, "You are now ${clicksNeeded-count} steps away from being a Keyspacer.", Toast.LENGTH_SHORT)
                        toast?.show()
                    }
                }
            }
        }
        if (configData.getBoolean("keyspacerOptions", false)) {
            buildVersionButton.setOnClickListener {
                toast = Toast.makeText (applicationContext, "No need, you are already a Keyspacer!", Toast.LENGTH_SHORT)
                toast?.show()
            }
            keyspacerLayout.visibility = View.VISIBLE
        } else keyspacerLayout.visibility = View.GONE
        keyspacerLayout.setOnClickListener {
            val intent = Intent(this, DeveloperOptions::class.java)
            startActivity(intent)
        }

    }

    private fun licenseDialog () {
        val builder = MaterialAlertDialogBuilder(this@Settings)
        val licenseBox: View = layoutInflater.inflate(R.layout.open_source_licenses, null)
        builder.setView(licenseBox)

        val dialog = builder.create()
        dialog.show()

        licenseBox.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.from_bottom))
        licenseBox.findViewById<MaterialButton>(R.id.closeButton).setOnClickListener { dialog.dismiss() }
        val license = licenseBox.findViewById<MarkdownEditText>(R.id.licenseText)
        license.setText (getString(R.string.license))
        license.renderMD()
    }

    class ContinuousClicksHandler (private val clicksCount: Int, maxInterval: Long) {
        class TimerWatchDog (private val timeout: Long) {
            private var timer: Timer? = null
            fun refresh(job: () -> Unit) {
                timer?.cancel()
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() = job.invoke()
                }, timeout)
            }
            fun cancel() = timer?.cancel()
        }
        private var callback: ContinuousClicksCallback? = null
        private val timerWatchDog = TimerWatchDog (maxInterval)
        private var currentClicks = 0
        fun click(): Int {
            if (++currentClicks == clicksCount) {
                timerWatchDog.cancel()
                currentClicks = 0
                callback?.onContinuousClicksSuccessful()
            } else {
                timerWatchDog.refresh {
                    currentClicks = 0
                    callback?.onContinuousClicksFailed()
                }
            }
            return currentClicks
        }
        fun registerCallback (callback: ContinuousClicksCallback) {
            this.callback = callback
        }
        interface ContinuousClicksCallback {
            fun onContinuousClicksSuccessful()
            fun onContinuousClicksFailed()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Toast.makeText(applicationContext, "Restarting app to take effect", Toast.LENGTH_LONG).show()
        val intent = Intent(this, StartHere::class.java)
        this.startActivity(intent)
        finishAffinity()
    }
}