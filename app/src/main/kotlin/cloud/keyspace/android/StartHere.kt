package cloud.keyspace.android

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils.loadAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import cash.z.ecc.android.bip39.Mnemonics
import com.android.volley.NetworkError
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.keyspace.keyspacemobile.NetworkUtilities
import com.nulabinc.zxcvbn.Zxcvbn
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import java.util.concurrent.Executor
import kotlin.system.exitProcess


private lateinit var _supportFragmentManager: FragmentManager

private val MODE_CREATE_ACCOUNT = "createAccount"
private val MODE_SIGN_IN = "signIn"

private lateinit var mapper: ObjectMapper

class StartHere : AppCompatActivity() {
    private lateinit var fadeAnimation: Animation
    private lateinit var slideAnimation: Animation

    private lateinit var crypto: CryptoUtilities
    private lateinit var misc: MiscUtilities
    private lateinit var configData: SharedPreferences


    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var keyring: CryptoUtilities.Keyring

    fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) } // to print, not related to crypto

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        _supportFragmentManager = supportFragmentManager

        mapper = jacksonObjectMapper()

        crypto = CryptoUtilities(applicationContext, this)
        misc = MiscUtilities(applicationContext)

        configData = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)
        var isLoggedIn = configData.getBoolean("userLoggedIn", false)

        fadeAnimation = loadAnimation(applicationContext, R.anim.fade_in)
        slideAnimation = loadAnimation(applicationContext, R.anim.from_bottom)

        setContentView(R.layout.onboarding)

        if (!misc.screenLockEnabled()) renderMissingScreenLockDialog() else {
            if (isLoggedIn) { // already logged in
                loggedInBiometrics()

            } else { // not logged in
                rootedDeviceCheck()
                loadFragment(EmailFragment())
            }
        }

    }

    private fun rootedDeviceCheck() {
        val rootBeer = RootBeer(applicationContext)
        if (rootBeer.isRootedWithBusyBoxCheck) {
            val dialogBuilder = MaterialAlertDialogBuilder(this@StartHere)
            dialogBuilder
                .setCancelable(false)
                .setTitle("Hmmm")
                .setIcon(R.drawable.ic_baseline_root_24)
                .setMessage("We don't mind that you've rooted your device, but we can't guarantee you'll have the same level of security.")
                .setPositiveButton("I understand") { dialog, _ -> dialog.dismiss() }

            val alert = dialogBuilder.create()
            alert.show()

        } else {
            Log.d("Keyspace", "Clearly, you're not a nerd.  :(")
        }
    }

    private fun renderMissingScreenLockDialog() {

        val builder = MaterialAlertDialogBuilder(this@StartHere)
        builder.setCancelable(false)
        val noLockInfoBox: View = layoutInflater.inflate(R.layout.no_lock_set_description, null)
        builder.setView(noLockInfoBox)
        noLockInfoBox.startAnimation(loadAnimation(applicationContext, R.anim.from_bottom))
        noLockInfoBox.findViewById<MaterialButton>(R.id.openSettingsButton).setOnClickListener {
            clearKeyspaceData()
            val intent = Intent(ACTION_SET_NEW_PASSWORD)
            startActivity(intent)
            finishAndRemoveTask()
            finishAffinity()
        }

        val dialog = builder.create()
        dialog.show()

        val view = dialog.window!!.decorView
        ObjectAnimator.ofFloat(view, "translationY", view.height.toFloat(), 0.0f).start()

    }

    private fun clearKeyspaceData() {
        crypto.wipeAllKeys()
        configData.edit().clear().commit()

        val filenameExtension = "kfs"
        val vaultFilename = "vault"
        var filename: String? = "$vaultFilename.$filenameExtension"

        val file = File(applicationContext.cacheDir, filename!!)
        file.delete()
        file.deleteRecursively()
    }

    class AuthenticationFragment(email: String, passphrase: CharArray, spacedWords: CharArray, mode: String) : Fragment() {
        lateinit var authenticationIcon: ImageView
        lateinit var titleBiometrics: TextView
        lateinit var descriptionBiometrics: TextView
        lateinit var authenticateButton: MaterialButton
        lateinit var backButton: ImageView

        val email = email
        val passphrase = passphrase
        val spacedWords = spacedWords
        val mode = mode

        lateinit var misc: MiscUtilities
        lateinit var crypto: CryptoUtilities
        private var authenticationScreenFragmentView: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            authenticationScreenFragmentView = inflater.inflate(R.layout.onboarding_authentication_blurb, container, false)
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

            loadContent()

            authenticateButton.setOnClickListener {
                displayScreenLock()
            }

            backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

            return authenticationScreenFragmentView

        }

        @SuppressLint("UseCompatLoadingForDrawables")
        private fun loadContent() {

            misc = MiscUtilities(requireContext())
            crypto = CryptoUtilities(requireContext(), requireActivity() as AppCompatActivity)

            authenticationIcon = authenticationScreenFragmentView!!.findViewById(R.id.authenticationIcon)
            descriptionBiometrics = authenticationScreenFragmentView!!.findViewById(R.id.descriptionBiometrics)
            titleBiometrics = authenticationScreenFragmentView!!.findViewById(R.id.titleBiometrics)
            authenticateButton = authenticationScreenFragmentView!!.findViewById(R.id.authenticateButton)
            backButton = authenticationScreenFragmentView!!.findViewById(R.id.backButton)

            if (!misc.biometricsExist()) {
                authenticationIcon.setImageDrawable(requireContext().getDrawable(R.drawable.ic_baseline_phonelink_lock_24))
                titleBiometrics.text = "Device lock"
                descriptionBiometrics.text = requireContext().getString(R.string.blurbDescriptionDeviceLock)
            }

        }

        val biometricPromptThread = Handler(Looper.getMainLooper())
        private fun displayScreenLock () {
            val executor: Executor = ContextCompat.getMainExecutor(requireActivity()) // execute on different thread awaiting response

            try {
                val biometricManager = BiometricManager.from(requireActivity())
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
                        biometricPromptThread.removeCallbacksAndMessages(null)
                        authenticationIcon.setImageDrawable(requireContext().getDrawable(R.drawable.ic_baseline_check_24))
                        authenticationIcon.animation = loadAnimation(requireContext(), R.anim.zoom_spin)
                        authenticationIcon.animate()

                        authenticateButton.isEnabled = false

                        loadFragment(SetAccountFragment(email, passphrase, spacedWords, mode))

                        Log.d("Keyspace", "Authentication successful")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { // Authentication error. Verify error code and message
                        biometricPromptThread.removeCallbacksAndMessages(null)
                        Log.d("Keyspace", "Authentication canceled")
                    }

                    override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                        biometricPromptThread.removeCallbacksAndMessages(null)
                        Log.d("Keyspace", "Incorrect credentials supplied")
                    }
                }

                val biometricPrompt = BiometricPrompt(requireActivity(), executor, authenticationCallback)

                val builder = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(resources.getString(R.string.app_name))
                    .setSubtitle(resources.getString(R.string.biometrics_generic_subtitle))
                    .setDescription(resources.getString(R.string.biometrics_modify_item_description))
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                builder.setConfirmationRequired(true)

                val promptInfo = builder.build()
                biometricPrompt.authenticate(promptInfo)

                biometricPromptThread.postDelayed({
                    biometricPrompt.cancelAuthentication()
                    biometricPromptThread.removeCallbacksAndMessages(null)
                    val builder = MaterialAlertDialogBuilder(requireActivity())
                    builder.setTitle("Authentication error")
                    builder.setMessage("Authentication timed out because you waited too long.\n\nPlease try again.")
                    builder.setNegativeButton("Retry") { _, _ ->
                        displayScreenLock ()
                    }

                    try {
                        val errorDialog: AlertDialog = builder.create()
                        errorDialog.setCancelable(true)
                        errorDialog.show()
                    } catch (activityDead: WindowManager.BadTokenException) {
                    }

                }, (crypto.DEFAULT_AUTHENTICATION_DELAY - 2).toLong() * 1000)


            } catch (noLockSet: NoSuchMethodError) {
                biometricPromptThread.removeCallbacksAndMessages(null)
                val builder = MaterialAlertDialogBuilder(requireActivity())
                builder.setTitle("No biometric hardware")
                builder.setMessage("Your biometric sensors (fingerprint, face ID or iris scanner) could not be accessed. Please add biometrics from your phone's settings to continue.\n\nTry restarting your phone if you have already enrolled biometrics.")
                builder.setNegativeButton("Exit") { _, _ ->
                    requireActivity().finishAffinity()
                }
                val errorDialog: AlertDialog = builder.create()
                errorDialog.setCancelable(true)
                errorDialog.show()

                Log.e("Keyspace", "Please set a screen lock.")
                noLockSet.stackTrace

            } catch (incorrectCredentials: Exception) {
                biometricPromptThread.removeCallbacksAndMessages(null)
                val builder = MaterialAlertDialogBuilder(requireActivity())
                builder.setTitle("Authentication failed")
                builder.setMessage("Your identity couldn't be verified. Please try again after a while.")
                builder.setNegativeButton("Exit") { _, _ ->
                    requireActivity().finishAffinity()
                }
                val errorDialog: AlertDialog = builder.create()
                errorDialog.setCancelable(true)
                errorDialog.show()

                Log.e("Keyspace", "Your identity could not be verified.")
                incorrectCredentials.stackTrace

            }
        }
    }

    class SetAccountFragment(email: String, passphrase: CharArray, spacedWords: CharArray, mode: String) : Fragment() {
        lateinit var iconography: ImageView
        lateinit var loadingText: TextSwitcher
        lateinit var loadingSubtitle: TextSwitcher
        lateinit var loadingBar: ProgressBar

        lateinit var animatedLogo: AnimatedVectorDrawable
        lateinit var animatedLogoToSend: AnimatedVectorDrawable
        lateinit var sendToReceive: AnimatedVectorDrawable
        lateinit var receiveToKeystore: AnimatedVectorDrawable

        val email = email
        val passphrase = passphrase
        val spacedWords = spacedWords
        val mode = mode

        lateinit var sha256: ByteArray
        lateinit var bip39Seed: ByteArray
        lateinit var asymmetricHardenedSeed: ByteArray
        lateinit var symmetricHardenedSeed: ByteArray
        lateinit var symmetricKey: ByteArray
        lateinit var publicKey: ByteArray
        lateinit var privateKey: ByteArray
        lateinit var keyring: CryptoUtilities.Keyring

        lateinit var signedToken: String

        fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
        lateinit var crypto: CryptoUtilities
        lateinit var io: IOUtilities
        lateinit var network: NetworkUtilities
        lateinit var configData: SharedPreferences

        private var loadingScreenFragmentView: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            loadingScreenFragmentView = inflater.inflate(R.layout.loading_screen, container, false)

            loadContent()
            try {
                generateCryptoObjects()
                // logger()
                if (mode == MODE_CREATE_ACCOUNT) createAccount()
                else if (mode == MODE_SIGN_IN) signIn()

            } catch (unknownError: Exception) {
                showCryptographyErrorDialog()
            }

            return loadingScreenFragmentView

        }

        private fun generateCryptoObjects () {

            sha256 = crypto.choppedSha256(spacedWords)
            bip39Seed = crypto.wordsToSeed(spacedWords, passphrase)!!

            val rootSignSeed = crypto.kdf (
                masterKey = bip39Seed,
                context = crypto.ROOT_SIGNED_SEED_CONTEXT,
                keyId = crypto.ROOT_SIGNED_SEED_KEY_ID
            )

            val vaultKey = crypto.kdf (
                masterKey = bip39Seed,
                context = crypto.VAULT_KEY_CONTEXT,
                keyId = crypto.VAULT_KEY_KEY_ID
            )

            symmetricKey = vaultKey!!

            val ed25519 = crypto.ed25519Keypair (
                seed = rootSignSeed!!
            )

            publicKey = ed25519.publicKey
            privateKey = ed25519.privateKey

            keyring = CryptoUtilities.Keyring (
                XCHACHA_POLY1305_KEY = symmetricKey,
                ED25519_PUBLIC_KEY = publicKey,
                ED25519_PRIVATE_KEY = privateKey
            )

            network = NetworkUtilities(
                requireContext(),
                requireActivity() as AppCompatActivity,
                keyring
            )

        }

        private fun signIn () {
            CoroutineScope(Dispatchers.IO).launch {
                val keyauthResponse = network.sendKeyauthRequest()
                val message = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(keyauthResponse)
                val signedToken = crypto.sign(message, privateKey)

                withContext(Dispatchers.Main) {  // used to run synchronous Kotlin functions like `suspend fun foo()`
                    delay (500)
                    setKeygen()

                    try {
                        generateCryptoObjects()

                        keygenToSend()
                        delay(500)

                        CoroutineScope(Dispatchers.IO).launch {
                            kotlin.runCatching {
                                val vaultData = network.grabLatestVaultFromBackend (signedToken)
                                withContext(Dispatchers.Main) {  // used to run synchronous Kotlin functions like `suspend fun foo()`
                                    sendToReceive()

                                    io.writeVault(vaultData)

                                    delay(500)
                                    receiveToKeystore()
                                    storeToKeyring()
                                    delay (1000)

                                    keystoreToTick()
                                    delay (3000)

                                    startPermissions()
                                }
                            }.onFailure {
                                when (it) {
                                    is NetworkUtilities.IncorrectCredentialsException -> {
                                        withContext(Dispatchers.Main) {
                                            showIncorrectCredentialsDialog()
                                        }
                                    }
                                    is NetworkError -> {
                                        withContext(Dispatchers.Main) {
                                            showNetworkErrorDialog()
                                        }
                                    }
                                    else -> throw it
                                }
                            }
                        }

                    } catch (unknownError: Exception) {
                        showCryptographyErrorDialog ()
                    }

                }
            }
        }

        @SuppressLint("UseRequireInsteadOfGet")
        private fun createAccount () {
            CoroutineScope(Dispatchers.IO).launch {
                val keyauthResponse = network.sendKeyauthRequest()
                val message = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(keyauthResponse)
                val signedToken = crypto.sign(message, privateKey)

                withContext(Dispatchers.Main) {  // used to run synchronous Kotlin functions like `suspend fun foo()`
                    delay (500)
                    setKeygen()

                    try {
                        generateCryptoObjects()

                        keygenToSend()
                        CoroutineScope(Dispatchers.IO).launch {
                            kotlin.runCatching {
                                val createAccountResponse: NetworkUtilities.SignupResponse = network.sendSignupRequest(
                                    NetworkUtilities.SignupParameters(
                                        email = email,
                                        public_key = publicKey.toHexString(),
                                        signed_token = signedToken
                                    )
                                )!!

                                if (createAccountResponse.status != network.RESPONSE_SUCCESS) throw NetworkUtilities.AccountExistsException() else {
                                    withContext(Dispatchers.Main) {  // used to run synchronous Kotlin functions like `suspend fun foo()`
                                        delay(1000)

                                        receiveToKeystore()
                                        storeToKeyring()
                                        delay (1000)

                                        keystoreToTick()
                                        delay (3000)

                                        startPermissions()
                                    }
                                }

                            }.onFailure {
                                when (it) {
                                    is NetworkUtilities.AccountExistsException -> {
                                        withContext(Dispatchers.Main) {
                                            showDuplicateAccountDialog()
                                        }
                                    }
                                    is NetworkError -> {
                                        withContext(Dispatchers.Main) {
                                            showNetworkErrorDialog()
                                        }
                                    }
                                    else -> throw it
                                }
                            }
                        }

                    } catch (unknownError: Exception) {
                        showCryptographyErrorDialog ()
                    }

                }
            }
        }

        private fun showIncorrectCredentialsDialog () {
            val dialogBuilder = MaterialAlertDialogBuilder(requireActivity())
            dialogBuilder
                .setCancelable(false)
                .setTitle("Incorrect credentials")
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage("Please check your username, passphrase or recovery phrase and try again.")
                .setPositiveButton("Start over") { dialog, _ ->
                    quitApp(requireActivity(), true)
                }

            val alert = dialogBuilder.create()
            alert.show()

        }

        private fun showDuplicateAccountDialog () {
            val dialogBuilder = MaterialAlertDialogBuilder(requireActivity())
            dialogBuilder
                .setCancelable(false)
                .setTitle("Account exists")
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage("An account with the email \"$email\" already exists. Please sign in using that email instead.")
                .setPositiveButton("Start over") { dialog, _ ->
                    quitApp(requireActivity(), true)
                }

            val alert = dialogBuilder.create()
            alert.show()

        }

        private fun showNetworkErrorDialog () {
            val dialogBuilder = MaterialAlertDialogBuilder(requireActivity())
            dialogBuilder
                .setCancelable(false)
                .setTitle("Connection error")
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage("Couldn't connect to Keyspace backend. Please check your connection and try again.")
                .setPositiveButton("Start over") { dialog, _ ->
                    quitApp(requireActivity(), true)
                }
                .setNegativeButton("Quit app") { dialog, _ ->
                    quitApp(requireActivity(), false)
                }

            val alert = dialogBuilder.create()
            alert.show()

        }

        private fun showCryptographyErrorDialog () {
            val dialogBuilder = MaterialAlertDialogBuilder(requireActivity())
            dialogBuilder
                .setCancelable(false)
                .setTitle("Cryptography error")
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage("A cryptographic error occurred. Reach out to the Keyspace team.")
                .setPositiveButton("Quit app") { dialog, _ ->
                    quitApp(requireActivity(), false)
                }

            val alert = dialogBuilder.create()
            alert.show()
        }

        private fun startPermissions() {
            requireActivity().finish()
            val intent = Intent(requireContext(), Permissions::class.java)
            startActivity(intent)
        }

        private fun storeToKeyring() {
            crypto.wipeAllKeys()

            var keyring: CryptoUtilities.Keyring? = CryptoUtilities.Keyring(
                XCHACHA_POLY1305_KEY = symmetricKey,
                ED25519_PUBLIC_KEY = publicKey,
                ED25519_PRIVATE_KEY = privateKey,
                // LOGIN_TOKEN = token
            )

            crypto.storeKeys(keyring!!, crypto.getKeystoreMasterKey())

            configData.edit()
                .putBoolean("userLoggedIn", true)
                .putString("userEmail", email)
                .apply()

            keyring = null
            System.gc()
        }

        private fun animTest() {
            Handler().postDelayed({
                setKeygen()
                Handler().postDelayed({
                    keygenToSend()
                    Handler().postDelayed({
                        sendToReceive()
                        Handler().postDelayed({
                            receiveToKeystore()
                            Handler().postDelayed({ keystoreToTick() }, 1500)
                        }, 1500)
                    }, 1500)
                }, 400)
            }, 400)
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        private fun loadContent() {
            // NetworkUtilities is defined in generateCryptoObject()
            configData = requireContext().getSharedPreferences(requireContext().packageName + "_configuration_data", MODE_PRIVATE)
            crypto = CryptoUtilities(requireContext(), requireActivity() as AppCompatActivity)
            io = IOUtilities(requireContext(), requireActivity() as AppCompatActivity, CryptoUtilities.Keyring(ByteArray(0),ByteArray(0),ByteArray(0)))

            iconography = loadingScreenFragmentView!!.findViewById(R.id.iconography)
            loadingText = loadingScreenFragmentView!!.findViewById(R.id.loadingTextSwitcher)
            loadingSubtitle = loadingScreenFragmentView!!.findViewById(R.id.loadingSubtitleSwitcher)

            loadingText.setInAnimation(requireContext(), android.R.anim.fade_in)
            loadingText.setOutAnimation(requireContext(), android.R.anim.fade_out)
            loadingSubtitle.setInAnimation(requireContext(), android.R.anim.fade_in)
            loadingSubtitle.setOutAnimation(requireContext(), android.R.anim.fade_out)

            loadingBar = loadingScreenFragmentView!!.findViewById(R.id.loadingBar)

            animatedLogo = requireContext().getDrawable(R.drawable.keyspace_animated_splash_fast) as AnimatedVectorDrawable
            animatedLogoToSend = requireContext().getDrawable(R.drawable.logotocloud) as AnimatedVectorDrawable
            sendToReceive = requireContext().getDrawable(R.drawable.sendtoreceive) as AnimatedVectorDrawable
            receiveToKeystore = requireContext().getDrawable(R.drawable.receivetophone) as AnimatedVectorDrawable

        }

        private fun setKeygen() {
            loadingScreenFragmentView!!.findViewById<TextView>(R.id.loadingText).text = "Generating keys"
            loadingScreenFragmentView!!.findViewById<TextView>(R.id.loadingSubtitle).text =  "Creating your\nEd25519 keypair"

            iconography.setImageDrawable(animatedLogo)
            animatedLogo.start()

            Handler().postDelayed({ loadingSubtitle.setText ("Creating your\nXChaCha20-Poly1305 symmetric key") }, 500)

        }

        private fun keygenToSend() {
            Handler().postDelayed({
                iconography.animate().scaleX(1.5f).scaleY(1.5f)
                iconography.setImageDrawable(animatedLogoToSend)
                loadingText.setText ("Uploading")
                loadingSubtitle.setText ("Sending your account details")
                animatedLogoToSend.start() }, 1000)
        }

        private fun sendToReceive() {
            Handler().postDelayed({
                iconography.animate().scaleX(1.5f).scaleY(1.5f)
                iconography.setImageDrawable(sendToReceive)
                loadingText.setText ("Downloading")
                loadingSubtitle.setText ("Fetching your Keyspace vault")
                sendToReceive.start() }, 1500)
        }

        private fun receiveToKeystore() {
            Handler().postDelayed({
                iconography.animate().scaleX(1.25f).scaleY(1.25f)
                loadingText.setText ("Storing keys")

                var strongBoxExists = try {
                    if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                        "Securing your keyring using Strongbox"
                    } else if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE)) {
                        "Securing your keyring using HAL Keystore"
                    } else {
                        "Securing your keyring using Keystore"
                    }
                } catch (couldntGetStrongboxStatus: IllegalStateException) {
                    "Securing your keyring using Keystore"
                }

                loadingSubtitle.setText (strongBoxExists)
                iconography.setImageDrawable(receiveToKeystore)
                receiveToKeystore.start() }, 1500)
        }

        private fun keystoreToTick() {
            Handler().postDelayed({
                iconography.animate().scaleX(0.85f).scaleY(0.85f)
                iconography.startAnimation(loadAnimation(requireContext(), R.anim.zoom_spin))
                iconography.setImageDrawable(requireContext().getDrawable(R.drawable.ic_baseline_check_24))
                loadingText.setText ("Signed in")
                loadingSubtitle.setText ("You're all set to use Keyspace!")
                loadingBar.isIndeterminate = false
                loadingBar.animation = loadAnimation(requireContext(), R.anim.fade_out)
                loadingText.performHapticFeedback(HapticFeedbackConstants.REJECT) }, 1500)
        }

    }

    class WordsBlurbFragment(email: String, passphrase: CharArray) : Fragment() {
        lateinit var iAgree: MaterialCheckBox
        lateinit var recoveryPhraseBlurbContinue: MaterialButton
        lateinit var backButton: ImageView
        lateinit var timerObject: Timer

        private var wordsBlurbFragmentView: View? = null

        private val email = email
        private val passphrase = passphrase

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            wordsBlurbFragmentView = inflater.inflate(R.layout.onboarding_words_blurb, container, false)

            timerObject = Timer()
            loadContent()
            loadOnboardingBlurb()

            return wordsBlurbFragmentView

        }

        private fun loadContent() {
            iAgree = wordsBlurbFragmentView!!.findViewById(R.id.iAgree)
            recoveryPhraseBlurbContinue = wordsBlurbFragmentView!!.findViewById(R.id.recoveryPhraseBlurbContinue)
            backButton = wordsBlurbFragmentView!!.findViewById(R.id.backButton)
            backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed(); timerObject.cancel(); timerObject.purge() }
        }

        private fun loadOnboardingBlurb() {
            val iAgreeText = iAgree.text.toString()
            var timer = 10

            iAgree.isEnabled = false
            timer += 1
            timerObject.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        requireActivity().runOnUiThread {
                            iAgree.isChecked = false
                            timer -= 1
                            iAgree.text = "$iAgreeText ($timer)"
                            if (timer == 0) { // Time Up
                                timerObject.cancel()
                                requireActivity().runOnUiThread { iAgree.isEnabled = true; iAgree.text = iAgreeText }
                            }
                        }
                    } catch (noContext: IllegalStateException) {
                    }
                }
            }, 0, 1000) // 1000 milliseconds = 1 second

            recoveryPhraseBlurbContinue.isEnabled = false

            recoveryPhraseBlurbContinue.setOnClickListener {
                loadFragment(ShowWordsFragment(email, passphrase))
            }

            iAgree.setOnCheckedChangeListener { buttonView, isChecked ->
                recoveryPhraseBlurbContinue.isEnabled = isChecked
            }
        }

    }

    class TapWordsFragment(email: String, passphrase: CharArray, sha256: ByteArray, spacedWords: CharArray, seed: ByteArray) : Fragment() {
        lateinit var subtitleTapWords: TextView
        lateinit var tapWordsChips: ChipGroup
        lateinit var progressTapWords: ProgressBar
        lateinit var textProgressTapWords: TextView
        lateinit var createAccountButton: MaterialButton
        lateinit var backButton: ImageView

        private lateinit var crypto: CryptoUtilities

        private val email = email
        private val passphrase = passphrase

        private val spacedWords: CharArray = spacedWords
        private var sha256: ByteArray = sha256
        private var seed: ByteArray = seed

        private var tapWordsFragmentView: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            tapWordsFragmentView = inflater.inflate(R.layout.onboarding_tap_words, container, false)
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

            loadContent()
            loadUI()

            return tapWordsFragmentView

        }

        private fun loadContent() {
            crypto = CryptoUtilities(requireContext(), requireActivity() as AppCompatActivity)

            subtitleTapWords = tapWordsFragmentView!!.findViewById(R.id.subtitleTapWords)
            progressTapWords = tapWordsFragmentView!!.findViewById(R.id.progressTapWords)
            textProgressTapWords = tapWordsFragmentView!!.findViewById(R.id.textProgressTapWords)
            tapWordsChips = tapWordsFragmentView!!.findViewById(R.id.tapWordsChips)
            tapWordsChips.isSingleSelection = true
            createAccountButton = tapWordsFragmentView!!.findViewById(R.id.createAccountButton)
            backButton = tapWordsFragmentView!!.findViewById(R.id.backButton)
            backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        private fun loadUI() {
            var wordList = String(spacedWords).split(" ").toList()
            var shuffledWordList = wordList.shuffled()

            for (index in 0 until tapWordsChips.childCount) {
                val chip = tapWordsChips.getChildAt(index) as Chip
                chip.text = shuffledWordList[index]
                chip.isSelected = false
            }

            var counter = 0
            textProgressTapWords.text = "$counter complete, ${tapWordsChips.childCount} to go."

            tapWordsChips.setOnCheckedStateChangeListener { chipGroup, id ->
                val chipId = chipGroup.checkedChipId
                val chip: Chip = chipGroup.findViewById(chipId)

                if (wordList[counter] == chip.text.toString().lowercase()) {
                    if (counter != tapWordsChips.childCount) {
                        createAccountButton.isEnabled = false
                        counter++
                        chip.isEnabled = false
                        chip.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        chip.startAnimation(loadAnimation(requireContext(), R.anim.fade_out))
                        progressTapWords.progress = counter
                        textProgressTapWords.text = "$counter complete, ${tapWordsChips.childCount - counter} to go."
                        //chip.clearAnimation()
                    }
                } else {
                    (requireContext().getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(150)
                    chip.isChecked = false
                    chip.chipIcon = requireContext().getDrawable(R.drawable.ic_baseline_close_24)
                    chip.chipIconTint = ColorStateList.valueOf(Color.RED)
                    chip.startAnimation(loadAnimation(requireContext(), R.anim.wiggle))
                    Handler().postDelayed({
                        chip.chipIcon = null
                        chip.isSelected = false
                        chip.clearAnimation()
                    }, 400)

                }

                tapWordsChips.invalidate()
                tapWordsChips.refreshDrawableState()
                tapWordsChips.clearAnimation()
                tapWordsChips.destroyDrawingCache()

                if (counter == tapWordsChips.childCount) {
                    createAccountButton.isEnabled = true
                    textProgressTapWords.text = "All done. Good job!"
                    subtitleTapWords.text = "Tap \"Next\" to go to the last step."

                    val valueAnimator = ValueAnimator.ofInt(tapWordsChips.measuredHeight, tapWordsChips.measuredHeight - tapWordsChips.measuredHeight)
                    valueAnimator.duration = 500L
                    valueAnimator.addUpdateListener {
                        val animatedValue = valueAnimator.animatedValue as Int
                        val layoutParams = tapWordsChips.layoutParams
                        layoutParams.height = animatedValue
                        tapWordsChips.layoutParams = layoutParams
                    }
                    valueAnimator.start()
                }

            }

            createAccountButton.isEnabled = false
            createAccountButton.setOnClickListener {
                loadFragment(StartHere.AuthenticationFragment(email, passphrase, spacedWords, mode = MODE_CREATE_ACCOUNT))
            }

        }

    }

    class ShowWordsFragment(email: String, passphrase: CharArray) : Fragment() {
        lateinit var wordChips: ChipGroup
        lateinit var nextButton: MaterialButton
        lateinit var backButton: ImageView
        lateinit var timerObject: Timer

        private lateinit var crypto: CryptoUtilities

        private val email = email
        private val passphrase = passphrase

        private lateinit var spacedWords: CharArray
        private lateinit var sha256: ByteArray
        private lateinit var seed: ByteArray

        private var wordsFragmentView: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            wordsFragmentView = inflater.inflate(R.layout.onboarding_show_words, container, false)
            requireActivity().window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            timerObject = Timer()
            loadContent()
            loadChips()
            loadUI()

            return wordsFragmentView

        }

        private fun loadContent() {
            crypto = CryptoUtilities(requireContext(), requireActivity() as AppCompatActivity)
            wordChips = wordsFragmentView!!.findViewById(R.id.showWordsChips)
            nextButton = wordsFragmentView!!.findViewById(R.id.nextButtonOnShowScreen)
            backButton = wordsFragmentView!!.findViewById(R.id.backButton)
            backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed(); timerObject.cancel(); timerObject.purge() }
        }

        private fun loadChips() {
            spacedWords = crypto.bip39(passphrase).words!!

            val wordList = String(spacedWords).split(" ").toList()
            sha256 = crypto.choppedSha256(spacedWords)
            seed = crypto.wordsToSeed(spacedWords, passphrase = passphrase)!!

            for (index in 0 until wordChips.childCount) {
                val chip = wordChips.getChildAt(index) as Chip
                chip.text = Html.fromHtml("""<b>&nbsp;${index+1}&nbsp;&nbsp;</b> ${wordList[index]}&nbsp;""", 0)
            }

            // Animate for fancy schmancy effects
            for (index in 0 until wordChips.childCount) {
                val chip = wordChips.getChildAt(index) as Chip
                chip.visibility = View.INVISIBLE
            }

            for (index in 0 until wordChips.childCount) {
                val chip = wordChips.getChildAt(index) as Chip
                Handler().postDelayed({
                    try {
                        chip.animation = loadAnimation(requireContext(), R.anim.fade_in)
                        chip.visibility = View.VISIBLE
                    } catch (noContext: IllegalStateException) {
                    }
                }, (0..750).random().toLong())
            }

        }

        private fun loadUI() {
            val nextButtonText = nextButton.text.toString()
            var timer = 30

            nextButton.isEnabled = false
            timer += 1
            timerObject.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        requireActivity().runOnUiThread {
                            timer -= 1
                            nextButton.text = "$nextButtonText ($timer)"
                            if (timer == 0) { // Time Up
                                cancel()
                                requireActivity().runOnUiThread { nextButton.isEnabled = true; nextButton.text = nextButtonText }
                            }
                        }
                    } catch (noContext: IllegalStateException) {
                    }
                }
            }, 0, 1000) // 1000 milliseconds = 1 second

            nextButton.isEnabled = false
            nextButton.setOnClickListener {
                loadFragment(
                    TapWordsFragment(
                        email,
                        passphrase,
                        sha256,
                        spacedWords,
                        seed
                    )
                )
            }
        }

    }

    class EnterWordsFragment(email: String, passphrase: CharArray) : Fragment() {
        lateinit var subtitleEnterWords: TextView
        lateinit var enterWordsLayout: ChipGroup
        lateinit var progressEnterWords: ProgressBar
        lateinit var textProgressEnterWords: TextView
        lateinit var signInButton: MaterialButton
        lateinit var backButton: ImageView

        private lateinit var crypto: CryptoUtilities

        private val email = email
        private val passphrase = passphrase

        private var enterWordsFragmentView: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            enterWordsFragmentView = inflater.inflate(R.layout.onboarding_enter_words, container, false)
            requireActivity().window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            loadContent()
            loadUI()

            return enterWordsFragmentView

        }

        private fun loadContent() {
            crypto = CryptoUtilities(requireContext(), requireActivity() as AppCompatActivity)

            subtitleEnterWords = enterWordsFragmentView!!.findViewById(R.id.subtitleEnterWords)
            progressEnterWords = enterWordsFragmentView!!.findViewById(R.id.progressEnterWords)
            textProgressEnterWords = enterWordsFragmentView!!.findViewById(R.id.textProgressEnterWords)
            enterWordsLayout = enterWordsFragmentView!!.findViewById(R.id.enterWordsLayout)
            enterWordsLayout.isSingleSelection = true
            signInButton = enterWordsFragmentView!!.findViewById(R.id.signInButton)
            backButton = enterWordsFragmentView!!.findViewById(R.id.backButton)
            backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        private fun loadUI() {

            signInButton.isEnabled = false

            // Animate for fancy schmancy effects
            for (index in 0 until enterWordsLayout.childCount) {
                val enterWordTextInputLayout = enterWordsLayout.getChildAt(index) as TextInputLayout
                enterWordTextInputLayout.visibility = View.INVISIBLE
            }

            val bip39WordsList: List<String> = Mnemonics.getCachedWords("en")

            val words = mutableListOf<String?>()
            for (index in 0 until enterWordsLayout.childCount) words.add(index, null)
            for (index in 0 until enterWordsLayout.childCount) {
                val enterWordTextInputLayout = enterWordsLayout.getChildAt(index) as TextInputLayout
                Handler().postDelayed({
                    enterWordTextInputLayout.animation = loadAnimation(requireContext(), R.anim.fade_in)
                    enterWordTextInputLayout.visibility = View.VISIBLE
                }, (0..500).random().toLong())

                val enterWordTextInputEditText = enterWordTextInputLayout.editText as TextInputEditText
                enterWordTextInputEditText.doOnTextChanged { word, start, before, count ->
                    if (!word!!.contains(Regex("""[a-zA-Z]"""))) {
                        enterWordTextInputEditText.text!!.append("")
                    }

                    progressEnterWords.progress = words.count { it != null }

                    if (bip39WordsList.contains(word.toString().lowercase())) {
                        (requireContext().getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(250)
                        enterWordTextInputLayout.startIconDrawable = requireContext().getDrawable(R.drawable.ic_baseline_check_24)
                        textProgressEnterWords.text = "${word.toString().capitalize()} is a valid word!"

                        words[index] = word.toString().lowercase()
                        progressEnterWords.progress = words.count { it != null }

                        if ((words.count { it == null }) == 0) {
                            signInButton.isEnabled = true
                        }

                    } else {
                        words[index] = null
                        progressEnterWords.progress = words.count { it != null }
                        enterWordTextInputLayout.startIconDrawable = null
                        textProgressEnterWords.text = "Enter word #${index+1}"
                        signInButton.isEnabled = false
                    }

                }
            }

            signInButton.setOnClickListener {
                try {
                    val spacedWords = words.joinToString(prefix = "", postfix = "", separator = " ").toCharArray()
                    loadFragment(AuthenticationFragment(email, passphrase, spacedWords, mode = MODE_SIGN_IN))
                } catch (incorrectMnemonics: UninitializedPropertyAccessException) {
                    val dialogBuilder = MaterialAlertDialogBuilder(requireActivity())
                    dialogBuilder
                        .setCancelable(false)
                        .setTitle("Incorrect recovery phrase")
                        .setIcon(R.drawable.ic_baseline_error_24)
                        .setMessage("The recovery phrase you entered is invalid. Please make sure that you enter your words in the right order.")
                        .setPositiveButton("Try again") { dialog, _ ->
                            _supportFragmentManager.popBackStackImmediate()
                            loadFragment(EnterWordsFragment(email, passphrase))
                        }

                    val alert = dialogBuilder.create()
                    alert.show()
                }
            }

        }

    }

    class PassphraseFragment(email: String) : Fragment() {
        lateinit var titlePassphrase: TextView
        lateinit var subtitlePassphrase: TextView
        lateinit var descriptionPassphrase: TextView
        lateinit var passphraseInput: TextInputEditText
        lateinit var passphraseLayout: TextInputLayout
        lateinit var reenterPassphraseInput: TextInputEditText
        lateinit var reenterPassphraseLayout: TextInputLayout
        lateinit var passphraseStrength: TextView
        lateinit var createNewButton: MaterialButton
        lateinit var signInButton: MaterialButton
        lateinit var backButton: ImageView

        private var passphraseFragmentView: View? = null

        private val email = email

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            passphraseFragmentView = inflater.inflate(R.layout.onboarding_enter_passphrase, container, false)

            loadContent()
            loadForCreatingAccount()

            return passphraseFragmentView

        }

        private fun loadContent() {
            titlePassphrase = passphraseFragmentView!!.findViewById(R.id.titlePassphrase)
            subtitlePassphrase = passphraseFragmentView!!.findViewById(R.id.subtitlePassphrase)
            descriptionPassphrase = passphraseFragmentView!!.findViewById(R.id.descriptionPassphrase)
            passphraseInput = passphraseFragmentView!!.findViewById(R.id.passphraseInput)
            passphraseLayout = passphraseFragmentView!!.findViewById(R.id.passphraseLayout)
            reenterPassphraseInput = passphraseFragmentView!!.findViewById(R.id.reenterPassphraseInput)
            reenterPassphraseLayout = passphraseFragmentView!!.findViewById(R.id.reenterPassphraseLayout)
            passphraseStrength = passphraseFragmentView!!.findViewById(R.id.passphraseStrength)
            signInButton = passphraseFragmentView!!.findViewById(R.id.passphraseSignin)
            createNewButton = passphraseFragmentView!!.findViewById(R.id.passphraseCreateNew)
            backButton = passphraseFragmentView!!.findViewById(R.id.backButton)
            backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        private fun loadForSigningIn() {
            subtitlePassphrase.text = "Type in your passphrase"
            descriptionPassphrase.text = "If you used a passphrase during account creation, enter it below."

            signInButton.clearAnimation()
            subtitlePassphrase.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            descriptionPassphrase.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            signInButton.startAnimation(loadAnimation(requireContext(), R.anim.from_right))

            passphraseStrength.visibility = View.GONE
            passphraseStrength.clearAnimation()
            passphraseInput.text!!.clear()
            signInButton.text = "Create Keyspace account"
            signInButton.setOnClickListener {
                loadForCreatingAccount()
                signInButton.text = "Sign in to existing account"
                signInButton.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
                subtitlePassphrase.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
                descriptionPassphrase.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            }

            reenterPassphraseLayout.visibility = View.GONE
            reenterPassphraseLayout.clearAnimation()

            passphraseInput.doOnTextChanged { passphrase, start, before, count ->
                if (passphrase.toString().isNotEmpty()) {
                    createNewButton.isEnabled = passphrase.toString().length >= 8

                } else {
                    createNewButton.isEnabled = true
                    passphraseStrength.visibility = View.GONE
                    passphraseStrength.clearAnimation()
                }
            }

            createNewButton.setOnClickListener {
                loadFragment(EnterWordsFragment(email, passphraseInput.text.toString().toCharArray()))
            }

        }

        private fun loadForCreatingAccount() {
            subtitlePassphrase.text = requireContext().getString(R.string.subtitlePassphrase)
            descriptionPassphrase.text = requireContext().getString(R.string.descriptionPassphrase)
            signInButton.visibility = View.VISIBLE

            passphraseStrength.visibility = View.GONE
            reenterPassphraseLayout.visibility = View.GONE

            createNewButton.isEnabled = true

            val passphraseTextWatcher: TextWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable) {}
                override fun beforeTextChanged(passphrase: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(passphrase: CharSequence, start: Int, before: Int, count: Int) {
                    if (!passphraseStrength.isVisible) passphraseStrength.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
                    if (passphrase.toString().isNotEmpty()) {
                        passphraseStrength.visibility = View.VISIBLE
                        CoroutineScope(Dispatchers.IO).launch {
                            var feedback = ""
                            feedback = try {
                                Zxcvbn().measure(mutableListOf(passphrase)[0]).feedback.suggestions[0].toString() // sanitized input for Zxcvbn library Java
                            } catch (goodPassword: IndexOutOfBoundsException) {
                                "This is a great passphrase!"
                            }
                            withContext(Dispatchers.Main) {
                                passphraseStrength.text = feedback
                            }
                        }
                        if (passphrase.toString().length >= 8) {
                            signInButton.visibility = View.GONE
                            signInButton.animation = loadAnimation(requireContext(), R.anim.from_right)
                            subtitlePassphrase.text = "Almost there"
                            descriptionPassphrase.text = "Re-enter your passphrase and tap \"Create new account\" confirm"

                            if (!reenterPassphraseLayout.isVisible) {
                                reenterPassphraseLayout.visibility = View.VISIBLE
                                reenterPassphraseLayout.animation = loadAnimation(requireContext(), R.anim.slide_down)
                                passphraseStrength.animation = loadAnimation(requireContext(), R.anim.slide_down)
                            }

                            reenterPassphraseInput.doOnTextChanged { reenterPassphrase, start, before, count ->
                                if (passphrase.toString() != reenterPassphrase.toString()) {
                                    passphraseStrength.text = "Please make sure the above passphrases match."
                                    createNewButton.isEnabled = false
                                } else {
                                    passphraseStrength.text = "These passphrases match!"
                                    createNewButton.isEnabled = true
                                }
                            }

                        } else {
                            signInButton.visibility = View.VISIBLE
                            reenterPassphraseLayout.visibility = View.GONE
                            reenterPassphraseInput.text!!.clear()
                            createNewButton.isEnabled = false
                            reenterPassphraseLayout.clearAnimation()
                        }

                    } else {  // Empty passphrase, continue without any drama
                        createNewButton.isEnabled = true
                        subtitlePassphrase.text = requireContext().getString(R.string.subtitlePassphrase)
                        descriptionPassphrase.text = requireContext().getString(R.string.descriptionPassphrase)
                        passphraseStrength.startAnimation(loadAnimation(requireContext(), R.anim.to_right))
                        passphraseStrength.visibility = View.GONE
                    }

                    reenterPassphraseInput.text!!.clear()
                }
            }

            passphraseInput.addTextChangedListener(passphraseTextWatcher)

            createNewButton.setOnClickListener {
                loadFragment(WordsBlurbFragment(email, passphraseInput.text.toString().toCharArray()))
            }

            signInButton.setOnClickListener {
                loadForSigningIn()
                passphraseInput.removeTextChangedListener(passphraseTextWatcher)
            }

        }

    }

    class EmailFragment : Fragment() {
        lateinit var emailInput: TextInputEditText
        lateinit var emailLayout: TextInputLayout
        lateinit var emailContinue: MaterialButton
        lateinit var connectingProgress: ProgressBar
        lateinit var titleEmail: TextView
        lateinit var subtitleEmail: TextView
        lateinit var keyspaceLogo: ImageView
        var nextPressed = false

        lateinit var animatedLogo: AnimatedVectorDrawable
        lateinit var fallingBlocks: AnimatedVectorDrawable

        private lateinit var network: NetworkUtilities
        private lateinit var crypto: CryptoUtilities
        private lateinit var misc: MiscUtilities
        private lateinit var configData: SharedPreferences

        private var emailFragmentView: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? { // Inflate the layout for this fragment
            if (!nextPressed) {
                emailFragmentView = inflater.inflate(R.layout.onboarding_enter_email, container, false)

                crypto = CryptoUtilities(requireContext(), requireActivity() as AppCompatActivity)
                misc = MiscUtilities(requireContext())

                loadContent()
                checkStatusScreen()
                setLoadingScreen()

                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.Main) {
                        val network = NetworkUtilities(requireContext(), requireActivity() as AppCompatActivity, CryptoUtilities.Keyring(ByteArray(0), ByteArray(0), ByteArray(0)))
                        val backendStatus = network.keyspaceStatus()
                        animatedLogo.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                            override fun onAnimationEnd(drawable: Drawable) {
                                super.onAnimationEnd(drawable)
                                (drawable as AnimatedVectorDrawable).unregisterAnimationCallback(this)

                                if (backendStatus.status == "alive") {
                                    setAvailable()
                                    if (backendStatus.apiVersion != getString(R.string.vault_version)) setUpdateApp()
                                } else if (backendStatus.status == "dead") setUnavailable() else setBadConnection()
                            }
                        })
                    }
                }
            }

            return emailFragmentView

        }

        private fun setBadConnection() {
            fallingBlocks = ContextCompat.getDrawable(requireContext(), R.drawable.fallingblocks) as AnimatedVectorDrawable
            keyspaceLogo.setImageDrawable(fallingBlocks)
            fallingBlocks.start()
            connectingProgress.visibility = View.INVISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                connectingProgress.visibility = View.GONE
                emailLayout.visibility = View.GONE
                subtitleEmail.text = requireContext().resources.getString(R.string.connection_error)
                subtitleEmail.visibility = View.VISIBLE
                titleEmail.visibility = View.GONE
                emailContinue.visibility = View.VISIBLE
                emailContinue.text = requireContext().resources.getString(R.string.retry)
                keyspaceLogo.setImageDrawable(requireContext().getDrawable(R.drawable.ic_baseline_cloud_off_24))
                keyspaceLogo.startAnimation(loadAnimation(requireContext(), R.anim.fade_in))
                keyspaceLogo.animate().scaleX(0.85f).scaleY(0.85f)
                emailContinue.setIconResource(R.drawable.ic_baseline_refresh_24)
                subtitleEmail.startAnimation(loadAnimation(requireContext(), R.anim.from_bottom))
                emailContinue.startAnimation(loadAnimation(requireContext(), R.anim.from_bottom))
                emailContinue.isEnabled = true

                emailContinue.setOnClickListener {
                    requireActivity().finish()
                    val intent = requireActivity().baseContext.packageManager.getLaunchIntentForPackage(requireActivity().baseContext.packageName)
                    intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }

            }, 750)
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        private fun setUpdateApp() {
            fallingBlocks = ContextCompat.getDrawable(requireContext(), R.drawable.fallingblocks) as AnimatedVectorDrawable
            keyspaceLogo.setImageDrawable(fallingBlocks)
            fallingBlocks.start()
            connectingProgress.visibility = View.INVISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                connectingProgress.visibility = View.GONE
                emailLayout.visibility = View.GONE
                subtitleEmail.text = requireContext().resources.getString(R.string.update_text)
                subtitleEmail.visibility = View.VISIBLE
                titleEmail.visibility = View.GONE
                emailContinue.visibility = View.VISIBLE
                keyspaceLogo.setImageDrawable(requireContext().getDrawable(R.drawable.googleplay))
                keyspaceLogo.setColorFilter(subtitleEmail.currentTextColor)
                keyspaceLogo.startAnimation(loadAnimation(requireContext(), R.anim.fade_in))
                keyspaceLogo.animate().scaleX(0.85f).scaleY(0.85f)
                subtitleEmail.startAnimation(loadAnimation(requireContext(), R.anim.from_bottom))
                emailContinue.startAnimation(loadAnimation(requireContext(), R.anim.from_bottom))
                emailContinue.text = requireContext().resources.getString(R.string.play_store_text)
                emailContinue.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_open_in_new_24)
                emailContinue.isEnabled = true
                emailContinue.setOnClickListener {
                    requireActivity().finish()
                    requireActivity().finishAffinity()
                    requireActivity().finishAndRemoveTask()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireContext().packageName}")))
                }
            }, 750)
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        private fun setUnavailable() {
            fallingBlocks = ContextCompat.getDrawable(requireContext(), R.drawable.fallingblocks) as AnimatedVectorDrawable
            keyspaceLogo.setImageDrawable(fallingBlocks)
            fallingBlocks.start()
            connectingProgress.visibility = View.INVISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                connectingProgress.visibility = View.GONE
                emailLayout.visibility = View.GONE
                subtitleEmail.text = requireContext().resources.getString(R.string.maintenance_error)
                subtitleEmail.visibility = View.VISIBLE
                titleEmail.visibility = View.GONE
                emailContinue.visibility = View.VISIBLE
                emailContinue.text = requireContext().resources.getString(R.string.exit)
                keyspaceLogo.setImageDrawable(requireContext().getDrawable(R.drawable.ic_baseline_precision_manufacturing_24))
                keyspaceLogo.startAnimation(loadAnimation(requireContext(), R.anim.fade_in))
                keyspaceLogo.animate().scaleX(0.85f).scaleY(0.85f)
                emailContinue.setIconResource(R.drawable.ic_baseline_exit_to_app_24)
                subtitleEmail.startAnimation(loadAnimation(requireContext(), R.anim.from_bottom))
                emailContinue.startAnimation(loadAnimation(requireContext(), R.anim.from_bottom))
                emailContinue.isEnabled = true
                emailContinue.setOnClickListener {
                    requireActivity().finish()
                    requireActivity().finishAffinity()
                    requireActivity().finishAndRemoveTask()
                }
            }, 750)

        }

        private fun loadContent() {
            emailInput = emailFragmentView!!.findViewById(R.id.emailInput)
            emailInput.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            emailLayout = emailFragmentView!!.findViewById(R.id.emailLayout)
            emailContinue = emailFragmentView!!.findViewById(R.id.emailContinue)
            connectingProgress = emailFragmentView!!.findViewById(R.id.connectingProgress)
        }

        private fun checkStatusScreen() {
            connectingProgress.visibility = View.VISIBLE
            titleEmail = emailFragmentView!!.findViewById<TextView>(R.id.titleEmail)
            subtitleEmail = emailFragmentView!!.findViewById<TextView>(R.id.subtitleEmail)
            titleEmail.visibility = View.GONE
            subtitleEmail.visibility = View.GONE
            emailLayout.visibility = View.GONE
            emailContinue.visibility = View.GONE
        }

        private fun setLoadingScreen() {
            keyspaceLogo = emailFragmentView!!.findViewById(R.id.keyspace_logo)
            animatedLogo = ContextCompat.getDrawable(requireContext(), R.drawable.keyspace_animated_splash_slow) as AnimatedVectorDrawable
            keyspaceLogo.setImageDrawable(animatedLogo)
            animatedLogo.start()
        }

        private fun setAvailable() {
            connectingProgress.visibility = View.GONE
            emailLayout.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            emailContinue.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            titleEmail.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            subtitleEmail.startAnimation(loadAnimation(requireContext(), R.anim.from_right))
            titleEmail.visibility = View.VISIBLE
            subtitleEmail.visibility = View.VISIBLE
            emailLayout.visibility = View.VISIBLE
            emailContinue.visibility = View.VISIBLE
            emailContinue.isEnabled = false

            emailInput.doOnTextChanged { email, start, count, after ->
                val isEmailFilled = misc.isValidEmail(email.toString())
                emailContinue.isEnabled = isEmailFilled
            }

            emailContinue.setOnClickListener {
                nextPressed = true
                loadFragment(PassphraseFragment(emailInput.text.toString()))
            }

        }

    }

    lateinit var keystoreProgress: ProgressBar
    private fun loggedInBiometrics() {
        setContentView(R.layout.biometrics_screen)
        val authenticateButton = findViewById<MaterialButton>(R.id.authenticateButton)

        val authenticateTitle = findViewById<TextSwitcher>(R.id.authenticationTitleTextSwitcher)
        val authenticateDescription= findViewById<TextSwitcher>(R.id.authenticationDescriptionTextSwitcher)

        authenticateTitle.setInAnimation(applicationContext, R.anim.fade_in_fast)
        authenticateTitle.setOutAnimation(applicationContext, R.anim.fade_out_fast)
        authenticateDescription.setInAnimation(applicationContext, R.anim.fade_in_fast)
        authenticateDescription.setOutAnimation(applicationContext, R.anim.fade_out_fast)

        val authenticationIcon = findViewById<ImageView>(R.id.fingerprint_icon)
        val zoomSpin = loadAnimation(applicationContext, R.anim.zoom_spin)

        val keyguardToUnlock = AnimatedVectorDrawableCompat.create(applicationContext, R.drawable.keyguardtolock)
        val fingerprintToUnlock = AnimatedVectorDrawableCompat.create(applicationContext, R.drawable.fingerprinttolock)

        authenticateButton.setOnClickListener { loggedInBiometrics() }

        keystoreProgress = findViewById(R.id.keystoreProgress)
        keystoreProgress.visibility = View.VISIBLE

        val biometricPromptThread = Handler(Looper.getMainLooper())
        val executor: Executor = ContextCompat.getMainExecutor(this@StartHere) // execute on different thread awaiting response

        try {
            val biometricManager = BiometricManager.from(this@StartHere)
            val canAuthenticate =
                biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                Log.d("Keyspace", "Device lock found")
            } else {
                Log.d("Keyspace", "Device lock not set")
                throw NoSuchMethodError()
            }

            biometricPromptThread.removeCallbacksAndMessages(null)

            authenticateTitle.setText("Unlock Keyspace")
            if (misc.biometricsExist()) {
                authenticationIcon.setImageDrawable(applicationContext.getDrawable(R.drawable.ic_baseline_fingerprint_24))
                authenticateDescription.setText(getString(R.string.blurbDescriptionBiometrics))
            }
            else {
                authenticationIcon.setImageDrawable(applicationContext.getDrawable(R.drawable.ic_baseline_phonelink_lock_24))
                authenticateDescription.setText(getString(R.string.blurbDescriptionDeviceLock))
            }

            val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { // Authentication succeeded

                    authenticateButton.visibility = View.INVISIBLE
                    authenticateButton.isEnabled = false

                    authenticateDescription.setText("Authentication token")

                    Handler().postDelayed({
                        authenticateDescription.setText("Ed25519 public key")
                        Handler().postDelayed({ authenticateDescription.setText("Ed25519 private key")
                            Handler().postDelayed({ authenticateDescription.setText("XChaCha20-Poly1305 symmetric key") }, 50) }, 100) }, 100)

                    val keyringThread = Thread { keyring = crypto.retrieveKeys(crypto.getKeystoreMasterKey())!! }
                    keyringThread.start()

                    if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if strongbox exists
                        authenticateTitle.setText("Reading Strongbox")
                    } else if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) { // Check if hardware keystore exists
                        authenticateTitle.setText("Reading HAL Keystore")
                    } else authenticateTitle.setText("Reading Keystore")

                    if (misc.biometricsExist()) authenticationIcon.setImageDrawable(fingerprintToUnlock)
                    else authenticationIcon.setImageDrawable(keyguardToUnlock)

                    fingerprintToUnlock!!.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable) {
                            authenticationIcon.setImageDrawable(applicationContext.getDrawable(R.drawable.ic_baseline_check_24))
                            keyringThread.join()

                            zoomSpin.setAnimationListener(object : AnimationListener {

                                override fun onAnimationStart(animation: Animation) {
                                    authenticateTitle.setText("All done!")
                                    authenticateDescription.setText("daaaammn if you can read this you have eagle eyes... \uD83E\uDD85")
                                }

                                override fun onAnimationRepeat(animation: Animation) {  }

                                @SuppressLint("UseCompatLoadingForDrawables")
                                override fun onAnimationEnd(animation: Animation) {
                                    keystoreProgress.visibility = View.INVISIBLE
                                    startDashboard()
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
                    authenticationIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_24))
                    authenticateTitle.setText("Authentication failed")
                    (applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(150)
                    findViewById<LinearLayout>(R.id.fingerprint_icon_layout).startAnimation(loadAnimation(applicationContext, R.anim.wiggle))
                    keystoreProgress.visibility = View.INVISIBLE
                    Log.d("Keyspace", "Authentication canceled")
                }

                override fun onAuthenticationFailed() { // Authentication failed. User may not have been recognized
                    biometricPromptThread.removeCallbacksAndMessages(null)
                    authenticationIcon.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_24))
                    authenticateTitle.setText("Authentication failed")
                    (applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(150)
                    findViewById<LinearLayout>(R.id.fingerprint_icon_layout).startAnimation(loadAnimation(applicationContext, R.anim.wiggle))
                    keystoreProgress.visibility = View.INVISIBLE
                    Log.d("Keyspace", "Incorrect credentials supplied")
                }
            }

            val biometricPrompt = BiometricPrompt(this@StartHere, executor, authenticationCallback)

            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(resources.getString(R.string.app_name))
                .setSubtitle(resources.getString(R.string.biometrics_generic_subtitle))
                .setDescription(resources.getString(R.string.biometrics_modify_item_description))
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            builder.setConfirmationRequired(true)

            val promptInfo = builder.build()
            biometricPrompt.authenticate(promptInfo)

            biometricPromptThread.postDelayed({
                biometricPrompt.cancelAuthentication()
                biometricPromptThread.removeCallbacksAndMessages(null)
                keystoreProgress.visibility = View.INVISIBLE
                val timeoutDialogBuilder = MaterialAlertDialogBuilder(this@StartHere)
                timeoutDialogBuilder.setTitle("Authentication error")
                timeoutDialogBuilder.setMessage("Authentication timed out because you waited too long.\n\nPlease try again.")
                timeoutDialogBuilder.setNegativeButton("Retry") { _, _ ->
                    loggedInBiometrics()
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
            keystoreProgress.visibility = View.INVISIBLE
            val builder = MaterialAlertDialogBuilder(this@StartHere)
            builder.setTitle("No biometric hardware")
            builder.setMessage("Your biometric sensors (fingerprint, face ID or iris scanner) could not be accessed. Please add biometrics from your phone's settings to continue.\n\nTry restarting your phone if you have already enrolled biometrics.")
            builder.setNegativeButton("Exit") { _, _ ->
                this@StartHere.finishAffinity()
            }
            val errorDialog: AlertDialog = builder.create()
            errorDialog.setCancelable(true)
            errorDialog.show()

            Log.e("Keyspace", "Please set a screen lock.")
            noLockSet.stackTrace

        } catch (incorrectCredentials: Exception) {
            biometricPromptThread.removeCallbacksAndMessages(null)
            incorrectCredentials.printStackTrace()
            keystoreProgress.visibility = View.INVISIBLE
            val builder = MaterialAlertDialogBuilder(this@StartHere)
            builder.setTitle("Authentication failed")
            builder.setMessage("Your identity couldn't be verified. Please try again after a while.")
            builder.setNegativeButton("Exit") { _, _ ->
                this@StartHere.finishAffinity()
            }
            val errorDialog: AlertDialog = builder.create()
            errorDialog.setCancelable(true)
            errorDialog.show()

            Log.e("Keyspace", "Your identity could not be verified.")
            incorrectCredentials.stackTrace

        }

    }

    private fun startDashboard () {
        crypto.secureStartActivity(
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
            keyring = keyring,
            itemId = null
        )

    }

    override fun onBackPressed() {

        fun exitApp () {
            val dialogBuilder = MaterialAlertDialogBuilder(this@StartHere)
            dialogBuilder
                .setCancelable(true)
                .setTitle("Exit Keyspace")
                .setIcon(R.drawable.ic_baseline_exit_to_app_24)
                .setMessage("Would you like to exit Keyspace?")
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Yes") { dialog, _ -> finish() }

            val alert = dialogBuilder.create()
            alert.show()
        }

        if (configData.getBoolean("isLoggedIn", false)) {
            try {
                biometricPrompt.cancelAuthentication()
            } catch (noPrompt: UninitializedPropertyAccessException) { }
            exitApp()
        } else {
            if (_supportFragmentManager.backStackEntryCount <= 1) {
                exitApp ()
            } else {
                supportFragmentManager.popBackStack()
            }
        }

    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        try {
            biometricPrompt.cancelAuthentication()
        } catch (noPrompt: UninitializedPropertyAccessException) {
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            biometricPrompt.cancelAuthentication()
        } catch (noPrompt: UninitializedPropertyAccessException) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            biometricPrompt.cancelAuthentication()
        } catch (noPrompt: UninitializedPropertyAccessException) {
        }
    }

}

private fun quitApp (context: Context, restart: Boolean) {
    (context as Activity).finish()
    if (restart) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }
}

fun loadFragment(fragment: Fragment) {
    val fragmentManager: FragmentManager = _supportFragmentManager
    fragmentManager.commit {
        setCustomAnimations(R.anim.from_right, 0, R.anim.from_right, R.anim.to_right)
        setReorderingAllowed(false)
        replace(R.id.onboardingRoot, fragment) // replace the FrameLayout with new Fragment
        addToBackStack(null)
    }
}