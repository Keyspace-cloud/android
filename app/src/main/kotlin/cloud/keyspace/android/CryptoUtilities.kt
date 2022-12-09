package cloud.keyspace.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toEntropy
import cash.z.ecc.android.bip39.toSeed
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.KeyDerivation
import com.goterl.lazysodium.interfaces.KeyExchange
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.HexMessageEncoder
import com.goterl.lazysodium.utils.Key
import com.sun.jna.NativeLong
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.spec.InvalidKeySpecException
import java.util.*
import java.util.concurrent.Executor


/**
 * Keyspace's cryptographic utilities. Things such as mnemonic generation, key provisioning, KeyspaceFS and Keyring management can be found here,.
 * @param applicationContext The context of the activity that a `CryptoUtilities` object is initialized in, example: `applicationContext`, `this` etc.
 * @param appCompatActivity The current activity this function is called in. This is the `AppCompatActivity` that BiometricPrompt and Android Keystore will use. Use `this` if unsure.
 */
class CryptoUtilities(
    applicationContext: Context,  // The context to derive information from.
    appCompatActivity: AppCompatActivity, // The activity to display the prompt inside of.
) { // constructor without init {}

    private var context: Context = applicationContext
    var activity: AppCompatActivity = appCompatActivity
    val utils = MiscUtilities(applicationContext)
    val sodium = SodiumAndroid()
    val lazySodium = LazySodiumAndroid(sodium, StandardCharsets.UTF_8)

    val XCHACHA_POLY1305_KEY_ALIAS = "keyspace_xchacha_poly1305_key"
    val ED25519_PUBLIC_KEY_ALIAS = "keyspace_ed25519_public"
    val ED25519_PRIVATE_KEY_ALIAS = "keyspace_ed25519_private"
    val LOGIN_TOKEN_ALIAS = "keyspace_login_token"
    val KEYRING_NAME = MasterKey.DEFAULT_MASTER_KEY_ALIAS

    private var XCHACHA_POLY1305_NONCE_BYTES = 24
    var DEFAULT_AUTHENTICATION_DELAY = 30

    fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    fun String.fromB64ToUtf8(): String {
        return android.util.Base64.decode(this, android.util.Base64.DEFAULT).toString(StandardCharsets.UTF_8)
    }

    fun String.fromUtf8ToB64(): String {
        return android.util.Base64.encodeToString(this.toByteArray(StandardCharsets.UTF_8), android.util.Base64.DEFAULT)
    }

    data class Keyring (
        val XCHACHA_POLY1305_KEY: ByteArray?,
        val ED25519_PUBLIC_KEY: ByteArray?,
        val ED25519_PRIVATE_KEY: ByteArray?,
        // val LOGIN_TOKEN: ByteArray?,
    )

    data class Bip39 (
        val words: CharArray?,
        val seed: ByteArray?,
    )

    /**
     * Generate 12 mnemonic words using the Kotlin-bip39 library
     *
     * @param password The user password as a String to use as seed.
     * @return A map with `words` and `seed` entries containing the 12 words separated by a hyphen (-) and the seed as a hexadecimal string respectively
     * @see <a href="https://github.com/zcash/kotlin-bip39">Kotlin-bip39 on GitHub</a>
     */

    fun bip39 (password: CharArray): Bip39 {
        val entropy: ByteArray = Mnemonics.WordCount.COUNT_12.toEntropy()
        val mnemonicCode = Mnemonics.MnemonicCode(entropy)
        mnemonicCode.validate()

        val seed = mnemonicCode.toSeed(password)
        //password.fill('0')

        val words: MutableList<String> = mutableListOf()
        for (word in mnemonicCode) {
            words.add(word)}
        val trimmedWords = words.joinToString(" ").toCharArray()

        //Log.d("RAW BIP39 SEED", String(seed))
        //Log.d("RAW BIP39 SEED LENGTH", seed.size.toString())
        mnemonicCode.clear()

        return Bip39 (
            words = trimmedWords,
            seed = seed
        )
    }

    //// -------------------------------------------|
    //// GENERIC HASH WITH SALT
    //// -------------------------------------------|
    private fun cryptoGenericHashBlake2bSaltPersonal (subKey: ByteArray, subKeyLen: Int, `in`: ByteArray, inLen: Long, masterKey: ByteArray, masterKeyLen: Int, subKeyId: ByteArray, context: ByteArray): Int {
        require(!(inLen < 0 || inLen > `in`.size)) { "inLen out of bounds: $inLen" }
        require(!(subKeyLen < 0 || subKeyLen > subKey.size)) { "outLen out of bounds: $subKeyLen" }
        require(!(masterKeyLen < 0 || masterKeyLen > masterKey.size)) { "outLen out of bounds: $masterKeyLen" }
        return lazySodium
                .sodium!!
                .crypto_generichash_blake2b_salt_personal (
                    subKey,
                    subKeyLen,
                    `in`,
                    inLen,
                    masterKey,
                    masterKeyLen,
                    subKeyId,
                    context
                )
    }

    val ROOT_SIGNED_SEED_CONTEXT = "__rootSignSeed__".toByteArray(StandardCharsets.US_ASCII)
    val VAULT_KEY_CONTEXT = "____vaultKey____".toByteArray(StandardCharsets.US_ASCII)

    val ROOT_SIGNED_SEED_KEY_ID = byteArrayOf (
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 1
    )

    val VAULT_KEY_KEY_ID = byteArrayOf (
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 2
    )

    fun kdf (masterKey: ByteArray, context: ByteArray, keyId: ByteArray): ByteArray? {
        val outputKeySize = KeyDerivation.MASTER_KEY_BYTES
        val outputKey = ByteArray(outputKeySize)

        val kdfStatusCode: Int = cryptoGenericHashBlake2bSaltPersonal (
            outputKey, outputKeySize,
            ByteArray(0), 0,
            masterKey, masterKey.size,
            keyId, context
        )

        return if (kdfStatusCode == 0) outputKey else null
    }

    fun wordsToSeed (spacedWords: CharArray, passphrase: CharArray): ByteArray? {
        lateinit var seed: ByteArray
        try {
            seed = Mnemonics.MnemonicCode(spacedWords).toSeed(passphrase)
        } catch (incorrectMnemonic: Mnemonics.ChecksumException) {
            Log.e("KEYSPACE", "Incorrect bip39 mnemonics. One or more words you entered aren't in the right order. Check spelling and try again.")
            incorrectMnemonic.printStackTrace()
        }
        return seed
    }

    /**
     * Generate a SHA256 hash of a string, in this case - of the 12 mnemonic words generated by bip39.
     *
     * @param words A string containing the 12 words separated by a hyphen (-)
     * @return A hash of the 12 hyphenated words as a ByteArray
     * @see <a href="https://github.com/zcash/kotlin-bip39">Kotlin-bip39 on GitHub</a>
     */
    fun choppedSha256(words: CharArray): ByteArray {
        val hashOutput = lazySodium.cryptoHashSha256(String(words))
        val choppedHash = hashOutput.takeLast(hashOutput.length / 2)
        return HexMessageEncoder().decode(choppedHash) // chopped hash as ByteArray
    }

    /**
     * Generate an Argon2i hash using LazySodium to use as hardened seed or xChaCha key
     *
     * @param password A password as a String.
     * - To generate an ed25519 keypair, use the seed of the bip39 words.
     * - To generate an AES key, use the hyphenated bip39 words themselves
     * @param salt The last 16 bytes of a SHA256 salt of the 12 word hyphenated mnemonic as a ByteArray.
     * @return A hash of the password + salt as a ByteArray
     * @see <a href="https://github.com/terl/lazysodium-android">LazySodium on GitHub</a>
     * @see <a href="https://doc.libsodium.org/">LibSodium documentation</a>
     */
    fun argon2i (password: CharArray, salt: ByteArray): ByteArray {
        var stringifiedPassword = String(password)
        val hash = lazySodium.cryptoPwHash (
            stringifiedPassword,
            32,
            salt,
            8, // updated on Oct 25, 2022 at 22:10 IST
            NativeLong(128000), // if using KiB, convert to kB and cast to NativeLong (due to LibSodium using C / NDK)
            PwHash.Alg.PWHASH_ALG_ARGON2I13
        )

        //password.fill('0')
        stringifiedPassword.replaceRange(0, stringifiedPassword.length, "")
        System.gc()

        val byteArrayHash = HexMessageEncoder().decode(hash)

        return byteArrayHash
    }

    /**
     * Generate an ed25519 keypair using LazySodium
     *
     * @param seed Argon2i hash as ByteArray
     * @return A map with `public` and `private` entries containing the public and private ed25519 keys as hex strings
     * @see <a href="https://github.com/terl/lazysodium-android">LazySodium on GitHub</a>
     * @see <a href="https://doc.libsodium.org/">LibSodium documentation</a>
     */
    fun ed25519Keypair (seed: ByteArray): Ed25519Keypair {
        val keypair = lazySodium.cryptoSignSeedKeypair(seed)
        return Ed25519Keypair (
            publicKey = keypair.publicKey.asBytes,
            privateKey = keypair.secretKey.asBytes
        )
    }

    /**
     * Sign data using LazySodium's cryptoSign (combined mode)
     *
     * @param data The string to be signed using edDSA
     * @param privateKey The ed25519 private/secret key as a hex string (128 characters long / 256B)
     * @return Signed hexadecimal data as UTF-8 string.
     * @see <a href="https://github.com/terl/lazysodium-android">LazySodium on GitHub</a>
     * @see <a href="https://doc.libsodium.org/">LibSodium documentation</a>
     */
    fun sign (data: String, privateKey: ByteArray): String {
        val ed25519SecretKey = Key.fromBytes(privateKey)
        return lazySodium.cryptoSign (data, ed25519SecretKey)
    }

    /**
     * Verify data using LazySodium's cryptoSignVerifyDetached
     *
     * @param data The string to be signed using edDSA
     * @param publicKey The ed25519 public key as a hex string (128 characters long / 256B)
     * @return `true` if message was signed with the right private key and not tampered with, or else `false`.
     * @see <a href="https://github.com/terl/lazysodium-android">LazySodium on GitHub</a>
     * @see <a href="https://doc.libsodium.org/">LibSodium documentation</a>
     */
    fun verify (signature: String, data: String, publicKey: ByteArray): Boolean {
        val ed25519PublicKey = Key.fromBytes(publicKey)
        return lazySodium.cryptoSignVerifyDetached (signature, data, ed25519PublicKey)
    }

    /**
     * Display a BiometricPrompt. Note: The BiometricPrompt runs on a separate Executor thread.
     *
     * @param title The prompt's title
     * @param subtitle The prompt's subtitle
     * @param description The prompt's description (Android 8.0+)
     * @return `true` if successfully authenticated, `false` by default.
     */
    fun authenticate (
        title: String, // Title of the prompt, could be app name.
        subtitle: String, // Subtitle of the prompt, could be action to be performed.
        description: String, // Description of the prompt, could be reason for the prompt.
    ): Boolean {
        var authenticationResult = false // don't allow successful authentication by default
        val executor: Executor = ContextCompat.getMainExecutor(activity) // execute on different thread awaiting response
        try {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)

            if (canAuthenticate == BIOMETRIC_SUCCESS) {
                Log.d("Keyspace", "Device lock found")
            } else {
                Log.d("Keyspace", "Device lock not set")
                throw NoSuchMethodError()
            }

            val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { // Authentication succeeded
                    authenticationResult = true
                    Log.d("Keyspace", "Authentication successful")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { // Authentication error. Verify error code and message
                    Log.d("Keyspace", "Authentication canceled")
                    AlertDialog.Builder(activity)
                        .setTitle("Authentication failure")
                        .setMessage("Android needs your biometrics or credentials to retrieve your data from the Keystore. Please use biometrics or enter your PIN, pattern or password.")
                        .setCancelable(false)
                        .setNegativeButton("Exit"){ _, _ ->
                            activity.finish()
                        }
                    .show()
                }

                override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                    Log.d("Keyspace", "Incorrect credentials supplied")
                }
            }

            val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setDescription(description)
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            builder.setConfirmationRequired(true)

            val promptInfo = builder.build()
            val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)
            biometricPrompt.authenticate(promptInfo)

        } catch (badPrompt: IllegalArgumentException) {
            Log.e("Keyspace", "Wrong context or activity passed to function.")
            badPrompt.stackTrace
        } catch (wrongActivity: ClassCastException) {
            Log.e("Keyspace", "Wrong activity passed to function.")
            wrongActivity.stackTrace
        } catch (noLockSet: NoSuchMethodError) {
            Log.e("Keyspace", "Please set a screen lock.")
            noLockSet.stackTrace
        } catch (incorrectCredentials: Exception) {
            Log.e("Keyspace", "Your identity could not be verified.")
            incorrectCredentials.stackTrace
        }

        return authenticationResult
    }

    /**
     * Display a BiometricPrompt and start an activity if successful. Note: The BiometricPrompt runs on a separate Executor thread.
     *
     * @param nextActivityIntent The intent of the next `AppCompatActivity` to start.
     * @param title The prompt's title
     * @param subtitle The prompt's subtitle
     * @param description The prompt's description (Android 8.0+)
     * @return `true` if successfully authenticated, `false` by default.
     */
    fun authenticateAndStartActivity(
        nextActivityIntent: Intent, // The activity to start upon successful authentication.
        title: String, // Title of the prompt, could be app name.
        subtitle: String, // Subtitle of the prompt, could be action to be performed.
        description: String, // Description of the prompt, could be reason for the prompt.
    ): Boolean {

        var authenticationResult = false // don't allow successful authentication by default
        val executor: Executor = ContextCompat.getMainExecutor(activity) // execute on different thread awaiting response
        try {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)

            if (canAuthenticate == BIOMETRIC_SUCCESS) {
                Log.d("Keyspace", "Device lock found")
            } else {
                Log.d("Keyspace", "Device lock not set")
                throw NoSuchMethodError()
            }

            val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { // Authentication succeeded
                    authenticationResult = true
                    Log.d("Keyspace", "Authentication successful")
                    nextActivityIntent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(nextActivityIntent)
                    activity.finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { // Authentication error. Verify error code and message
                    Log.d("Keyspace", "Authentication canceled")
                }

                override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                    Log.d("Keyspace", "Incorrect credentials supplied")
                }
            }

            val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setDescription(description)
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            builder.setConfirmationRequired(true)

            val promptInfo = builder.build()
            val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)
            biometricPrompt.authenticate(promptInfo)

        } catch (badPrompt: IllegalArgumentException) {
            Log.e("Keyspace", "Wrong context or activity passed to function.")
            badPrompt.stackTrace
        } catch (wrongActivity: ClassCastException) {
            Log.e("Keyspace", "Wrong activity passed to function.")
            wrongActivity.stackTrace
        } catch (noLockSet: NoSuchMethodError) {
            Log.e("Keyspace", "Please set a screen lock.")
            noLockSet.stackTrace
        } catch (incorrectCredentials: Exception) {
            Log.e("Keyspace", "Your identity could not be verified.")
            incorrectCredentials.stackTrace
        }

        return authenticationResult
    }

    /**
     * Display a BiometricPrompt and start a method/function if successful. Note: The BiometricPrompt runs on a separate Executor thread.
     *
     * @param method The method to start if authentication is successful
     * @param title The prompt's title
     * @param subtitle The prompt's subtitle
     * @param description The prompt's description (Android 8.0+)
     * @return `true` if successfully authenticated, `false` by default.
     */
    fun authenticateAndRun (
        function: () -> (Unit), // The function to be invoked post authentication success
        title: String, // Title of the prompt, could be app name.
        subtitle: String, // Subtitle of the prompt, could be action to be performed.
        description: String, // Description of the prompt, could be reason for the prompt.
    ): Boolean {

        var authenticationResult = false // don't allow successful authentication by default
        val executor: Executor = ContextCompat.getMainExecutor(activity) // execute on different thread awaiting response
        try {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)

            if (canAuthenticate == BIOMETRIC_SUCCESS) {
                Log.d("Keyspace", "Device lock found")
            } else {
                Log.d("Keyspace", "Device lock not set")
                throw NoSuchMethodError()
            }

            val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { // Authentication succeeded
                    authenticationResult = true
                    Log.d("Keyspace", "Authentication successful")
                    function()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { // Authentication error. Verify error code and message
                    Log.d("Keyspace", "Authentication canceled")
                }

                override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                    Log.d("Keyspace", "Incorrect credentials supplied")
                }
            }

            val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setDescription(description)
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            builder.setConfirmationRequired(true)

            val promptInfo = builder.build()
            val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)
            biometricPrompt.authenticate(promptInfo)

        } catch (badPrompt: IllegalArgumentException) {
            Log.e("Keyspace", "Wrong context or activity passed to function.")
            badPrompt.stackTrace
        } catch (wrongActivity: ClassCastException) {
            Log.e("Keyspace", "Wrong activity passed to function.")
            wrongActivity.stackTrace
        } catch (noLockSet: NoSuchMethodError) {
            Log.e("Keyspace", "Please set a screen lock.")
            noLockSet.stackTrace
        } catch (incorrectCredentials: Exception) {
            Log.e("Keyspace", "Your identity could not be verified.")
            incorrectCredentials.stackTrace
        }

        return authenticationResult
    }

    /**
     * Gets the device master key to encrypt and decrypt the custom Keyspace Keyring containing all custom.
     *
     * * On Android P devices containing a Strongbox chip, this device master key is stored inside an enclave in the CPU and cannot be used unless the device is unlocked.
     * * On devices containing a fingerprint sensor, this device master key is stored in the app container and is released only when the TPM (biometric chip) instructs it to.
     * * On poor peasants' devices, this device master key is stored in the app container and is released only when Keyguard tells Android the user was authenticated.
     *
     * If the app crashes, it's because the Keystore hasn't gotten an authentication signal from Android and will throw a "User not authenticated error".
     * To fix this, either
     *  1. implement a BiometricPrompt shortly before accessing the keys (preferably in an activity), or
     *  2. reinstall the app, then lock and unlock your phone once and quickly use the function.
     * @return `alias` string of device device master key that is used to encrypt and decrypt the Keyring
     * @see <a href="http://web.archive.org/web/20210226035204/https://security.googleblog.com/2020/02/data-encryption-on-android-with-jetpack.html">Data Encryption on Android with Jetpack Security</a>
     * @see <a href="http://android-doc.github.io/reference/android/security/keystore/KeyGenParameterSpec.html">KeyGenParameterSpec</a>
     */
    fun getKeystoreMasterKey(): MasterKey {
        val keySpecifications = KeyGenParameterSpec.Builder (
            KEYRING_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes (KeyProperties.BLOCK_MODE_GCM)
            setKeySize (MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
            setEncryptionPaddings (KeyProperties.ENCRYPTION_PADDING_NONE)
            setRandomizedEncryptionRequired (true)  // produces unique ciphertext for the same plain text input for extra security from cryptanalysis.
            setUserAuthenticationRequired (true) // will not release keys unless BiometricPrompt or Keyguard succeeds at least once.
            // setInvalidatedByBiometricEnrollment (true)  // invalidate existing keys after new biometric enrolment.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // setUserConfirmationRequired(true)  // always require users to verify via ConfirmationPrompt
                // setUserAuthenticationValidWhileOnBody(true)  // works only when the device is in the user's hand
                setUnlockedDeviceRequired (true)  // cannot work on locked devices
                if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if strongbox / hardware keystore exists
                    Log.d("Keyspace", "Strongbox Keystore (Trusted Execution Environment) detected.")
                    setIsStrongBoxBacked(true)  // use Strongbox / hardware keystore / Titan chip
                } else if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE)) {
                    Log.d("Keyspace", "Hardware Abstraction Layer (HAL) Keystore implementation detected.")
                } else {
                    Log.d("Keyspace", "Legacy software Keystore implementation detected.")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Ask for authentication when loading keys, must be larger than 0
                setUserAuthenticationParameters(DEFAULT_AUTHENTICATION_DELAY, KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                setUserAuthenticationValidityDurationSeconds(DEFAULT_AUTHENTICATION_DELAY)
            }

        }.build()

        val advancedKeyAlias = MasterKey.Builder(activity).apply {
            setKeyGenParameterSpec(keySpecifications)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if strongbox / hardware keystore exists
                    Log.d("Keyspace", "Accessing Strongbox (TEE) Keystore...")
                    setRequestStrongBoxBacked(true) // use Strongbox / hardware keystore / Titan chip
                } else if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE)) {
                    Log.d("Keyspace", "Accessing hardware-backed HAL Keystore...")
                } else {
                    Log.d("Keyspace", "Accessing software Keystore container...")
                }
            }
        }.build()

        Log.d("Keyspace", "Attempting to access $KEYRING_NAME from " +
                if (advancedKeyAlias.isStrongBoxBacked) "Strongbox"
                else if (advancedKeyAlias.isKeyStoreBacked)  "HAL Keystore"
                else "Software Keystore"
        )

        return advancedKeyAlias
    }

    /**
     * Store encryption / decryption keys from Keyspace's own key management solution, called the "Keyring" with the Android Keystore.
     * If the app crashes, it's because the Keystore couldn't get an authentication signal from Android and will throw a "User not authenticated error".
     *
     * To fix this, either:
     * 1. implement a BiometricPrompt shortly before accessing the keys (preferably in an activity), or
     * 2. reinstall the app, then lock and unlock your phone once and quickly use the function.
     *
     * @param  alias name of the key to be stored, can be something simple like "xchacha-key". Duplicate aliases are always overwritten. This is the alias within the Keyring, NOT the Android Keystore.
     * @param  key the actual key to be stored, as a UTF-8 string. To convert to and from ByteArray, use a cryptographically safe library like LazySodium.
     * @return `true` if the key was stored successfully, `false` if errors occured while storing keys (see Logcat for verbose information)
     * @see <a href="https://security.googleblog.com/2020/02/data-encryption-on-android-with-jetpack.html">Data Encryption on Android with Jetpack Security </a>
     */
    @SuppressLint("ApplySharedPref")
    private fun storeKey (alias: String, key: ByteArray, masterKey: MasterKey): Boolean {

        // var masterKey: MasterKey? = getKeystoreMasterKey()
        var _masterKey: MasterKey = masterKey

        var similarKeyName: String? = null

        try {

            Log.d("Keyspace", "Key length = " + key.size.toString() + ", Key mod 8 = " + (key.size % 8).toString())

            if (alias.lowercase().contains("token")) {
                if (
                    key.size % 2 != 0 || // if token length is a valid byte unit
                    (key.size * 2) != 40 // if token is long enough to qualify as key, 128-bit (64 Byte) key is minimum standard.
                ) { throw InvalidKeySpecException() }
            } else {
                if (
                    key.size % 2 != 0 || // if key length is a valid byte unit
                    (key.size * 2) < 64 // if key is long enough to qualify as key, 128-bit (64 Byte) key is minimum standard.
                ) { throw InvalidKeySpecException() }
            }

            val keyring = EncryptedSharedPreferences.create( // create keyring object in memory
                context,
                KEYRING_NAME,
                _masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val stringifiedKey = key.toHexString()
            var keys: MutableSet<String>? = keyring.all.keys

            if (keys!!.isEmpty()) { // if no keys exist
                Log.d("Keyspace", "Keyring empty. Adding new key \"$alias\".")
                keyring.edit() // Store keys in EncryptedSharedPreferences
                    .putString(alias, stringifiedKey)
                    .commit() // Store key immediately via commit() for security reasons
                Log.d("Keyspace", "Added key \"$alias\".")

                stringifiedKey.replaceRange(0, stringifiedKey.length, "")
                keys.forEach { it.replaceRange(0, it.length, "") }
                keys = null
                key.fill (0) // wipe array data for extra security
                System.gc()

                return true

            } else { // if other keys exist
                val miscUtils = MiscUtilities (context)
                Log.d("Keyspace", "Keys available: $keys")
                for (keyName in keys!!) {
                    if (miscUtils.areSimilar(keyName, alias)) {
                        similarKeyName = keyName

                        stringifiedKey.replaceRange(0, stringifiedKey.length, "")
                        keys.forEach { it.replaceRange(0, it.length, "") }
                        keys = null
                        key.fill (0) // wipe array data for extra security
                        System.gc()

                        throw IllegalArgumentException()
                    } else {
                        Log.d("Keyspace", "Adding new key \"$alias\".")
                        keyring.edit() // Store keys in EncryptedSharedPreferences
                            .putString(alias, stringifiedKey)
                            .commit() // Store key immediately via commit() for security reasons
                        Log.d("Keyspace", "Added key \"$alias\".")

                        stringifiedKey.replaceRange(0, stringifiedKey.length, "")
                        keys.forEach { it.replaceRange(0, it.length, "") }
                        keys = null
                        key.fill (0) // wipe array data for extra security
                        System.gc()

                        return true
                    }
                }
            }

            stringifiedKey.replaceRange(0, stringifiedKey.length, "")
            keys.forEach { it.replaceRange(0, it.length, "") }
            keys = null
            key.fill (0) // wipe array data for extra security
            System.gc()

        } catch (keyInvalid: InvalidKeySpecException) {
            Log.e("Keyspace", "Invalid key size. Please make sure your key is of a valid length, such as 128, 256, 512 (and so on...)")
            Log.e("Keyspace", "Keyring: " + keyInvalid.stackTraceToString())
            return false

        } catch (keyExists: IllegalArgumentException) {
            Log.e("Keyspace", "A similar key named \"$similarKeyName\" already exists. Please store it under a different name.")
            Log.e("Keyspace", "Keyring: " + keyExists.stackTraceToString())
            return false

        } catch (authenticationError: java.lang.Exception) {
            Log.e("Keyspace", "Android Keystore couldn't be accessed. Please lock and unlock your device and try again. If it was corrupted / wiped, reinstall the app and try again.")
            Log.e("Keyspace", "Keyring: " + authenticationError.stackTraceToString())
            return false
        }
        return false
    }

    fun storeKeys(
        keyring: Keyring,
        masterKey: MasterKey,
    ): Boolean {
        return try {
            if (keyring.ED25519_PRIVATE_KEY!!.isEmpty()) throw InvalidKeyException()
            if (keyring.ED25519_PUBLIC_KEY!!.isEmpty()) throw InvalidKeyException()
            if (keyring.XCHACHA_POLY1305_KEY!!.isEmpty()) throw InvalidKeyException()
            // if (keyring.LOGIN_TOKEN!!.isEmpty()) throw InvalidKeyException()
            storeKey (XCHACHA_POLY1305_KEY_ALIAS, keyring.XCHACHA_POLY1305_KEY, masterKey)
            storeKey (ED25519_PUBLIC_KEY_ALIAS, keyring.ED25519_PUBLIC_KEY, masterKey)
            storeKey (ED25519_PRIVATE_KEY_ALIAS, keyring.ED25519_PRIVATE_KEY, masterKey)
            // storeKey (LOGIN_TOKEN_ALIAS, keyring.LOGIN_TOKEN, masterKey)
            true
        } catch (badKeys: InvalidKeyException) {
            Log.e ("Keyspace", "Bad keyring supplied. Keys cannot be null or empty.")
            false
        }
    }

    /**
     * Retrieve encryption / decryption keys from Keyspace's own key management solution, called the "Keyring" with the Android Keystore.
     * If the app crashes, it's because the Keystore couldn't get an authentication signal from Android and will throw a "User not authenticated error".
     *
     * To fix this, either:
     * 1. implement a BiometricPrompt shortly before accessing the keys (preferably in an activity), or
     * 2. reinstall the app, then lock and unlock your phone once and quickly use the function.
     *
     * @param  alias name of the key to be stored, can be something simple like "xchacha-key". Duplicate aliases are always overwritten. This is the alias within the Keyring, NOT the Android Keystore.
     * @return key as `String` if found, `null` if no key with the alias was found.
     * @see <a href="https://security.googleblog.com/2020/02/data-encryption-on-android-with-jetpack.html">Data Encryption on Android with Jetpack Security </a>
     */
   private fun retrieveKey (alias: String, masterKey: MasterKey): ByteArray? {
        var encryptionKey: ByteArray? = null

        // var masterKey: MasterKey? = getKeystoreMasterKey()
        var _masterKey: MasterKey = masterKey

        try {
            val keyring = EncryptedSharedPreferences.create ( // create keyring object in memory
                context,
                KEYRING_NAME,
                _masterKey!!,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val keys: MutableSet<String> = keyring.all.keys

            for (key in keys) {
                if (key == alias) {
                    encryptionKey =  HexMessageEncoder().decode(keyring.getString(key, null)!!)// return null if no value for key exists
                    Log.d("Keyspace", "Found key named \"$alias\"")
                    break
                }
            }

            if (encryptionKey == null) {
                throw KeyException()
            }

        } catch (keyDoesNotExist: KeyException) {
            Log.e("Keyspace", "No such alias exists within the keyring.")
            Log.e("Keyspace", "Keyring: " + keyDoesNotExist.stackTraceToString())

        } catch (keystoreError: InvocationTargetException) {
            Log.e("Keyspace", "Android Keystore was corrupted / wiped. Please reinstall the application and try again.")
            Log.e("Keyspace", "Keyring: " + keystoreError.stackTraceToString())

        } catch (authenticationError: java.lang.Exception) {
            Log.e("Keyspace", "Android Keystore couldn't be accessed. Please lock and unlock your device and try again. If it was corrupted / wiped, reinstall the app and try again.")
            Log.e("Keyspace", "Keyring: " + authenticationError.stackTraceToString())
        }

        return encryptionKey
    }

    fun retrieveKeys (masterKey: MasterKey): Keyring? {
        var keyring: Keyring? = null
        try {

            keyring = Keyring (
                XCHACHA_POLY1305_KEY = retrieveKey(XCHACHA_POLY1305_KEY_ALIAS, masterKey),
                ED25519_PRIVATE_KEY = retrieveKey(ED25519_PRIVATE_KEY_ALIAS, masterKey),
                ED25519_PUBLIC_KEY = retrieveKey(ED25519_PUBLIC_KEY_ALIAS, masterKey),
                //LOGIN_TOKEN = retrieveKey(LOGIN_TOKEN_ALIAS, masterKey)
            )

            if (keyring.XCHACHA_POLY1305_KEY!!.isEmpty()) throw InvalidKeyException()
            if (keyring.ED25519_PUBLIC_KEY!!.isEmpty()) throw InvalidKeyException()
            if (keyring.ED25519_PRIVATE_KEY!!.isEmpty()) throw InvalidKeyException()
            // if (keyring.LOGIN_TOKEN!!.isEmpty()) throw InvalidKeyException()

        } catch (badKeys: InvalidKeyException) {
            Log.e ("Keyspace", "Couldn't find any valid keys in Keyring.")
        }

        return keyring
    }

    /**
     * Wipe all keys from Keyspace's Keyring as well as the master key to decrypt the Keyring from Android keystore.
     * If the app crashes, it's because the Keystore couldn't get an authentication signal from Android and will throw a "User not authenticated error".
     *
     * To fix this, either:
     * 1. implement a BiometricPrompt shortly before accessing the keys (preferably in an activity), or
     * 2. reinstall the app, then lock and unlock your phone once and quickly use the function.
     *
     * @return `true` if the key was stored successfully, `false` if errors occured while storing keys (see Logcat for verbose information)
     * @see <a href="https://security.googleblog.com/2020/02/data-encryption-on-android-with-jetpack.html">Data Encryption on Android with Jetpack Security </a>
     */
    @SuppressLint("ApplySharedPref")
    fun wipeAllKeys (): Boolean {

        try {
            val keyring = EncryptedSharedPreferences.create( // create keyring object in memory
                context,
                KEYRING_NAME,
                getKeystoreMasterKey(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            keyring.edit().clear().commit() // Store key immediately via commit() for security reasons
            context.deleteSharedPreferences(KEYRING_NAME) // Delete keyring file
            Log.d("Keyspace", "Wiped Keyring and deleted it.")

            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(KEYRING_NAME)

            // full flush just in case
            for (alias in keyStore.aliases().toList()) {
               keyStore.deleteEntry(alias)
               Log.d("Keyspace", "Found and deleted old keystore key named: ${alias}")
            }

            return true

        } catch (error: java.lang.Exception) {
            Log.e("Keyspace", "An error occurred while interacting with Keystore")
            Log.e("Keyspace", "Keyring: " + error.stackTraceToString())
            return false
        } catch (keystoreError: KeyStoreException) {
            Log.e("Keyspace", "Error while attempting to access Android Keystore. Please reinstall the application.")
            Log.e("Keyspace", "Keyring: " + keystoreError.stackTraceToString())
            return false
        }
    }

    data class EncryptedData(
        val ciphertext: ByteArray?,
        val nonce: ByteArray?,
    )
    /**
     * Encrypt a plaintext string with an xChaCha-Poly1305 private key in HexString ByteArray format
     *
     * @param  plaintext The plaintext string to be encrypted.
     * @param  xChaChaKeyAlias name of the key to be stored, can be something simple like "xchacha-key". Duplicate aliases are always overwritten. This is the alias within the Keyring, NOT the Android Keystore.
     * @return - Ciphertext as `String` with initialization vector (IV) / nonce appended to the front of ciphertext. Example: `nonce+ciphertext`
     *
     * - `null` if no key with the alias was found or errors occurred.
     */
    @SuppressLint("GetInstance")
    fun xChaChaEncrypt(plaintext: String?, xChaChaKey: ByteArray): EncryptedData {
        val ciphertext = ByteArray(SecretBox.XSALSA20POLY1305_MACBYTES + plaintext?.fromUtf8ToB64()?.toByteArray(StandardCharsets.UTF_8)!!.size)
        val nonce: ByteArray? = lazySodium.randomBytesBuf(XCHACHA_POLY1305_NONCE_BYTES) // set to null to debug

        try {
            val encrypt = lazySodium.cryptoAeadXChaCha20Poly1305IetfEncrypt (
                ciphertext,
                null,
                plaintext.fromUtf8ToB64().toByteArray(StandardCharsets.UTF_8),
                plaintext.fromUtf8ToB64().length.toLong(),
                null,
                0,
                null,
                nonce,
                xChaChaKey
            )

        } catch (encryptionError: Exception) {
            Log.e("Keyspace", "An error occurred while encrypting data. Either the plaintext was null or the xChaCha key was of incorrect length. If the key is twice the expected length, check the encoding (base64/HexString).")
            encryptionError.printStackTrace()
        }

        return EncryptedData (
            ciphertext,
            nonce
        )
    }

    @SuppressLint("GetInstance")
    fun xChaChaDecrypt(nonce: ByteArray, ciphertext: ByteArray, xChaChaKey: ByteArray): String? {
        val plaintext = ByteArray(ciphertext.size - SecretBox.XSALSA20POLY1305_MACBYTES)

        try {
            val decrypt = lazySodium.cryptoAeadXChaCha20Poly1305IetfDecrypt (
                plaintext,
                null,
                null,
                ciphertext,
                ciphertext.size.toLong(),
                null,
                0,
                nonce,
                xChaChaKey
            )

        } catch (decryptionError: Exception) {
            Log.e("Keyspace", "An error occurred while decrypting data")
            decryptionError.printStackTrace()
        }
        return String(plaintext).fromB64ToUtf8()
    }

    fun kfsEncrypt (plaintextString: String?, xChaChaKey: ByteArray): String? {
        return try {
            val encryption = xChaChaEncrypt (plaintextString, xChaChaKey)
            val ciphertextString = encryption.ciphertext
            val nonce = encryption.nonce
            ciphertextString!!.toHexString() + nonce!!.toHexString()
        } catch (nullData: Exception) {
            null
        }
    }

    fun kfsDecrypt (ciphertextString: String?, xChaChaKey: ByteArray): String? {
        return try {
            val nonce = HexMessageEncoder().decode(ciphertextString!!.takeLast(XCHACHA_POLY1305_NONCE_BYTES * 2))
            val ciphertext = HexMessageEncoder().decode(ciphertextString.substringBefore(ciphertextString!!.takeLast(XCHACHA_POLY1305_NONCE_BYTES * 2)))
            xChaChaDecrypt (nonce, ciphertext, xChaChaKey)
        } catch (nullData: Exception) {
            null
        }
    }

    data class Ed25519Keypair(
        val publicKey: ByteArray,
        val privateKey: ByteArray,
    )

    fun generateEphemeralKeys (): Ed25519Keypair {
        val keypair = lazySodium.cryptoKxKeypair()
        return Ed25519Keypair (
            keypair.publicKey.asBytes,
            keypair.secretKey.asBytes
        )
    }

    fun generateKeyroutePayload (email: String, signedToken: String, clientEphemeralPublicKey: ByteArray, clientEphemeralPrivateKey: ByteArray, serverEphemeralPublicKey: ByteArray, keyring: Keyring): String? {
        lateinit var ciphertext: String
        var clientSharedSecretRx = ByteArray(KeyExchange.SESSIONKEYBYTES)
        var clientSharedSecretTx = ByteArray(KeyExchange.SESSIONKEYBYTES)

        var generateSharedKey = lazySodium.cryptoKxClientSessionKeys (
            clientSharedSecretRx,
            clientSharedSecretTx,  // to be used as symmetric xChaCha key
            clientEphemeralPublicKey,
            clientEphemeralPrivateKey,
            serverEphemeralPublicKey
        )

        val keyringData = mapOf (
            "publicKey" to keyring.ED25519_PUBLIC_KEY?.toHexString(),
            "privateKey" to keyring.ED25519_PRIVATE_KEY?.toHexString(),
            "symmetricKey" to keyring.XCHACHA_POLY1305_KEY?.toHexString()
        )

        val encrypted = xChaChaEncrypt(
            JSONObject(keyringData).toString(4),
            clientSharedSecretTx
        )
        Log.d("Plain_keyring", JSONObject(keyringData).toString())
        ciphertext = encrypted.ciphertext!!.toHexString() + encrypted.nonce!!.toHexString()

        val keyrouteMessage = mapOf (
            "keyringData" to ciphertext,
            "signedToken" to signedToken,
            "dhPublicKey" to clientEphemeralPublicKey.toHexString(),
            "email" to email,
        )

        val payload = mapOf (
            "message" to mapOf (
                "message" to keyrouteMessage,
                "type" to "data"
            )
        )
        return JSONObject(payload).toString()
    }

    fun secureStartActivity (nextActivity: AppCompatActivity, nextActivityClassNameAsString: String, keyring: Keyring?, itemId: String?) {
        val intent = Intent (context, nextActivity::class.java) // Target explicitly to Dashboard Activity only
        intent.flags = FLAG_ACTIVITY_NEW_TASK

        intent.putExtra (XCHACHA_POLY1305_KEY_ALIAS, keyring?.XCHACHA_POLY1305_KEY)
        intent.putExtra (ED25519_PUBLIC_KEY_ALIAS, keyring?.ED25519_PUBLIC_KEY)
        intent.putExtra (ED25519_PRIVATE_KEY_ALIAS, keyring?.ED25519_PRIVATE_KEY)
        //intent.putExtra (LOGIN_TOKEN_ALIAS, keyring?.LOGIN_TOKEN)
        intent.putExtra ("itemId", itemId)
        intent.setClassName (context.packageName, context.packageName + "." + nextActivityClassNameAsString) // Target explicitly to Keyspace app's Dashboard class only

        context.startActivity (intent)

        intent.removeExtra (XCHACHA_POLY1305_KEY_ALIAS)
        intent.removeExtra (ED25519_PUBLIC_KEY_ALIAS)
        intent.removeExtra (ED25519_PRIVATE_KEY_ALIAS)
        intent.removeExtra (LOGIN_TOKEN_ALIAS)
        intent.removeExtra ("itemId")

        try {
            for (key in intent.extras!!.keySet()) intent.removeExtra (key)
        } catch (nothingLeft: NullPointerException) {
            Log.d("Keyspace", "Nothing left in intent because it was wiped successfully. You're in good hands... :)")
        }

        intent.action = null
        intent.data = null
        intent.replaceExtras(Bundle())
        intent.flags = 0

        activity.finish()

    }

    fun receiveKeyringFromSecureIntent(currentActivityClassNameAsString: String, intent: Intent): Pair<Keyring, String?> {

        lateinit var xChaChaPoly1305Key: ByteArray
        lateinit var ed25519PublicKey: ByteArray
        lateinit var ed25519PrivateKey: ByteArray
        //lateinit var loginToken: ByteArray
        var itemId: String? = null

        if (intent.resolveActivity(context.packageManager).packageName == BuildConfig.APPLICATION_ID && intent.resolveActivity(context.packageManager).className == context.packageName + "." + currentActivityClassNameAsString) {

            xChaChaPoly1305Key = intent.getByteArrayExtra(XCHACHA_POLY1305_KEY_ALIAS)!!
            ed25519PublicKey = intent.getByteArrayExtra(ED25519_PUBLIC_KEY_ALIAS)!!
            ed25519PrivateKey = intent.getByteArrayExtra(ED25519_PRIVATE_KEY_ALIAS)!!
            //loginToken = intent.getByteArrayExtra(LOGIN_TOKEN_ALIAS)!!
            itemId = intent.getStringExtra("itemId")

            intent.removeExtra(XCHACHA_POLY1305_KEY_ALIAS)
            intent.removeExtra(ED25519_PUBLIC_KEY_ALIAS)
            intent.removeExtra(ED25519_PRIVATE_KEY_ALIAS)
            intent.removeExtra(LOGIN_TOKEN_ALIAS)
            intent.removeExtra ("itemId")

            try {
                for (key in intent.extras!!.keySet()) intent.removeExtra(key)
            } catch (nothingLeft: NullPointerException) {
                Log.d("Keyspace", "Nothing left in intent because it was wiped successfully. You're in good hands... :)")
            }

            intent.action = null
            intent.data = null
            intent.replaceExtras(Bundle())
            intent.flags = 0

        }

        // force gc to clear keyring
        System.gc()

        return Pair(
            Keyring(
                XCHACHA_POLY1305_KEY = xChaChaPoly1305Key,
                ED25519_PUBLIC_KEY = ed25519PublicKey,
                ED25519_PRIVATE_KEY = ed25519PrivateKey,
                //LOGIN_TOKEN = loginToken
            ),
            itemId
        )

    }

}
