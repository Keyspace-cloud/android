package cloud.keyspace.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anggrayudi.storage.SimpleStorageHelper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.button.MaterialButton
import com.keyspace.keyspacemobile.NetworkUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.Date


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

        val intentData = crypto.receiveKeyringFromSecureIntent(
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
            storageHelper.openFilePicker(
                allowMultiple = false,
                filterMimeTypes = arrayOf(JSON_MIME_TYPE)
            )
        }

        storageHelper.onFileSelected = { _, files ->
            setImporting(files[0].name.toString())

            lifecycleScope.launch(Dispatchers.Main) {
                val vault: ExtractedItems? = withContext(Dispatchers.IO) {
                    val file = contentResolver.openInputStream(files[0].uri)
                    val fileData = String(file?.readBytes()!!)
                    file.close()

                    try {
                        parseBitwardenJsonFile(fileData)
                    } catch (e: Exception) {
                        Log.e("parseThread", "Error parsing Bitwarden file.", e)
                        null
                    }
                }

                if (vault != null) {
                    withContext(Dispatchers.Main) {
                        saveItems(vault)
                    }
                }

                delay(1000)

                // Save to KeyspaceFS
                try {
                    if (vault == null) {
                        setNoData()
                    } else {
                        val logins = vault.logins.map { it.name ?: "" }.toTypedArray()
                        val cards = vault.cards.map { it.name ?: "" }.toTypedArray()
                        val notes = vault.notes.map { it.name ?: "" }.toTypedArray()

                        val accounts = listOf(
                            *logins,
                            *cards,
                            *notes
                        )
                        setDone(accounts)
                    }
                } catch (_: Exception) {
                    setNoData()
                }
            }
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
        description.text =
            "Imported ${accounts.size} account${if (accounts.size > 1) "s" else ""} successfully! Make sure all your accounts are in the list below"
        description.append("\n")

        filePickerButton.visibility = View.VISIBLE
        filePickerButton.text = "Finish"
        filePickerButton.setOnClickListener {
            crypto.secureStartActivity(
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

    data class Folder(
        val id: String,
        val name: String
    )

    data class Uri(
        val match: String?,
        val uri: String
    )

    data class Login(
        val uris: List<Uri>,
        val username: String?,
        val password: String?,
        val totp: String?
    )

    data class PasswordHistory(
        val lastUsedDate: String,
        val password: String
    )

    data class Item1(
        val id: String,
        val organizationId: String?,
        val folderId: String?,
        val type: Int,
        val reprompt: Int,
        val name: String?,
        val notes: String?,
        val favorite: Boolean?,
        val fields: List<ItemField>?,
        val login: Login?,
        val passwordHistory: List<PasswordHistory>?,
        val collectionIds: String?,
        val revisionDate: String?,
        val creationDate: String?,
        val deletedDate: String?,
    )

    data class ItemField(
        val name: String?,
        val value: String?,
        val type: Int,
        val linkedId: String?
    )

    data class SecureNote(
        val type: Int
    )

    data class Item2(
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
        val collectionIds: String?,
        val creationDate: String?,
        val revisionDate: String?,
    )

    data class Card(
        val cardholderName: String?,
        val brand: String?,
        val number: String?,
        val expMonth: String?,
        val expYear: String?,
        val code: String?
    )

    data class Item3(
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

    data class Identity(
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

    data class Item4(
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

    data class BitwardenVault(
        val encrypted: Boolean,
        val folders: List<Folder>,
        val items: List<Any?>
    )

    data class ExtractedItems(
        val logins: MutableList<Item1>,
        val notes: MutableList<Item2>,
        val cards: MutableList<Item3>,
        val identities: MutableList<Item4>,
        val folders: List<Folder>,
    )

    private fun parseBitwardenJsonFile(fileData: String): ExtractedItems? {
        val mapper = jacksonObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val vault: BitwardenVault = mapper.readValue(fileData, BitwardenVault::class.java)

        if (vault.encrypted) throw NullPointerException()

        var extractedItems: ExtractedItems? = null

        if (vault.items.isNotEmpty()) {
            extractedItems = ExtractedItems(
                logins = mutableListOf(),
                notes = mutableListOf(),
                cards = mutableListOf(),
                identities = mutableListOf(),
                folders = vault.folders
            )
            for (item in vault.items) {
                Log.d("parseBitwardenJsonFile", "item = $item")

                when (item.toString().substringAfter(", type=").substringBefore(", ").toInt()) {
                    1 -> extractedItems.logins.add(
                        mapper.convertValue(item, Item1::class.java).also {
                            Log.d("parseBitwardenJsonFile", "login = $it")
                        }
                    )

                    2 -> extractedItems.notes.add(
                        mapper.convertValue(item, Item2::class.java).also {
                            Log.d("parseBitwardenJsonFile", "note = $it")
                        }
                    )

                    3 -> extractedItems.cards.add(
                        mapper.convertValue(item, Item3::class.java).also {
                            Log.d("parseBitwardenJsonFile", "card = $it")
                        }
                    )

                    4 -> extractedItems.identities.add(
                        mapper.convertValue(item, Item4::class.java).also {
                            Log.d("parseBitwardenJsonFile", "identity = $it")
                        }
                    )
                }
            }
        }

        return extractedItems.also {
            Log.d("parseBitwardenJsonFile", "extractedItems = $it")
        }

    }

    private fun saveItems(items: ExtractedItems) {
        val itemPersistence = ItemPersistence(
            applicationContext = applicationContext,
            appCompatActivity = this,
            keyring = keyring,
            itemId = null
        )

        val cards = items.cards.map { it.copy() }
        Log.d("saveItems", "cards = ${cards.size}")

        val logins = items.logins.map { it.copy() }
        Log.d("saveItems", "logins = ${logins.size}")

        val notes = items.notes.map { it.copy() }
        Log.d("saveItems", "notes = ${notes.size}")

        cards.forEach { card ->
            Log.d("saveItems", "Saving card ${card.name} (${card.id})")

            val expMonth = card.card.expMonth?.takeLast(2)?.toIntOrNull() ?: 0
            val expYear = card.card.expYear?.takeLast(2)?.toIntOrNull() ?: 0
            val expDate = String.format("%02d/%02d", expMonth, expYear)

            itemPersistence.saveCard(
                cardName = card.name ?: "",
                cardNumber = card.card.number ?: "",
                cardholderName = card.card.cardholderName ?: "",
                toDate = expDate,
                securityCode = card.card.code ?: "",
                atmPin = "",
                isAtmCard = false,
                hasRfidChip = false,
                iconFileName = null,
                cardColor = null,
                isFavorite = card.favorite ?: false,
                tagId = card.folderId,
                notes = card.notes ?: "",
                customFieldsData = card.fields?.map { field ->
                    IOUtilities.CustomField(
                        name = field.name ?: "",
                        value = field.value ?: "",
                        hidden = false
                    )
                }?.toMutableList() ?: mutableListOf(),
                frequencyAccessed = 0
            ) { error ->
                Log.e("saveItems", "Error saving card: $error")
            }
        }

        logins.forEach { login ->
            Log.d("saveItems", "Saving login ${login.name} (${login.id})")

            val username = login.login?.username ?: ""

            itemPersistence.saveLogin(
                siteName = login.name ?: "",
                siteUrlsData = login.login?.uris?.map {
                    it.uri
                }?.toMutableList() ?: mutableListOf(),
                userName = username,
                email = if (Regex(Patterns.EMAIL_ADDRESS.pattern()).matches(username)) {
                    username
                } else {
                    ""
                },
                password = login.login?.password ?: "",
                passwordHistoryData = login.passwordHistory?.map {
                    IOUtilities.Password(
                        password = it.password,
                        created = try {
                            OffsetDateTime.parse(it.lastUsedDate).toEpochSecond()
                        } catch (e: Exception) {
                            Log.e("saveItems", "Failed to parse date.", e)
                            Date().time
                        }
                    )
                }?.toMutableList() ?: mutableListOf(),
                secret = login.login?.totp ?: "",
                backupCodes = "",
                iconFileName = null,
                isFavorite = login.favorite ?: false,
                tagId = login.folderId,
                notes = login.notes ?: "",
                customFieldsData = login.fields?.map { field ->
                    IOUtilities.CustomField(
                        name = field.name ?: "",
                        value = field.value ?: "",
                        hidden = false
                    )
                }?.toMutableList() ?: mutableListOf()
            ) { error ->
                Log.e("saveItems", "Error saving login: $error")
            }
        }

        notes.forEach { note ->
            Log.d("saveItems", "Saving note ${note.name} (${note.id})")

            val consolidatedNote = (note.notes ?: "") + note.fields?.joinToString(
                separator = "\n",
                prefix = "\n"
            ) { field ->
                "${field.name}: ${field.value}"
            }

            itemPersistence.saveNote(
                note = consolidatedNote,
                noteColor = null,
                isFavorite = note.favorite ?: false,
                tagId = note.folderId,
                timestamp = note.revisionDate?.let {
                    try {
                        OffsetDateTime.parse(it).toEpochSecond()
                    } catch (e: Exception) {
                        Log.w("saveItems", "Failed to parse creation date.", e)
                        null
                    }
                } ?: note.creationDate?.let {
                    try {
                        OffsetDateTime.parse(it).toEpochSecond()
                    } catch (e: Exception) {
                        Log.w("saveItems", "Failed to parse revision date.", e)
                        null
                    }
                } ?: Date().time,
                frequencyAccessed = 0
            )
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        storageHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

}

