package cloud.keyspace.android

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.keyspace.keyspacemobile.NetworkUtilities
import java.time.Instant
import java.util.UUID

data class Error(
    val cardNumberError: String? = null,
    val securityCodeError: String? = null,
    val toDateError: String? = null,
    val cardholderNameError: String? = null,
    val nameError: String? = null,
    val atmPinError: String? = null,
    val siteNameError: String? = null,
    val emailError: String? = null,
    val secretError: String? = null,
)

class ItemPersistence(
    applicationContext: Context,
    appCompatActivity: AppCompatActivity,
    private val keyring: CryptoUtilities.Keyring,
    private var itemId: String?
) {
    private val crypto: CryptoUtilities = CryptoUtilities(applicationContext, appCompatActivity)
    private val misc: MiscUtilities = MiscUtilities(applicationContext)
    private val io: IOUtilities = IOUtilities(applicationContext, appCompatActivity, keyring)
    private val vault: IOUtilities.Vault = io.getVault()
    private val network: NetworkUtilities =
        NetworkUtilities(applicationContext, appCompatActivity, keyring)

    private var _card: IOUtilities.Card? = null
    private val card: IOUtilities.Card
        get() = _card!!

    private var _login: IOUtilities.Login? = null
    private val login: IOUtilities.Login
        get() = _login!!

    private var _note: IOUtilities.Note? = null
    private val note: IOUtilities.Note
        get() = _note!!

    private val nextActivityClassName: String =
        applicationContext.getString(R.string.title_activity_dashboard)

    init {
        _card = try {
            io.decryptCard(io.getCard(itemId!!, vault)!!)
        } catch (e: Exception) {
            Log.w("ItemPersistence", "Unable to decrypt card.", e)
            null
        }
        Log.d("ItemPersistence", "card = $_card")

        _login = try {
            io.decryptLogin(io.getLogin(itemId!!, vault)!!)
        } catch (e: Exception) {
            Log.w("ItemPersistence", "Unable to decrypt login.", e)
            null
        }
        Log.d("ItemPersistence", "login = $_login")

        _note = try {
            io.decryptNote(io.getNote(itemId!!, vault)!!)
        } catch (e: Exception) {
            Log.w("ItemPersistence", "Unable to decrypt note.", e)
            null
        }
        Log.d("ItemPersistence", "note = $_note")
    }

    fun saveLogin(
        siteName: String,
        siteUrlsData: MutableList<String>,
        userName: String,
        email: String,
        password: String,
        passwordHistoryData: MutableList<IOUtilities.Password>,
        secret: String,
        backupCodes: String,
        iconFileName: String?,
        isFavorite: Boolean,
        tagId: String?,
        notes: String,
        customFieldsData: MutableList<IOUtilities.CustomField>,
        onInputError: (error: Error) -> Unit
    ) {
        Log.d("saveLogin", "itemId = $itemId")

        if (siteName.isBlank()) {
            onInputError(
                Error(
                    siteNameError = "Please enter a site name"
                ).also {
                    Log.e("saveLogin", "error = $it")
                }
            )

            return
        }

        if (email.isNotBlank()) {
            if (!misc.isValidEmail(email)) {
                onInputError(
                    Error(
                        emailError = "Please enter a valid email"
                    ).also {
                        Log.e("saveLogin", "error = $it")
                    }
                )

                return
            }
        }

        if (secret.isNotBlank() && secret.length < 6) {
            onInputError(
                Error(
                    secretError = "Please enter a valid TOTP secret"
                ).also {
                    Log.e("saveLogin", "error = $it")
                }
            )

            return
        }

        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = io.getLogin(itemId!!, vault)?.dateCreated!!
            vault.login?.remove(io.getLogin(itemId!!, vault))

            if (login.loginData != null) {
                if (!login.loginData!!.password.isNullOrEmpty()) {
                    if (password != login.loginData?.password) {
                        passwordHistoryData.add(
                            IOUtilities.Password(
                                password = password,
                                created = Instant.now().epochSecond
                            )
                        )
                    }
                }
            }
        } else {
            passwordHistoryData.clear()
            passwordHistoryData.add(
                IOUtilities.Password(
                    password = password,
                    created = Instant.now().epochSecond
                )
            )
        }

        val data = IOUtilities.Login(
            id = itemId ?: UUID.randomUUID().toString(),
            organizationId = null,
            type = io.TYPE_LOGIN,
            name = siteName,
            notes = notes,
            favorite = isFavorite,
            tagId = tagId,
            loginData = IOUtilities.LoginData(
                username = userName,
                password = password,
                passwordHistory = if (passwordHistoryData.size > 0) {
                    passwordHistoryData
                } else {
                    null
                },
                email = email,
                totp = IOUtilities.Totp(
                    secret = secret,
                    backupCodes = backupCodes.replace("\n", "").split(",").toMutableList()
                ),
                siteUrls = if (siteUrlsData.size > 0) {
                    siteUrlsData
                } else {
                    null
                }
            ),
            dateCreated = dateCreated,
            dateModified = Instant.now().epochSecond,
            frequencyAccessed = 0,
            customFields = customFieldsData,
            iconFile = iconFileName
        )
        Log.d("saveLogin", "data = $data")

        val encryptedLogin = io.encryptLogin(data)
        Log.d("saveLogin", "encryptedLogin = $encryptedLogin")

        vault.login?.add(encryptedLogin)
        io.writeVault(vault)

        if (itemId != null) {
            network.writeQueueTask(encryptedLogin, mode = network.MODE_PUT)
            Log.d("saveLogin", "Updating existing login...")
        } else {
            network.writeQueueTask(encryptedLogin, mode = network.MODE_POST)
            Log.d("saveLogin", "Saving new login...")
        }

        crypto.secureStartActivity(
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = nextActivityClassName,
            keyring = keyring,
            itemId = null
        )
    }

    fun saveCard(
        cardName: String,
        cardNumber: String,
        cardholderName: String,
        toDate: String,
        securityCode: String,
        atmPin: String,
        isAtmCard: Boolean,
        hasRfidChip: Boolean,
        iconFileName: String?,
        cardColor: String?,
        isFavorite: Boolean,
        tagId: String?,
        notes: String,
        customFieldsData: MutableList<IOUtilities.CustomField>,
        frequencyAccessed: Long,
        onInputError: (error: Error) -> Unit
    ) {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = card.dateCreated!!
            vault.card?.remove(io.getCard(itemId!!, vault))
        }

        if (cardNumber.replace(" ", "").length < 16) {
            onInputError(
                Error(
                    cardNumberError = "Enter a valid 16 digit card number"
                )
            )
        } else if (cardNumber.replace(" ", "").length in 17..18
            || cardNumber.replace(" ", "").length > 19
        ) {
            onInputError(
                Error(
                    cardNumberError = "Enter a valid 19 digit card number"
                )
            )
        } else if (securityCode.length !in 3..4) {
            onInputError(
                Error(
                    securityCodeError = "Enter a valid security code"
                )
            )
        } else if (toDate.isEmpty()) {
            onInputError(
                Error(
                    toDateError = "Enter an expiry date"
                )
            )
        } else if (cardholderName.isEmpty()) {
            onInputError(
                Error(
                    cardholderNameError = "Enter card holder's name"
                )
            )
        } else if (cardName.isEmpty()) {
            onInputError(
                Error(
                    nameError = "Enter a name. This can be your bank's name."
                )
            )
        } else if (isAtmCard && atmPin.length < 4) {
            onInputError(
                Error(
                    atmPinError = "Enter a valid Personal Identification Number"
                )
            )
        } else {
            val data = IOUtilities.Card(
                id = itemId ?: UUID.randomUUID().toString(),
                organizationId = null,
                type = io.TYPE_CARD,
                name = cardName,
                color = cardColor,
                favorite = isFavorite,
                tagId = tagId,
                dateCreated = dateCreated,
                dateModified = Instant.now().epochSecond,
                frequencyAccessed = frequencyAccessed + 1,
                cardNumber = cardNumber.filter { !it.isWhitespace() },
                cardholderName = cardholderName,
                expiry = toDate,
                notes = notes,
                pin = if (atmPin.length == 4 && isAtmCard) {
                    atmPin
                } else {
                    ""
                },
                securityCode = securityCode,
                customFields = customFieldsData,
                rfid = hasRfidChip,
                iconFile = iconFileName
            )

            val encryptedCard = io.encryptCard(data)

            vault.card?.add(encryptedCard)
            io.writeVault(vault)

            if (itemId != null) {
                network.writeQueueTask(encryptedCard, mode = network.MODE_PUT)
            } else {
                network.writeQueueTask(encryptedCard, mode = network.MODE_POST)
            }

            crypto.secureStartActivity(
                nextActivity = Dashboard(),
                nextActivityClassNameAsString = nextActivityClassName,
                keyring = keyring,
                itemId = null
            )
        }
    }

    fun saveNote(
        note: String,
        noteColor: String?,
        isFavorite: Boolean,
        tagId: String?,
        timestamp: Long,
        frequencyAccessed: Long,
    ) {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = this.note.dateCreated!!
            vault.note?.remove(io.getNote(itemId!!, vault))
        }

        val data = IOUtilities.Note(
            id = itemId ?: UUID.randomUUID().toString(),
            organizationId = null,
            type = io.TYPE_NOTE,
            notes = note,
            color = noteColor,
            favorite = isFavorite,
            tagId = tagId,
            dateCreated = dateCreated,
            dateModified = timestamp,
            frequencyAccessed = frequencyAccessed
        )

        val encryptedNote = io.encryptNote(data)

        vault.note?.add(encryptedNote)
        io.writeVault(vault)

        if (itemId != null) {
            network.writeQueueTask(encryptedNote, mode = network.MODE_PUT)
        } else {
            network.writeQueueTask(encryptedNote, mode = network.MODE_POST)
        }

        crypto.secureStartActivity(
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = nextActivityClassName,
            keyring = keyring,
            itemId = null
        )
    }
}
