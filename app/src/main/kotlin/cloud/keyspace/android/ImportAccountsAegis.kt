package cloud.keyspace.android

import android.content.Intent
import android.icu.text.IDNA
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.anggrayudi.storage.SimpleStorageHelper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.button.MaterialButton
import com.goterl.lazysodium.utils.HexMessageEncoder
import com.keyspace.keyspacemobile.NetworkUtilities
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

class ImportAccountsAegis : AppCompatActivity() {

    lateinit var crypto: CryptoUtilities
    lateinit var keyring: CryptoUtilities.Keyring

    private lateinit var storageHelper: SimpleStorageHelper
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities
    lateinit var misc: MiscUtilities
    lateinit var vault: IOUtilities.Vault

    lateinit var backButton: ImageView
    lateinit var progressBar: ProgressBar
    lateinit var label: TextView
    lateinit var description: TextView
    lateinit var filePickerButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.import_aegis)

        crypto = CryptoUtilities(applicationContext, this)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_import_aegis),
            intent = intent
        )
        keyring = intentData.first

        io = IOUtilities(applicationContext, this, keyring)
        network = NetworkUtilities(applicationContext, this, keyring)
        misc = MiscUtilities(applicationContext)
        vault = io.getVault()

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.GONE

        label = findViewById(R.id.label)
        description = findViewById(R.id.description)

        filePickerButton = findViewById(R.id.filePickerButton)

        val JSON_MIME_TYPE = "application/json"
        val FILES_REQUEST_CODE = 1234

        storageHelper = SimpleStorageHelper(this, FILES_REQUEST_CODE, savedInstanceState)
        filePickerButton.setOnClickListener {
            storageHelper.openFilePicker (
                allowMultiple = false,
                filterMimeTypes = arrayOf (JSON_MIME_TYPE)
            )
        }

        storageHelper.onFileSelected = { _, files ->

            setImporting(files[0].name.toString())

            lateinit var accounts: List<Entry>
            val parseThread = Thread {
                val file = contentResolver.openInputStream(files[0].uri)
                val fileData = String(file?.readBytes()!!)
                file.close()

                accounts = parseAegisJsonFile(fileData)
            }
            parseThread.start()

            Handler().postDelayed({
                parseThread.join()
                // Save to KeyspaceFS
                if (accounts.isNotEmpty()) {
                    setDone(accounts)
                    val vault: IOUtilities.Vault = io.getVault()
                    for (account in accounts) {
                        val siteName = if (account.issuer.isBlank()) account.name.substringBeforeLast(":")
                        else {
                            val accountLabel = account.name.substringAfterLast(":")
                            if (accountLabel.isNotEmpty() && accountLabel != account.issuer) "${account.issuer} (${accountLabel})"
                            else account.issuer
                        }
                        val data = IOUtilities.Login (
                            id = UUID.randomUUID().toString(),
                            organizationId = null,
                            type = io.TYPE_LOGIN,
                            name = siteName,
                            notes = account.note,
                            favorite = account.favorite,
                            tagId = null,
                            loginData = IOUtilities.LoginData(
                                username = if (!account.name.substringAfterLast(":").contains("@")) account.name.substringAfterLast(":") else null,
                                password = null,
                                passwordHistory = null,
                                email = if (account.name.substringAfterLast(":").contains("@") && account.name.substringAfterLast(":").contains(".")) account.name.substringAfterLast(":") else null,
                                totp = IOUtilities.Totp(
                                    secret = account.info.secret,
                                    backupCodes = null
                                ),
                                siteUrls = null
                            ),
                            dateCreated = Instant.now().epochSecond,
                            dateModified = Instant.now().epochSecond,
                            frequencyAccessed = 0,
                            customFields = null,
                            iconFile = if (misc.getSiteIcon(siteName, description.currentTextColor) != null) siteName else null
                        )

                        val encryptedLogin = io.encryptLogin(data)

                        vault.login?.add (encryptedLogin)
                        network.writeQueueTask (encryptedLogin, mode = network.MODE_POST)
                    }

                    io.writeVault(vault)

                } else setNoData()
            }, 1000)

        }

    }

    private fun setImporting(accounts: String) {
        progressBar.visibility = View.VISIBLE
        label.text = "Importing"
        description.text = "Importing accounts from $accounts"
        filePickerButton.visibility = View.GONE
    }

    private fun setDone(accounts: List<Entry>) {
        label.text = "Import successful"
        progressBar.visibility = View.GONE
        description.text = "Imported ${accounts.size} account${if (accounts.size > 1) "s" else ""} successfully! Make sure all your accounts are in the list below"
        description.append ("\n")
        for (account in accounts) {
            if (account.issuer.isBlank()) description.append("\n• ${account.name.substringBeforeLast(":")}")
            else {
                val accountLabel = account.name.substringAfterLast(":")
                if (accountLabel.isNotEmpty() && accountLabel != account.issuer) description.append("\n• ${account.issuer} (${accountLabel})")
                else description.append("\n• ${account.issuer}")
            }
        }
        filePickerButton.visibility = View.VISIBLE
        filePickerButton.text = "Finish"
        filePickerButton.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = ImportAccountsAegis(),
                nextActivityClassNameAsString = getString(R.string.title_activity_settings),
                keyring = keyring,
                itemId = null
            )
            finish()
        }
    }

    private fun setNoData() {
        progressBar.visibility = View.VISIBLE
        label.text = "No data found"
        description.text = "This file has no accounts to import from."
    }

    data class Info (
        val secret: String,
        val algo: String,
        val digits: String,
        val period: String,
    )

    data class Entry (
        val type: String,
        val name: String,
        val issuer: String,
        val note: String,
        val favorite: Boolean,
        val info: Info,
    )

    private fun parseAegisJsonFile (fileData: String): List<Entry> {
        val mapper = jacksonObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val vault: Map<*, *> = mapper.readValue (fileData, MutableMap::class.java)

        val db: Map<*, *> = vault["db"] as Map<*, *>

        val entries = mutableListOf<Entry>()
        for (item in db["entries"] as ArrayList<*>) {
            val entry = mapper.readValue (
                mapper.writer().withDefaultPrettyPrinter().writeValueAsBytes(item),
                Entry::class.java
            )
            entries.add(entry)
        }

        return entries

    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        storageHelper.storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        storageHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

}

