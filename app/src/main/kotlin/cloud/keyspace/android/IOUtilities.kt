package cloud.keyspace.android

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nulabinc.zxcvbn.Zxcvbn
import org.json.JSONException
import java.io.File
import java.io.IOException
import kotlin.math.log

/**
 * Keyspace\'s file input/output utilities. KeyspaceFS management can be found here.
 * @param applicationContext The context of the activity that an `IOUtilities` object is initialized in, example: `applicationContext`, `this` etc.
 * @param appCompatActivity The current activity functions are called in. This is the `AppCompatActivity` that will be used for permissions prompts. Use `this` if unsure.
 */

class IOUtilities(
    private var applicationContext: Context,  // The context to derive information from.
    private var appCompatActivity: AppCompatActivity, // The activity to display the permissions prompt inside of.
    private var keyring: CryptoUtilities.Keyring,
) { // constructor without init {}

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
    private val mapper = jacksonObjectMapper()

    private var crypto: CryptoUtilities = CryptoUtilities(applicationContext, appCompatActivity)

    private val filenameExtension = "kfs"
    private val vaultFilename = "vault"
    private var filename: String? = "$vaultFilename.$filenameExtension"

    val TYPE_LOGIN = "login"
    val TYPE_NOTE = "note"
    val TYPE_CARD = "card"
    val TYPE_TAG = "tag"

    val file = File(applicationContext.cacheDir, filename!!)

    data class Tag (
        val id: String,
        val name: String,
        val type: String,
        val dateCreated: Long?,
        val color: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Vault (
        val version: String,
        val tag: MutableList<Tag>?,
        val login: MutableList<Login>?,
        val note: MutableList<Note>?,
        val card: MutableList<Card>?,
    )

    data class CustomField (
        var name: String?,
        var value: String,
        val hidden: Boolean
    )

    data class Password (
        val password: String,
        val created: Long
    )

    data class Totp (
        val secret: String?,
        val backupCodes: MutableList<String>?
    )

    data class LoginData (
        val username: String?,
        val password: String?,
        val passwordHistory: MutableList<Password>?,

        val email: String?,
        val totp: Totp?,
        val siteUrls: MutableList<String>?
    )

    data class Login (
        val id: String?,
        val organizationId: String?,
        val type: String?,
        val name: String?,
        val notes: String?,
        val favorite: Boolean,
        val tagId: String?,
        val loginData: LoginData?,
        val dateCreated: Long?,
        val dateModified: Long?,
        val frequencyAccessed: Long?,
        val iconFile: String?,
        val customFields: MutableList<CustomField>?
    )

    data class Note (
        val id: String?,
        val organizationId: String?,
        val type: String?,
        val dateCreated: Long?,
        val dateModified: Long?,
        val frequencyAccessed: Long?,
        val notes: String?,
        val color: String?,
        val favorite: Boolean,
        val tagId: String?,
    )

    data class Card (
        val id: String?,
        val organizationId: String?,
        val type: String?,
        val cardNumber: String?,
        val color: String?,
        val expiry: String?,
        val securityCode: String?,
        val favorite: Boolean,
        val cardholderName: String?,
        val name: String?,
        val pin: String?,
        var rfid: Boolean?,
        val notes: String?,
        val dateCreated: Long?,
        val dateModified: Long?,
        val frequencyAccessed: Long?,
        val iconFile: String?,
        val tagId: String?,
        val customFields: MutableList<CustomField>?
    )

    // reader functions

    fun wipeAllKeyspaceFSFiles(): Boolean {
        file.writeText("")
        file.delete()
        file.deleteRecursively()
        return true
    }

    fun getVault (): Vault {

        lateinit var vault: Vault

        try {

            if (!file.exists()) {
                Log.d("Keyspace", "Couldn't read a valid KeyspaceFS file. Creating new file at \"${file.absolutePath}\" and initializing it.")
                val emptyVault = Vault (
                    version = applicationContext.getString(R.string.vault_version),
                    tag = mutableListOf(),
                    login = mutableListOf(),
                    note = mutableListOf(),
                    card = mutableListOf(),
                )

                writeVault (emptyVault)

            } else Log.d("Keyspace", "Found a KeyspaceFS file at ${file.absolutePath}. Decrypting...")

            val inputStream = file.inputStream()
            val fileData = inputStream.readBytes()
            inputStream.close()

            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // In case the vault isn't updated, ignore new properties in json
            vault = mapper.readValue (fileData, Vault::class.java)

            if (vault.version != applicationContext.getString(R.string.vault_version)) {
                Log.e("Keyspace", "Vault version mismatch. The vault at \"${file.absolutePath}\" is on version ${vault.version}, but version the app uses version ${applicationContext.getString(R.string.vault_version)}")
            }

            vault = Vault (
                version = vault.version,
                tag = vault.tag,
                login = vault.login,
                note = vault.note,
                card = vault.card
            )

        } catch (permissionError: NullPointerException) {
            // ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            Log.e("Keyspace", "File read failed.\n${permissionError.stackTraceToString()}")

        } catch (noFile: NoSuchFileException) {
            Log.e("Keyspace", "File write failed. Couldn't find any .$filenameExtension files\n${noFile.stackTraceToString()}")

        } catch (ioError: IOException) {
            // ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            Log.e("Keyspace", "File write failed. Did you grant Keyspace storage access permissions?\n${ioError.stackTraceToString()}")

        } catch (fsException: FileSystemException) {
            Log.e("Keyspace", "Empty .$filenameExtension file.\n${fsException.stackTraceToString()}")

        } catch (invalidJSON: JSONException) {
            Log.e("Keyspace", "Invalid JSON format detected.\n${invalidJSON.stackTraceToString()}")
        }

        return vault

    }

    fun getTags (vault: Vault): MutableList<Tag> {
        return vault.tag!!
    }

    fun getTag(id: String, vault: Vault): Tag? {
        val tags: MutableList<Tag> = getTags (vault)
        var matchedTag: Tag? = null

        for (tag in tags!!) if (tag.id == id) matchedTag = tag

        return matchedTag
    }

    fun encryptTag (tag: Tag): Tag? {
        return Tag (
            id = tag.id,
            name = crypto.kfsEncrypt(tag.name, keyring.XCHACHA_POLY1305_KEY!!)!!,
            type = tag.type,
            dateCreated = tag.dateCreated,
            color = try { if (!tag.color.isNullOrBlank()) crypto.kfsEncrypt(tag.color, keyring.XCHACHA_POLY1305_KEY!!) else null } catch (exception: Exception) { return null }
        )
    }

    fun decryptTag (tag: Tag): Tag? {
        return Tag (
            id = tag.id,
            name = crypto.kfsDecrypt (tag.name, keyring.XCHACHA_POLY1305_KEY!!)!!,
            type = tag.type,
            dateCreated = tag.dateCreated,
            color = try { if (!tag.color.isNullOrBlank()) crypto.kfsDecrypt(tag.color, keyring.XCHACHA_POLY1305_KEY!!) else null } catch (exception: Exception) { return null }
        )
    }

    fun getLogins (vault: Vault): MutableList<Login> {
        return vault.login!!
    }

    fun getLogin (id: String, vault: Vault): Login? {
        val logins: MutableList<Login> = getLogins (vault)
        var matchedLogin: Login? = null

        for (login in logins) if (login.id == id) matchedLogin = login

        return matchedLogin
    }

    fun decryptLogin (login: Login): Login {
        fun passwordHistory (): MutableList<Password>? {
            val passwordHistory: MutableList<Password> = mutableListOf()

            if (login.loginData == null) {
                return null
            } else {
                if (login.loginData.passwordHistory == null) {
                    return null
                } else {
                    for (oldPassword in login.loginData.passwordHistory) {
                        passwordHistory.add (
                            Password (
                                password = crypto.kfsDecrypt (oldPassword.password, keyring.XCHACHA_POLY1305_KEY!!)!!,
                                created = oldPassword.created
                            )
                        )
                    }
                }
            }

            return passwordHistory
        }

        fun backupCodes (): MutableList<String>? {
            val backupCodes: MutableList<String> = mutableListOf()
            if (login.loginData == null) {
                return null
            } else {
                if (login.loginData.totp != null) {
                    if (login.loginData.totp.backupCodes == null) {
                        return null
                    } else {
                        for (code in login.loginData.totp.backupCodes) backupCodes.add (crypto.kfsDecrypt (code, keyring.XCHACHA_POLY1305_KEY!!)!!)
                    }
                }
            }
            return backupCodes
        }

        fun siteUrls (): MutableList<String>? {
            val siteUrls: MutableList<String> = mutableListOf()
            if (login.loginData == null) {
                return null
            } else {
                if (login.loginData.siteUrls == null) {
                    return null
                } else {
                    for (site in login.loginData.siteUrls) siteUrls.add (crypto.kfsDecrypt (site, keyring.XCHACHA_POLY1305_KEY!!)!!)
                }
            }

            return siteUrls
        }

        fun customFields (): MutableList<CustomField>? {
            val customFields: MutableList<CustomField> = mutableListOf()
            if (login.loginData == null) {
                return null
            } else {
                if (login.customFields == null) {
                    return null
                } else {
                    for (field in login.customFields) {
                        customFields.add (
                            CustomField (
                                name = crypto.kfsDecrypt (field.name, keyring.XCHACHA_POLY1305_KEY!!)!!,
                                value = crypto.kfsDecrypt (field.value, keyring.XCHACHA_POLY1305_KEY!!)!!,
                                hidden = field.hidden
                            )
                        )
                    }
                }
            }

            return customFields
        }

        return Login (
            id = try { login.id } catch (_: Exception) { } as String?,
            organizationId = login.organizationId,
            type = login.type,
            name = try { crypto.kfsDecrypt (login.name, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?,
            notes = try { crypto.kfsDecrypt (login.notes, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?,
            favorite = login.favorite,
            tagId = login.tagId,
            loginData = LoginData (
                username = try { crypto.kfsDecrypt (login.loginData?.username, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?,
                password = try { crypto.kfsDecrypt (login.loginData?.password, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null  } as String?,
                passwordHistory = passwordHistory(),
                email = try { crypto.kfsDecrypt (login.loginData?.email, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) {  null } as String?,
                totp = Totp (
                    secret = try { crypto.kfsDecrypt (login.loginData?.totp?.secret, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?,
                    backupCodes = backupCodes()
                ),
                siteUrls = siteUrls()
            ),
            dateCreated = login.dateCreated,
            dateModified = login.dateModified,
            frequencyAccessed = login.frequencyAccessed,
            customFields = customFields(),
            iconFile = try { crypto.kfsDecrypt (login.iconFile, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?
        )
    }

    val SORT_TAG_CREATED = "SORT_TAG_CREATED"
    val SORT_LAST_EDITED = "SORT_LAST_EDITED"
    val SORT_OLDEST = "SORT_OLDEST"
    val SORT_FAVORITES = "SORT_FAVORITES"
    val SORT_NAME_ASCENDING = "SORT_NAME_ASCENDING"
    val SORT_AUTHENTICATOR_ASCENDING = "SORT_AUTHENTICATOR_ASCENDING"
    val SORT_NOTES_ASCENDING = "SORT_NOTES_ASCENDING"
    val SORT_WEAKEST = "SORT_WEAKEST"
    val SORT_USERNAME_ASCENDING = "SORT_USERNAME_ASCENDING"
    val SORT_EMAIL_ASCENDING = "SORT_EMAIL_ASCENDING"
    val SORT_COLOR_ASCENDING = "SORT_COLOR_ASCENDING"

    fun vaultSorter (vault: Vault, sortingMode: String): Vault {

        val decryptedTags = mutableListOf<Tag>()
        if (vault.tag != null)
            for (tag in vault.tag) decryptedTags.add(decryptTag(tag)!!)

        val decryptedLogins = mutableListOf<Login>()
        if (vault.login != null)
            for (login in vault.login) decryptedLogins.add(decryptLogin(login)!!)

        val decryptedNotes = mutableListOf<Note>()
        if (vault.note != null)
            for (note in vault.note) decryptedNotes.add(decryptNote(note)!!)

        val decryptedCards = mutableListOf<Card>()
        if (vault.card != null)
            for (card in vault.card) decryptedCards.add(decryptCard(card)!!)

        val decryptedVault = Vault (
            version = vault.version,
            tag = decryptedTags,
            login = decryptedLogins,
            note = decryptedNotes,
            card = decryptedCards
        )

        fun tagCreatedSort (): Vault {
            val loginsContainingTags = mutableListOf<Login>()
            val loginsNotContainingTags = mutableListOf<Login>()

            val notesContainingTags = mutableListOf<Note>()
            val notesNotContainingTags = mutableListOf<Note>()

            val cardsContainingTags = mutableListOf<Card>()
            val cardsNotContainingTags = mutableListOf<Card>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (login.tagId != null) loginsContainingTags.add (login) else loginsNotContainingTags.add(login)
                }
            }

            if (decryptedVault.note != null) {
                for (note in decryptedVault.note) {
                    if (note.tagId != null) notesContainingTags.add (note) else notesNotContainingTags.add(note)
                }
            }

            if (decryptedVault.card != null) {
                for (card in decryptedVault.card) {
                    if (card.tagId != null) cardsContainingTags.add (card) else cardsNotContainingTags.add(card)
                }
            }

            val logins = (loginsContainingTags + loginsNotContainingTags).toMutableList()
            val notes = (notesContainingTags + notesNotContainingTags).toMutableList()
            val cards = (cardsContainingTags + cardsNotContainingTags).toMutableList()

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in logins) logins[logins.indexOf(login)] = encryptLogin(login)
            for (note in notes) notes[notes.indexOf(note)] = encryptNote(note)
            for (card in cards) cards[cards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = logins,
                note = notes,
                card = cards
            )
        }

        fun favoriteSort (): Vault {
            val loginsContainingFavorites = mutableListOf<Login>()
            val loginsNotContainingFavorites = mutableListOf<Login>()

            val notesContainingFavorites = mutableListOf<Note>()
            val notesNotContainingFavorites = mutableListOf<Note>()

            val cardsContainingFavorites = mutableListOf<Card>()
            val cardsNotContainingFavorites = mutableListOf<Card>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (login.favorite) {
                        loginsContainingFavorites.add (login)
                    } else loginsNotContainingFavorites.add(login)
                }
            }

            if (decryptedVault.note != null) {
                for (note in decryptedVault.note) {
                    if (note.favorite) {
                        notesContainingFavorites.add (note)
                    } else notesNotContainingFavorites.add(note)
                }
            }

            if (decryptedVault.card != null) {
                for (card in decryptedVault.card) {
                    if (card.favorite) {
                        cardsContainingFavorites.add (card)
                    } else cardsNotContainingFavorites.add(card)
                }
            }

            val combinedLogins = (loginsContainingFavorites + loginsNotContainingFavorites).toMutableList()
            val combinedNotes = (notesContainingFavorites + notesNotContainingFavorites).toMutableList()
            val combinedCards = (cardsContainingFavorites + cardsNotContainingFavorites).toMutableList()

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in combinedLogins) combinedLogins[combinedLogins.indexOf(login)] = encryptLogin(login)
            for (note in combinedNotes) combinedNotes[combinedNotes.indexOf(note)] = encryptNote(note)
            for (card in combinedCards) combinedCards[combinedCards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = combinedLogins,
                note = combinedNotes,
                card = combinedCards
            )
        }

        fun nameAscendingSort (): Vault {
            var logins = mutableListOf<Login>()
            var notes = mutableListOf<Note>()
            var cards = mutableListOf<Card>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    logins.add(login)
                }
                logins = logins.sortedBy { it.name.toString().lowercase().replace(Regex("[^A-Za-z0-9]"),"").replace(" ", "") }.toMutableList()
            }
            if (decryptedVault.note != null) {
                for (note in decryptedVault.note) {
                    notes.add(note)
                }
                notes = notes.sortedBy { it.notes.toString().lowercase().replace(Regex("[^A-Za-z0-9]"),"").replace(" ", "") }.toMutableList()
            }
            if (decryptedVault.card != null) {
                for (card in decryptedVault.card) {
                    cards.add(card)
                }
                cards = cards.sortedBy { it.name.toString().lowercase().replace(Regex("[^A-Za-z0-9]"),"").replace(" ", "") }.toMutableList()
            }

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in logins) logins[logins.indexOf(login)] = encryptLogin(login)
            for (note in notes) notes[notes.indexOf(note)] = encryptNote(note)
            for (card in cards) cards[cards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = logins,
                note = notes,
                card = cards
            )
        }

        fun notesAscendingSort (): Vault {
            val loginsContainingNotes = mutableListOf<Login>()
            val loginsNotContainingNotes = mutableListOf<Login>()

            val notesContainingNotes = mutableListOf<Note>()
            val notesNotContainingNotes = mutableListOf<Note>()

            val cardsContainingNotes = mutableListOf<Card>()
            val cardsNotContainingNotes = mutableListOf<Card>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (!login.notes.isNullOrBlank()) {
                        loginsContainingNotes.add (login)
                    } else loginsNotContainingNotes.add(login)
                }
            }

            if (decryptedVault.note != null) {
                for (note in decryptedVault.note) {
                    if (!note.notes.isNullOrBlank()) {
                        notesContainingNotes.add (note)
                    } else notesNotContainingNotes.add(note)
                }
            }

            if (decryptedVault.card != null) {
                for (card in decryptedVault.card) {
                    if (!card.notes.isNullOrBlank()) {
                        cardsContainingNotes.add (card)
                    } else cardsNotContainingNotes.add(card)
                }
            }

            val combinedLogins = (loginsContainingNotes.sortedBy { it.notes.toString().lowercase().replace(Regex("[^A-Za-z0-9]"),"").replace(" ", "") } + loginsNotContainingNotes).toMutableList()
            val combinedNotes = (notesContainingNotes.sortedBy { it.notes.toString().lowercase().replace(Regex("[^A-Za-z0-9]"),"").replace(" ", "") } + notesNotContainingNotes).toMutableList()
            val combinedCards = (cardsContainingNotes.sortedBy { it.notes.toString().lowercase().replace(" ", "").lowercase().replace(Regex("[^A-Za-z0-9]"),"").replace(" ", "") } + cardsNotContainingNotes).toMutableList()

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in combinedLogins) combinedLogins[combinedLogins.indexOf(login)] = encryptLogin(login)
            for (note in combinedNotes) combinedNotes[combinedNotes.indexOf(note)] = encryptNote(note)
            for (card in combinedCards) combinedCards[combinedCards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = combinedLogins,
                note = combinedNotes,
                card = combinedCards
            )
        }

        fun authenticatorAscendingSort (): Vault {
            var loginsContainingTotp = mutableListOf<Login>()
            val loginsNotContainingTotp = mutableListOf<Login>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (login.loginData != null) {
                        if (login.loginData.totp != null) {
                            if (login.loginData.totp.secret != null){
                                if (login.loginData.totp.secret.isNotEmpty()) loginsContainingTotp.add(login) else loginsNotContainingTotp.add(login)
                            } else loginsNotContainingTotp.add(login)
                        } else loginsNotContainingTotp.add(login)
                    }
                }
            }

            loginsContainingTotp = loginsContainingTotp.sortedBy { it.name.toString() }.toMutableList()

            val combinedLogins: MutableList<Login> = (loginsContainingTotp + loginsNotContainingTotp).toMutableList()
            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in combinedLogins) combinedLogins[combinedLogins.indexOf(login)] = encryptLogin(login)
            for (note in decryptedNotes) decryptedNotes[decryptedNotes.indexOf(note)] = encryptNote(note)
            for (card in decryptedCards) decryptedCards[decryptedCards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = combinedLogins,
                note = decryptedNotes,
                card = decryptedCards
            )
        }

        fun emailAscendingSort (): Vault {
            var loginsContainingEmails = mutableListOf<Login>()
            var loginsNotContainingEmails = mutableListOf<Login>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (login.loginData != null) {
                        if (login.loginData.email != null) loginsContainingEmails.add(login) else loginsNotContainingEmails.add(login)
                    }
                }
                loginsContainingEmails = loginsContainingEmails.sortedBy { it.loginData!!.email }.toMutableList()
            }

            val combinedLogins: MutableList<Login> = (loginsContainingEmails + loginsNotContainingEmails).toMutableList()
            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in combinedLogins) combinedLogins[combinedLogins.indexOf(login)] = encryptLogin(login)
            for (note in decryptedNotes) decryptedNotes[decryptedNotes.indexOf(note)] = encryptNote(note)
            for (card in decryptedCards) decryptedCards[decryptedCards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedVault.tag,
                login = combinedLogins,
                note = decryptedVault.note,
                card = decryptedVault.card
            )
        }

        fun weakestAscendingSort (): Vault {
            var loginsContainingWeakPassword = mutableListOf<Login>()
            var loginsNotContainingWeakPassword = mutableListOf<Login>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (login.loginData != null) {
                        if (login.loginData.password != null) {
                            if (Zxcvbn().measure(login.loginData.password).score <= 2 && login.loginData.totp?.secret.isNullOrBlank()) loginsContainingWeakPassword.add (login)
                            else loginsNotContainingWeakPassword.add (login)
                        } else loginsContainingWeakPassword.add (login)
                    }
                }
                loginsContainingWeakPassword = loginsContainingWeakPassword.sortedBy { it.dateModified }.toMutableList()
                loginsNotContainingWeakPassword = loginsNotContainingWeakPassword.sortedBy { it.loginData?.totp?.secret.isNullOrBlank() }.toMutableList()
            }

            val combinedLogins: MutableList<Login> = (loginsContainingWeakPassword + loginsNotContainingWeakPassword).toMutableList()
            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in combinedLogins) combinedLogins[combinedLogins.indexOf(login)] = encryptLogin(login)
            for (note in decryptedNotes) decryptedNotes[decryptedNotes.indexOf(note)] = encryptNote(note)
            for (card in decryptedCards) decryptedCards[decryptedCards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedVault.tag,
                login = combinedLogins,
                note = decryptedVault.note,
                card = decryptedVault.card
            )
        }

        fun usernameAscendingSort (): Vault {
            var loginsContainingUsernames = mutableListOf<Login>()
            var loginsNotContainingUsernames = mutableListOf<Login>()

            if (decryptedVault.login != null) {
                for (login in decryptedVault.login) {
                    if (login.loginData != null) {
                        if (login.loginData.username != null) loginsContainingUsernames.add(login) else loginsNotContainingUsernames.add(login)
                    }
                }
                loginsContainingUsernames = loginsContainingUsernames.sortedBy { it.loginData!!.username }.toMutableList()
            }

            val combinedLogins: MutableList<Login> = (loginsContainingUsernames + loginsNotContainingUsernames).toMutableList()
            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in combinedLogins) combinedLogins[combinedLogins.indexOf(login)] = encryptLogin(login)
            for (note in decryptedNotes) decryptedNotes[decryptedNotes.indexOf(note)] = encryptNote(note)
            for (card in decryptedCards) decryptedCards[decryptedCards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedVault.tag,
                login = combinedLogins,
                note = decryptedVault.note,
                card = decryptedVault.card
            )
        }

        fun lastEditedSort (): Vault {
            var logins = mutableListOf<Login>()
            var notes = mutableListOf<Note>()
            var cards = mutableListOf<Card>()

            logins = decryptedLogins.sortedByDescending { it.dateModified }.toMutableList()
            notes = decryptedNotes.sortedByDescending { it.dateModified }.toMutableList()
            cards = decryptedCards.sortedByDescending { it.dateModified }.toMutableList()

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in logins) logins[logins.indexOf(login)] = encryptLogin(login)
            for (note in notes) notes[notes.indexOf(note)] = encryptNote(note)
            for (card in cards) cards[cards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = logins,
                note = notes,
                card = cards
            )
        }

        fun oldestSort (): Vault {
            var logins = mutableListOf<Login>()
            var notes = mutableListOf<Note>()
            var cards = mutableListOf<Card>()

            logins = decryptedLogins.sortedBy { it.dateModified }.toMutableList()
            notes = decryptedNotes.sortedBy { it.dateModified }.toMutableList()
            cards = decryptedCards.sortedBy{ it.dateModified }.toMutableList()

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in logins) logins[logins.indexOf(login)] = encryptLogin(login)
            for (note in notes) notes[notes.indexOf(note)] = encryptNote(note)
            for (card in cards) cards[cards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = logins,
                note = notes,
                card = cards
            )
        }

        fun colorSort (): Vault {

            fun getColor (color: String?): Int {
                return if (color == null) 0 else Color.parseColor(color)
            }

            val notes = mutableListOf<Note>()
            val cards = mutableListOf<Card>()

            if (decryptedVault.note != null) {
                for (note in decryptedVault.note) {
                    notes.add(note)
                }
                notes.sortBy { getColor(it.color) }
            }

            if (decryptedVault.card != null) {
                for (card in decryptedVault.card) {
                    cards.add(card)
                }
                cards.sortBy { getColor(it.color) }
            }

            for (decryptedTag in decryptedTags) decryptedTags[decryptedTags.indexOf(decryptedTag)] = encryptTag(decryptedTag)!!
            for (login in decryptedLogins) decryptedLogins[decryptedLogins.indexOf(login)] = encryptLogin(login)
            for (note in notes) notes[notes.indexOf(note)] = encryptNote(note)
            for (card in cards) cards[cards.indexOf(card)] = encryptCard(card)

            return Vault (
                version = decryptedVault.version,
                tag = decryptedTags,
                login = decryptedVault.login,
                note = notes,
                card = cards
            )
        }

        return when (sortingMode) {
            SORT_TAG_CREATED -> tagCreatedSort()
            SORT_NAME_ASCENDING -> nameAscendingSort()
            SORT_NOTES_ASCENDING -> notesAscendingSort()
            SORT_LAST_EDITED -> lastEditedSort()
            SORT_FAVORITES -> favoriteSort()
            SORT_OLDEST -> oldestSort()
            SORT_AUTHENTICATOR_ASCENDING -> authenticatorAscendingSort()
            SORT_EMAIL_ASCENDING -> emailAscendingSort()
            SORT_USERNAME_ASCENDING -> usernameAscendingSort()
            SORT_COLOR_ASCENDING -> colorSort()
            SORT_WEAKEST -> weakestAscendingSort()
            else -> lastEditedSort()
        }

    }

    fun vaultsDiffer (localVault: Vault, serverVault: Vault): Boolean {
        /*
        * Iterate through items and store a List(HashMap<String, Long>)
        * i.e. a list of maps containing itemIds and timestamps from each vault.
        * Loop through and compare if they have the same entries. If they don't,
        * return the newer vault with data requested in ModificationData
        * */

        val localLoginTimestamps = mutableListOf<Long>()
        val localNoteTimestamps = mutableListOf<Long>()
        val localCardTimestamps = mutableListOf<Long>()
        val localTagTimestamps = mutableListOf<Long>()

        val serverLoginTimestamps = mutableListOf<Long>()
        val serverNoteTimestamps = mutableListOf<Long>()
        val serverCardTimestamps = mutableListOf<Long>()
        val serverTagTimestamps = mutableListOf<Long>()

        var isModified: Boolean = false

        if (localVault.tag != null) {
            for (item in localVault.tag) {
                if (item.dateCreated != null) localTagTimestamps.add(item.dateCreated)
            }
        }
        if (serverVault.tag != null) {
            for (item in serverVault.tag) {
                if (item.dateCreated != null) serverTagTimestamps.add(item.dateCreated)
            }
        }

        if (localVault.login != null) {
            for (item in localVault.login) {
                if (item.dateModified != null) localLoginTimestamps.add(item.dateModified)
            }
        }

        if (serverVault.login != null) {
            for (item in serverVault.login) {
                if (item.dateModified != null) serverLoginTimestamps.add(item.dateModified)
            }
        }

        if (localVault.note != null) {
            for (item in localVault.note) {
                if (item.dateModified != null) localNoteTimestamps.add(item.dateModified)
            }
        }

        if (serverVault.note != null) {
            for (item in serverVault.note) {
                if (item.dateModified != null) serverNoteTimestamps.add(item.dateModified)
            }
        }

        if (localVault.card != null) {
            for (item in localVault.card) {
                if (item.dateModified != null) localCardTimestamps.add(item.dateModified)
            }
        }
        if (serverVault.card != null) {
            for (item in serverVault.card) {
                if (item.dateModified != null) serverCardTimestamps.add(item.dateModified)
            }
        }

        var localVaultVersion: String = localVault.version
        var serverVaultVersion: String = serverVault.version

        var localEmail: String = localVault.version
        var serverEmail: String = serverVault.version

        if ((localEmail != serverEmail)) {
            isModified = true
        }

        if ((localVaultVersion != serverVaultVersion)) {
            isModified = true
        }

        try {
            if (serverTagTimestamps.max() > localTagTimestamps.max()
                || (serverTagTimestamps.max() < localTagTimestamps.max())
                || serverTagTimestamps.size != localTagTimestamps.size
            ) { // Prioritize server vault
                isModified = true
            }

        } catch (noTags: Exception) {
            //isModified = false
            if (serverTagTimestamps.size != localTagTimestamps.size) isModified = true
        }

        try {
            if (
                serverLoginTimestamps.max() > localLoginTimestamps.max()
                || (serverLoginTimestamps.max() < localLoginTimestamps.max())
                || serverLoginTimestamps.size != localLoginTimestamps.size
            ) { // Prioritize server vault
                isModified = true
            }

        } catch (noLogins: Exception) {
            //isModified = false
            if (serverLoginTimestamps.size != localLoginTimestamps.size) isModified = true
        }

        try {
            if (
                serverNoteTimestamps.max() > localNoteTimestamps.max()
                || (serverNoteTimestamps.max() < localNoteTimestamps.max())
                || serverNoteTimestamps.size != localNoteTimestamps.size
            ) { // Prioritize server vault
                isModified = true
            }

        } catch (noNotes: Exception) {
            //isModified = false
            if (serverNoteTimestamps.size != localNoteTimestamps.size) isModified = true
        }

        try {
            if (serverCardTimestamps.max() > localCardTimestamps.max()
                || (serverCardTimestamps.max() < localCardTimestamps.max())
                || serverCardTimestamps.size != localCardTimestamps.size
            ) { // Prioritize server vault
                isModified = true
            }

        } catch (noCards: Exception) {
            //isModified = false
            if (serverCardTimestamps.size != localCardTimestamps.size) isModified = true
        }

        if (serverLoginTimestamps.isEmpty() && localLoginTimestamps.isNotEmpty()) {
            isModified = true
        }

        if (serverNoteTimestamps.isEmpty() && localNoteTimestamps.isNotEmpty()) {
            isModified = true
        }

        if (serverCardTimestamps.isEmpty() && localCardTimestamps.isNotEmpty()) {
            isModified = true
        }

        /*
        Log.d("KeyspaceVaultSync", "Server KFS Latest: $serverTagTimestamps")
        Log.d("KeyspaceVaultSync", "Local KFS Latest:  $localTagTimestamps")
        Log.d("KeyspaceVaultSync", "Server KFS Latest: $serverLoginTimestamps")
        Log.d("KeyspaceVaultSync", "Local KFS Latest:  $localLoginTimestamps")
        Log.d("KeyspaceVaultSync", "Server KFS Latest: $serverNoteTimestamps")
        Log.d("KeyspaceVaultSync", "Local KFS Latest:  $localNoteTimestamps")
        Log.d("KeyspaceVaultSync", "Server KFS Latest: $serverCardTimestamps")
        Log.d("KeyspaceVaultSync", "Local KFS Latest:  $localCardTimestamps")
        */

        return isModified

    }

    fun getNotes (vault: Vault): MutableList<Note> {
        return vault.note!!
    }

    fun getNote (id: String, vault: Vault): Note? {
        val notes: MutableList<Note> = getNotes (vault)
        var matchedNote: Note? = null

        for (note in notes) if (note.id == id) matchedNote = note

        return matchedNote
    }

    fun decryptNote (note: Note): Note {
        return Note (
            id = note.id,
            organizationId = note.organizationId,
            type = note.type,
            notes = try { crypto.kfsDecrypt (note.notes, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) {null } as String?,
            color = try { crypto.kfsDecrypt (note.color, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) {null } as String?,
            favorite = note.favorite,
            tagId = note.tagId,
            dateCreated = note.dateCreated,
            dateModified = note.dateModified,
            frequencyAccessed = note.frequencyAccessed
        )
    }

    fun getCards (vault: Vault): MutableList<Card> {
        return vault.card!!
    }

    fun getCard (id: String, vault: Vault): Card? {
        val cards: MutableList<Card> = getCards (vault)
        var matchedCard: Card? = null

        for (card in cards) if (card.id == id) matchedCard = card

        return matchedCard
    }

    fun decryptCard (card: Card): Card {

        fun customFields (): MutableList<CustomField> {
            val customFields: MutableList<CustomField> = mutableListOf()
            for (field in card.customFields!!) {
                customFields.add (
                    CustomField (
                        name = crypto.kfsDecrypt (field.name, keyring.XCHACHA_POLY1305_KEY!!)!!,
                        value = crypto.kfsDecrypt (field.value, keyring.XCHACHA_POLY1305_KEY!!)!!,
                        hidden = field.hidden
                    )
                )
            }
            return customFields
        }

        return Card (
            id = card.id,
            organizationId = card.organizationId,
            type = card.type,
            name = try {crypto.kfsDecrypt (card.name, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?,
            notes = try {crypto.kfsDecrypt (card.notes, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null} as String?,
            color = try {crypto.kfsDecrypt (card.color, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null} as String?,
            cardNumber = try {crypto.kfsDecrypt (card.cardNumber, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null} as String?,
            cardholderName = try {crypto.kfsDecrypt (card.cardholderName, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { } as String?,
            expiry = try {crypto.kfsDecrypt (card.expiry, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) {null } as String?,
            pin = try {crypto.kfsDecrypt (card.pin.toString(), keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null} as String?,
            securityCode = try {crypto.kfsDecrypt (card.securityCode.toString(), keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null} as String?,
            favorite = card.favorite,
            tagId = card.tagId,
            dateCreated = card.dateCreated,
            dateModified = card.dateModified,
            frequencyAccessed = card.frequencyAccessed,
            customFields = customFields(),
            rfid = card.rfid,
            iconFile = try { crypto.kfsDecrypt (card.iconFile, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?
        )
    }

    // writer functions

    fun writeVault (vault: Vault): Boolean {
        var success = false

        try {
            file.delete() // delete corrupt or invalid file just in case
            Log.d("Keyspace", "Overwriting any file at \"${file.absolutePath}\" and initializing it.")
            val outputStream = file.outputStream()

            outputStream.apply {
                write(mapper.writer().withDefaultPrettyPrinter().writeValueAsBytes(vault))
                flush()
                close()
            }

            success = true

            if (vault.version != applicationContext.getString(R.string.vault_version)) {
                Log.e("Keyspace", "Vault version mismatch. The vault at \"${file.absolutePath}\" is on version ${vault.version}, but version the app uses version ${applicationContext.getString(R.string.vault_version)}")
            }

        } catch (permissionError: NullPointerException) {
            Log.e("Keyspace", "File read failed.\n${permissionError.stackTraceToString()}")
        } catch (noFile: NoSuchFileException) {
            Log.e("Keyspace", "File write failed. Couldn't find any .$filenameExtension files\n${noFile.stackTraceToString()}")
        } catch (ioError: IOException) {
            Log.e("Keyspace", "File write failed. Did you grant Keyspace storage access permissions?\n${ioError.stackTraceToString()}")
        } catch (fsException: FileSystemException) {
            Log.e("Keyspace", "Empty .$filenameExtension file.\n${fsException.stackTraceToString()}")
        } catch (invalidJSON: JSONException) {
            Log.e("Keyspace", "Invalid JSON format detected.\n${invalidJSON.stackTraceToString()}")
        }

        return success

    }

    fun encryptLogin (login: Login): Login {

        fun passwordHistory (): MutableList<Password>? {
            val passwordHistory: MutableList<Password> = mutableListOf()

            if (login.loginData == null) {
                return null
            } else {
                if (login.loginData.passwordHistory == null) {
                    return null
                } else {
                    for (oldPassword in login.loginData.passwordHistory) {
                        passwordHistory.add (
                            Password (
                                password = crypto.kfsEncrypt (oldPassword.password, keyring.XCHACHA_POLY1305_KEY!!)!!,
                                created = oldPassword.created
                            )
                        )
                    }
                }
            }

            return passwordHistory
        }

        fun backupCodes (): MutableList<String>? {
            val backupCodes: MutableList<String> = mutableListOf()
            if (login.loginData == null) {
                return null
            } else {
                if (login.loginData.totp != null) {
                    if (login.loginData.totp.backupCodes == null) {
                        return null
                    } else {
                        for (code in login.loginData.totp.backupCodes) backupCodes.add (crypto.kfsEncrypt (code, keyring.XCHACHA_POLY1305_KEY!!)!!)
                    }
                }
            }
            return backupCodes
        }

        fun siteUrls (): MutableList<String>? {
            val siteUrls: MutableList<String> = mutableListOf()
            if (login.loginData == null) {
                return null
            } else {
                if (login.loginData.siteUrls == null) {
                    return null
                } else {
                    for (site in login.loginData.siteUrls) siteUrls.add (crypto.kfsEncrypt (site, keyring.XCHACHA_POLY1305_KEY!!)!!)
                }
            }

            return siteUrls
        }

        fun customFields (): MutableList<CustomField>? {
            val customFields: MutableList<CustomField> = mutableListOf()
            if (login.loginData == null) {
                return null
            } else {
                if (login.customFields == null) {
                    return null
                } else {
                    for (field in login.customFields) {
                        customFields.add (
                            CustomField (
                                name = crypto.kfsEncrypt (field.name, keyring.XCHACHA_POLY1305_KEY!!)!!,
                                value = crypto.kfsEncrypt (field.value, keyring.XCHACHA_POLY1305_KEY!!)!!,
                                hidden = field.hidden
                            )
                        )
                    }
                }
            }

            return customFields
        }

        return Login (
            id = login.id,
            organizationId = login.organizationId,
            type = login.type,
            name = try {  crypto.kfsEncrypt (login.name, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            notes = try { crypto.kfsEncrypt (login.notes, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            favorite = login.favorite,
            tagId = login.tagId,
            loginData = LoginData (
                username = try { crypto.kfsEncrypt (login.loginData?.username, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
                password = try { crypto.kfsEncrypt (login.loginData?.password, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
                passwordHistory = passwordHistory(),
                email = try { crypto.kfsEncrypt (login.loginData?.email, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
                totp = Totp (
                    secret = try { crypto.kfsEncrypt (login.loginData?.totp?.secret, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
                    backupCodes = backupCodes()
                ),
                siteUrls = siteUrls()
            ),
            dateCreated = login.dateCreated,
            dateModified = login.dateModified,
            frequencyAccessed = login.frequencyAccessed,
            customFields = customFields(),
            iconFile = try { crypto.kfsEncrypt (login.iconFile, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?
        )

    }

    fun encryptNote (note: Note): Note {
        return Note (
            id = note.id,
            organizationId = note.organizationId,
            type = note.type,
            notes = try { crypto.kfsEncrypt (note.notes, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            color = try { crypto.kfsEncrypt (note.color, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            favorite = note.favorite,
            tagId = note.tagId,
            dateCreated = note.dateCreated,
            dateModified = note.dateModified,
            frequencyAccessed = note.frequencyAccessed
        )
    }

    fun encryptCard (card: Card): Card {

        fun customFields (): MutableList<CustomField> {
            val customFields: MutableList<CustomField> = mutableListOf()
            for (field in card.customFields!!) {
                customFields.add (
                    CustomField (
                        name = try { crypto.kfsEncrypt (field.name, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
                        value = crypto.kfsEncrypt (field.value, keyring.XCHACHA_POLY1305_KEY!!)!!,
                        hidden = field.hidden
                    )
                )
            }
            return customFields
        }

        return Card (
            id = card.id,
            organizationId = card.organizationId,
            type = card.type,
            name = try { crypto.kfsEncrypt (card.name, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            notes = try { crypto.kfsEncrypt (card.notes, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            color = try { crypto.kfsEncrypt (card.color, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            cardNumber = try { crypto.kfsEncrypt (card.cardNumber, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            cardholderName = try { crypto.kfsEncrypt (card.cardholderName, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            expiry = try { crypto.kfsEncrypt (card.expiry, keyring.XCHACHA_POLY1305_KEY!!) } catch (_: Exception) { null },
            pin = try { crypto.kfsEncrypt (card.pin.toString(), keyring.XCHACHA_POLY1305_KEY!!)  } catch (_: Exception) { null },
            securityCode = try { crypto.kfsEncrypt (card.securityCode.toString(), keyring.XCHACHA_POLY1305_KEY!!)  } catch (_: Exception) { null },
            favorite = card.favorite,
            tagId = card.tagId,
            dateCreated = card.dateCreated,
            dateModified = card.dateModified,
            frequencyAccessed = card.frequencyAccessed,
            customFields = customFields(),
            rfid = card.rfid,
            iconFile = try { crypto.kfsEncrypt (card.iconFile, keyring.XCHACHA_POLY1305_KEY!!)} catch (_: Exception) { null } as String?
        )
    }

    fun deleteItem (id: String, vault: Vault): Vault {

        val tags = getTags(vault)
        val logins = getLogins(vault)
        val notes = getNotes(vault)
        val cards = getCards(vault)

        for (tag in tags) if (tag.id == id) {
            tags.remove (tag)

            return Vault (
                version = vault.version,
                tag = tags,
                login = logins,
                note = notes,
                card = cards
            )

        }

        for (login in logins) if (login.id == id) {
            logins.remove (login)

            return Vault (
                version = vault.version,
                tag = tags,
                login = logins,
                note = notes,
                card = cards
            )

        }

        for (note in notes) if (note.id == id) {
            notes.remove (note)

            return Vault (
                version = vault.version,
                tag = tags,
                login = logins,
                note = notes,
                card = cards
            )

        }

        for (card in cards) if (card.id == id) {
            cards.remove (card)

            return Vault (
                version = vault.version,
                tag = tags,
                login = logins,
                note = notes,
                card = cards
            )

        }

        return Vault (
            version = vault.version,
            tag = tags,
            login = logins,
            note = notes,
            card = cards
        )

    }

}