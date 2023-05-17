package cloud.keyspace.android

import android.content.Intent
import android.net.Uri
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
import com.keyspace.keyspacemobile.NetworkUtilities
import java.util.*


class ImportAccountsBitwarden : AppCompatActivity() {

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
        setContentView(R.layout.import_bitwarden)

        crypto = CryptoUtilities(applicationContext, this)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_import_bitwarden),
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

            var vault: ExtractedItems? = null
            val parseThread = Thread {
                val file = contentResolver.openInputStream(files[0].uri)
                val fileData = String(file?.readBytes()!!)
                file.close()

                try {
                    vault = parseBitwardenJsonFile(fileData)
                } catch (_: Exception) { }

            }
            parseThread.start()

            Handler().postDelayed({
                parseThread.join()
                // Save to KeyspaceFS
                try {
                    if (vault == null) setNoData() else {
                        Log.d("BWDATA", vault!!.logins[4].name.toString())
                    }
                } catch (_: Exception) {
                    setNoData()
                }
            }, 1000)

        }

    }

    private fun setImporting(accounts: String) {
        progressBar.visibility = View.VISIBLE
        label.text = "Importing"
        description.text = "Importing accounts from $accounts"
        filePickerButton.visibility = View.GONE
    }

    private fun setDone(accounts: List<String>) {
        label.text = "Import successful"
        progressBar.visibility = View.GONE
        description.text = "Imported ${accounts.size} account${if (accounts.size > 1) "s" else ""} successfully! Make sure all your accounts are in the list below"
        description.append ("\n")

        filePickerButton.visibility = View.VISIBLE
        filePickerButton.text = "Finish"
        filePickerButton.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = ImportAccountsBitwarden(),
                nextActivityClassNameAsString = getString(R.string.title_activity_settings),
                keyring = keyring,
                itemId = null
            )
            finish()
        }
    }

    private fun setNoData() {
        progressBar.visibility = View.GONE
        label.text = "No data found"
        description.text = "This file has no accounts to import from."
        filePickerButton.visibility = View.VISIBLE
        filePickerButton.text = "Quit"
        filePickerButton.setOnClickListener {
            onBackPressed()
        }
    }

    data class Folder (
        val id: String,
        val name: String
    )

    data class Uri (
        val match: String?,
        val uri: String
    )

    data class Login (
        val uris: List<Uri>,
        val username: String?,
        val password: String?,
        val totp: String?
    )

    data class Item1 (
        val id: String,
        val organizationId: String?,
        val folderId: String?,
        val type: Int,
        val reprompt: Int,
        val name: String?,
        val notes: String?,
        val favorite: Boolean?,
        val fields: List<ItemField>?,
        val login: IOUtilities.Login?,
        val collectionIds: String?
    )

    data class ItemField (
        val name: String?,
        val value: String?,
        val type: Int,
        val linkedId: String?
    )

    data class SecureNote (
        val type: Int
    )

    data class Item2 (
        val id: String,
        val organizationId: String?,
        val folderId: String?,
        val type: Int,
        val reprompt: Int,
        val name: String?,
        val notes: String?,
        val favorite: Boolean?,
        val fields: List<ItemField>?,
        val secureNote: SecureNote,
        val collectionIds: String?
    )

    data class Card (
        val cardholderName: String?,
        val brand: String?,
        val number: String?,
        val expMonth: String?,
        val expYear: String?,
        val code: String?
    )

    data class Item3 (
        val id: String,
        val organizationId: String?,
        val folderId: String?,
        val type: Int,
        val reprompt: Int,
        val name: String?,
        val notes: String?,
        val favorite: Boolean?,
        val fields: List<ItemField>?,
        val card: Card,
        val collectionIds: String?
    )

    data class Identity (
        val title: String?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val address1: String?,
        val address2: String?,
        val address3: String?,
        val city: String?,
        val state: String?,
        val postalCode: String?,
        val country: String?,
        val company: String?,
        val email: String?,
        val phone: String?,
        val ssn: String?,
        val username: String?,
        val passportNumber: String?,
        val licenseNumber: String?
    )

    data class Item4 (
        val id: String,
        val organizationId: String?,
        val folderId: String?,
        val type: Int,
        val reprompt: Int,
        val name: String?,
        val notes: String?,
        val favorite: Boolean?,
        val fields: List<ItemField>?,
        val identity: Identity,
        val collectionIds: String?
    )

    data class BitwardenVault (
        val encrypted: Boolean,
        val folders: List<Folder>,
        val items: List<Any?>
    )

    data class ExtractedItems (
        val logins: MutableList<Item1>,
        val notes: MutableList<Item2>,
        val cards: MutableList<Item3>,
        val identities: MutableList<Item4>,
        val folders: List<Folder>,
    )

    private fun parseBitwardenJsonFile (fileData: String): ExtractedItems? {
        val mapper = jacksonObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val vault: BitwardenVault = mapper.readValue (fileData, BitwardenVault::class.java)

        if (vault.encrypted) throw NullPointerException()

        var extractedItems: ExtractedItems? = null

        if (vault.items.isNotEmpty()) {
            extractedItems = ExtractedItems (
                logins = mutableListOf(),
                notes = mutableListOf(),
                cards = mutableListOf(),
                identities = mutableListOf(),
                folders = vault.folders
            )
            for (item in vault.items) {
                when (item.toString().substringAfter(", type=").substringBefore(", ").toInt()) {
                    1 -> extractedItems.logins.add(mapper.convertValue(item, Item1::class.java))
                    2 -> extractedItems.notes.add(mapper.convertValue(item, Item2::class.java))
                    3 -> extractedItems.cards.add(mapper.convertValue(item, Item3::class.java))
                    4 -> extractedItems.identities.add(mapper.convertValue(item, Item4::class.java))
                }
            }
        }

        return extractedItems

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

