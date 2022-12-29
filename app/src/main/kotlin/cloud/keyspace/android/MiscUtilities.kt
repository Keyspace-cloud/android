package cloud.keyspace.android

import android.R.drawable
import android.R.raw
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.biometric.BiometricManager
import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toEntropy
import com.github.sumimakito.awesomeqr.AwesomeQrRenderer
import com.github.sumimakito.awesomeqr.option.RenderOption
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pixplicity.sharp.OnSvgElementListener
import com.pixplicity.sharp.Sharp
import org.json.JSONObject
import java.lang.reflect.Field
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.*


/**
 * Miscellaneous utilities such as datatype converters, 2FA code generators, QR code scanners etc live here.
 * // @param context The context of the activity that a `MiscUtilities` object is initialized in, example: `applicationContext`, `this` etc.
 */
class MiscUtilities (applicationContext: Context) {
    val context = applicationContext
    fun getPaymentGateway(cardNumber: String): String? {
        try {
            if (cardNumber.startsWith("4") ||
                cardNumber.startsWith("4026") ||
                cardNumber.startsWith("411750") ||
                cardNumber.startsWith("4508") ||
                cardNumber.startsWith("4913") ||
                cardNumber.startsWith("4917") ||
                cardNumber.startsWith("4844")) {
                return "visa"
            } else if (
                (cardNumber.take(4)).toInt() in 2221..2720 ||
                (cardNumber.take(2)).toInt() in 51..55 ||
                (cardNumber.take(4)).toInt() in 5100..5399) {
                return "mastercard"
            } else if (
                (cardNumber.take(4)).toInt() in 622126..622925 ||
                (cardNumber.take(3)).toInt() in 644..649 ||
                cardNumber.startsWith("66")) {
                return "discover"
            } else if (
                cardNumber.startsWith("60") ||
                cardNumber.startsWith("6521") ||
                cardNumber.startsWith("6522") ||
                cardNumber.startsWith("50")) {
                return "rupay"
            } else if (
                cardNumber.startsWith("34") ||
                cardNumber.startsWith("37")) {
                return "americanExpress"
            } else {
                return null
            }
        } catch (noNumber: NumberFormatException) {
            return null
        }
    }

    fun checkIfCardExpired (expiryDate:String): String? {
        var status: String? = null
        val year = expiryDate.substringAfter("/")
        val month = expiryDate.substringBefore("/")
        if (!year.isNullOrEmpty() || !month.isNullOrEmpty()) {
            val yearInt = year.toInt()
            val monthInt = month.toInt()

            val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString().takeLast(2).toInt()
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH).toString().toInt()+1

            if (
                (currentYear > yearInt)
                || (currentYear == yearInt && currentMonth > monthInt)
            ) {
                status = "Card expired"
            } else if (
                (currentYear == yearInt && currentMonth + 3 >= monthInt)
                || (currentYear == yearInt && currentMonth == monthInt)
            ) {
                status = "Expiring soon"
            }
        } else status = null
        return status
    }

    // password generator
    fun passwordGenerator (length: Int, uppercase: Boolean, lowercase: Boolean, numbers: Boolean, symbols: Boolean): String {
        var inputAlphabet = ""
        if (uppercase) inputAlphabet += "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (lowercase) inputAlphabet += "abcdefghijklmnopqrstuvwxyz"
        if (numbers) inputAlphabet += "0123456789"
        if (symbols) inputAlphabet += """~`!@#£€$¢¥§%°^&*()-_+={}[]|\\/:;\"₹'<>,.?"""
        // if (!uppercase && !lowercase && !numbers && !symbols) throw IllegalArgumentException()
        val random = SecureRandom.getInstanceStrong()
        val stringBuilder: java.lang.StringBuilder = java.lang.StringBuilder(length)
        for (i in 0 until length) stringBuilder.append(inputAlphabet[random.nextInt(inputAlphabet.length)])
        return stringBuilder.toString()
    }

    fun passphraseGenerator(length: Int): String {
        val words: MutableList<String> = mutableListOf()
        val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_24.toEntropy())
        mnemonicCode.validate()
        for (word in mnemonicCode) {
            words.add(word)
            if (words.size == length) break
        }
        return words.joinToString("-")
    }

    fun passwordStrength (password: String): Int {
        var score = 0
        if (password.length in 0..16) score = 3
        else if (password.length in 17..32) score = 6
        else if (password.length in 33..512) score = 10
        return score
    }

    fun passwordStrengthRating (score: Int): String {
        var strength = "Strong"
        when (score) {
            3 -> strength = "Weak"
            6 -> strength = "Good"
            10 -> strength = "Strong"
        }
        return strength
    }

    fun color (color: String?): Int {
        var colorData: Int = Color.parseColor("#000000")
        if (color == null) return Color.parseColor("#000000")
        when (color.lowercase()) {
            "red" -> colorData = Color.parseColor("#EF9A9A")
            "orange" -> colorData = Color.parseColor("#FFAB91")
            "yellow" -> colorData = Color.parseColor("#FFF59D")
            "blue" -> colorData = Color.parseColor("#81D4FA")
            "green" -> colorData = Color.parseColor("#C5E1A5")
            "brown" -> colorData = Color.parseColor("#D3A495")
            "pink" -> colorData = Color.parseColor("#FFDDDD")
            "purple" -> colorData = Color.parseColor("#E1BEE7")
        }
        return colorData
    }

    fun generateProfilePicture(email: String): Drawable {

        fun generateColorHash (email: String): Int {
            var hashCode = email.hashCode()
            val colorArray: ArrayList<String> =  context.resources.getStringArray(R.array.vault_item_colors).toList() as ArrayList<String>
            if (hashCode < 0) hashCode = -hashCode
            hashCode = (hashCode.toString().toCharArray()[2].code * hashCode.toString().toCharArray()[4].code).toString().takeLast(2).toInt() - 5
            return try { Color.parseColor(colorArray[hashCode]) } catch (unsupportedValues: ArrayIndexOutOfBoundsException) { context.getColor(R.color.lightFinesseColor) }
        }

        val text = email.first().toString().uppercase()

        val paint = Paint(ANTI_ALIAS_FLAG)
        paint.textSize = 400f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        val baseline: Float = -paint.ascent() + 32

        var width = (paint.measureText(text) + 0.0f)
        var height = (baseline + paint.descent() + 0.0f)

        val trueWidth = width
        if (width > height) height = width else width = height
        val image = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(image)
        canvas.drawColor(generateColorHash(email))
        canvas.drawText (text, width/2 - trueWidth/2, baseline, paint)

        return BitmapDrawable(context.resources, image)
    }

    fun isValidPackageName (packageName: String): Boolean {
        val isPackage: Boolean
        val isRegexMatched = Regex("^(?:[a-zA-Z]+(?:\\d*[a-zA-Z_]*)*)(?:\\.[a-zA-Z]+(?:\\d*[a-zA-Z_]*)*)+\$").containsMatchIn(packageName)
        isPackage = isRegexMatched
        return isPackage
    }

    fun grabURLFromString (text: String): String? {
        var url: String? = null
        val urlRegex = Regex("""^(http:\/\/www\.|https:\/\/www\.|http:\/\/|https:\/\/)?[a-z0-9]+([\-\.]{1}[a-z0-9]+)*\.[a-z]{2,5}(:[0-9]{1,5})?(\/.*)?$""")
        val ipAddressRegex = Regex("""((\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(:\d{1,5})*(\/*.*)*)""")

        if (urlRegex.containsMatchIn(text)) {
            url = urlRegex.findAll(text).map { it.groupValues[0] }.joinToString()
        } else if (ipAddressRegex.containsMatchIn(text)) {
            url = ipAddressRegex.findAll(text).map { it.groupValues[0] }.joinToString()
        }

        return url
    }

    /* example: otpauth://hotp/AbcIssuer:XyzAccount?secret=secret123&algorithm=SHA512&digits=12&period=300&lock=false&counter=0&label=defLabel */
    data class MfaCode(
        val type: String?,
        val mode: String?,
        val issuer: String?,
        val account: String?,
        val secret: String?,
        val algorithm: String?,
        val digits: Int?,
        val period: Int?,
        val lock: Boolean?,
        val counter: Int?,
        val label: String?,
    )

    fun decodeOTPAuthURL (OTPAuthURL: String): MfaCode? {
        val url = URLDecoder.decode(OTPAuthURL, "UTF-8")
        if (url.contains("otpauth") && url.contains("://")) {
            val type: String =
                if (url.substringBefore("://").contains("migration")) "Google Authenticator Backup"
                else "OTP"
            val mode: String =
                if (url.substringAfter("://").substringBefore("/").contains("totp"))
                    "totp"
                else "hotp"
            val issuer: String? = if (url.contains("issuer")) url.substringAfter("issuer=").substringBefore("&") else null
            val account: String? = if (url.contains("otp")) url.substringAfter("otp/").substringBefore("?").replace(":", "") else null
            val secret: String? = if (url.contains("secret")) url.substringAfter("secret=").substringBefore("&") else null
            val algorithm: String? = if (url.contains("algorithm")) url.substringAfter("algorithm=").substringBefore("&") else null
            val digits: Int? = if (url.contains("digits")) url.substringAfter("digits=").substringBefore("&").toInt() else null
            val period: Int? = if (url.contains("period")) url.substringAfter("period=").substringBefore("&").toInt() else null
            val lock: Boolean? = if (url.contains("lock")) url.substringAfter("lock=").substringBefore("&").toBoolean() else null
            val counter: Int? = if (url.contains("counter")) url.substringAfter("counter=").substringBefore("&").toInt() else null
            val label: String? = if (url.contains("label")) url.substringAfter("label=").substringBefore("&") else null

            return MfaCode (
                type = type,
                mode = mode,
                issuer = issuer,
                account = account,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                period = period,
                lock = lock,
                counter = counter,
                label = label,
            )
        } else {
            return null
        }
    }

    fun encodeOTPAuthURL (mfaCodeObject: MfaCode): String? {
        lateinit var type: String
        lateinit var issuer: String
        lateinit var account: String
        lateinit var secret: String

        if (mfaCodeObject.type.toString().isNotEmpty())
            type = if (mfaCodeObject.type.toString().lowercase().contains("backup") || mfaCodeObject.type.toString().lowercase().contains("migration")) "otpauth-migration" else "otpauth"
        else return null

        val mode: String = if (mfaCodeObject.mode.toString().isNotEmpty())
            if (mfaCodeObject.mode.toString().lowercase().contains("time") || mfaCodeObject.mode.toString().contains("totp")) "totp" else "hotp"
        else "totp"

        if (mfaCodeObject.account.toString().isNotEmpty())
            account = mfaCodeObject.account.toString()
        else return null

        issuer = mfaCodeObject.issuer.toString().ifEmpty { account }

        if (mfaCodeObject.secret.toString().isNotEmpty())
            secret = mfaCodeObject.secret.toString().trim().trim().replace(" ", "")
        else return null

        val algorithm: String = mfaCodeObject.algorithm.toString().ifEmpty { "SHA1" }

        val digits: String = if (mfaCodeObject.digits.toString().isNotEmpty())
            mfaCodeObject.digits.toString()
        else "6"

        val period: String = mfaCodeObject.period.toString().ifEmpty { "30" }

        val lock: String = mfaCodeObject.lock.toString().ifEmpty { "false" }

        val counter: String = mfaCodeObject.counter.toString().ifEmpty { "0" }

        val label: String = mfaCodeObject.label.toString().ifEmpty { "label" }

        return "$type://$mode/$issuer:$account?secret=$secret&algorithm=$algorithm&digits=$digits&period=$period&lock=$lock&counter=$counter&label=$label"
    }

    fun shrinkDrawable(drawable: Drawable, height: Int, width: Int): Drawable {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return BitmapDrawable(bitmap)
    }

    fun hexToAscii(hexStr: String): String {
        val output = StringBuilder("")
        var i = 0
        while (i < hexStr.length) {
            val str = hexStr.substring(i, i + 2)
            output.append(str.toInt(16).toChar())
            i += 2
        }
        return output.toString()
    }

    fun areSimilar (string1: String, string2: String): Boolean {
        val strippedString1 = string1
            .lowercase(Locale.getDefault()) // ignore case
            .replace(" ", "") // remove spaces
            .replace(".", "") // remove dots
            .replace("-", "") // remove hyphens
            .replace("_", "") // remove hyphens
            .replace(Regex("[^\\w]"), "")
            .replace(Regex("\\d"), "")
            .replace("_", "")
            .replace(" ", "")
            .lowercase()
            .trim()

        val strippedString2 = string2
            .lowercase(Locale.getDefault()) // ignore case
            .replace(" ", "") // remove spaces
            .replace(".", "") // remove dots
            .replace("-", "") // remove hyphens
            .replace("_", "") // remove hyphens
            .replace(Regex("[^\\w]"), "")
            .replace(Regex("\\d"), "")
            .replace("_", "")
            .replace(" ", "")
            .lowercase()
            .trim()

        return strippedString1 == strippedString2

    }

    fun scanQrCode (viewfinderMessage: String): String {
        return viewfinderMessage
    }

    fun generateQrCode (text: String): Bitmap? {
        val colorPalette = com.github.sumimakito.awesomeqr.option.color.Color()
        colorPalette.light = 0xFFFFFFFF.toInt() // for blank spaces
        colorPalette.dark = 0xFF000000.toInt() // for non-blank spaces
        colorPalette.background = 0xFFFFFFFF.toInt() // for the background (will be overriden by background images, if set)
        colorPalette.auto = false // set to true to automatically pick out colors from the background image (will only work if background image is present)

        val renderOption = RenderOption()
        renderOption.content = text // content to encode
        renderOption.size = 700 // size of the final QR code image
        renderOption.ecl = ErrorCorrectionLevel.Q // (optional) specify an error correction level
        renderOption.patternScale = 1f // (optional) specify a scale for patterns
        renderOption.color = colorPalette // set a color palette for the QR cod

        val qrCodeBitmap = AwesomeQrRenderer.render(renderOption)
        return qrCodeBitmap.bitmap
    }

    fun toMap(jsonObject: JSONObject): Map<String, Any?> {
        val map: MutableMap<String, String> = HashMap()
        try {
            val keys: Iterator<*> = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next() as String
                val value = jsonObject.getString(key)
                map[key] = value
            }
        } catch (dataError: Exception) {
            Log.e("Keyspace", "Invalid JSON file.")
            dataError.toString()
        }
        return map
    }

    fun isValidEmail(email: String?): Boolean {
        return if (email == null)
            false
        else
            Regex("^\\w+([-+.']\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*\$").containsMatchIn(email)
                    && email.length > 5
                    && email.substringAfterLast(".").length > 1
    }

    @SuppressLint("DiscouragedApi")
    fun getSiteIcon(siteName: String, color: Int?): Drawable? {

        var trimmedSiteName = siteName
            .lowercase()
            .replace(" ", "")

        val svg: Sharp? = try {
            Sharp.loadResource(context.resources, context.resources.getIdentifier(trimmedSiteName, "raw", context.packageName))
        } catch (_: Exception) {
            trimmedSiteName = trimmedSiteName.replace(("[^\\w\\d ]").toRegex(), "")
            try {
                Sharp.loadResource(context.resources, context.resources.getIdentifier(trimmedSiteName, "raw", context.packageName))
            } catch (_: Exception) {
                trimmedSiteName = "_${trimmedSiteName}"
                try {
                    Sharp.loadResource(context.resources, context.resources.getIdentifier(trimmedSiteName, "raw", context.packageName))
                } catch (_: Exception) {
                    null
                }
            }
        }

        svg?.setOnElementListener(object : OnSvgElementListener {
            override fun onSvgStart(canvas: Canvas, bounds: RectF?) { }
            override fun onSvgEnd(canvas: Canvas, bounds: RectF?) { }
            override fun <T : Any> onSvgElementDrawn(id: String?, element: T, canvas: Canvas, paint: Paint?) { }

            override fun <T : Any> onSvgElement(p0: String?, p1: T, elementBounds: RectF?, canvas: Canvas, canvasBounds: RectF?, paint: Paint?): T {

                if (color != null) {
                    paint?.color = Color.argb (
                        255,
                        Color.red(color),
                        Color.blue(color),
                        Color.green(color)
                    )
                } else {
                    val palette = context.resources.getColor(android.R.attr.textColorPrimary)
                    paint?.color = Color.argb (
                        255,
                        Color.red(palette),
                        Color.blue(palette),
                        Color.green(palette)
                    )
                }

                return p1
            }

        })

        return try { svg?.drawable?.current } catch (_: Exception) { null }

    }

    fun getSiteIconFilenames (): ArrayList<String> {
        val filenames = arrayListOf<String>()
        val rawResources = raw()
        val rawClass = R.raw::class.java
        val fields: Array<out Field> = rawClass.declaredFields

        for (element in fields) {
            val filename = element.name
            filenames.add(filename)
        }

        return filenames
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) } // to print, not related to crypto

    fun screenLockEnabled() : Boolean {

        var isKeyguardSet = false // don't allow successful authentication by default

        try {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

            isKeyguardSet = if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                Log.d("Keyspace", "Device lock found")
                true
            } else {
                Log.e("Keyspace", "Device lock not set")
                throw NoSuchMethodError()
                false
            }

        } catch (noLockSet: NoSuchMethodError) {
            isKeyguardSet = false
            Log.e("Keyspace", "Please set a screen lock.")
            noLockSet.stackTrace

        } catch (incorrectCredentials: Exception) {
            isKeyguardSet = false
            Log.e("Keyspace", "Your identity could not be verified.")
            incorrectCredentials.stackTrace
        }

        return isKeyguardSet
    }

    fun biometricsExist() : Boolean {
        var isKeyguardSet = false // don't allow successful authentication by default

        try {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)

            isKeyguardSet = if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                Log.d("Keyspace", "Device lock found")
                true
            } else {
                Log.e("Keyspace", "Device lock not set")
                throw NoSuchMethodError()
                false
            }

        } catch (noLockSet: NoSuchMethodError) {
            isKeyguardSet = false
            Log.e("Keyspace", "Please set a screen lock.")
            noLockSet.stackTrace

        } catch (incorrectCredentials: Exception) {
            isKeyguardSet = false
            Log.e("Keyspace", "Your identity could not be verified.")
            incorrectCredentials.stackTrace
        }

        return isKeyguardSet
    }

    fun backup2faCodesToList(pastedCodesAsString: String): List<String> {
        var pastedCodes: String = pastedCodesAsString
        pastedCodes = Regex("(([0-9a-zA-Z ]{4,} +[0-9a-zA-Z]{4,}+)|([0-9a-zA-Z-]{4,}))").findAll(pastedCodes).map { it.groupValues[1] }.joinToString()
        val trim1 = pastedCodes.trim().split(",").toList()
        val trimList1: MutableList<String> = mutableListOf()
        for (item in trim1) {
            trimList1.add(item.trim())
        }
        val trimList2: MutableList<String> = mutableListOf()
        for (item in trimList1) {
            if (item.any {it in "0123456789/?!:;%"}) {
                trimList2.add(item)
            }
        }
        return trimList2
    }

    fun containsNonAlphabet (string: String): Boolean {
        return (
                string.contains(" ") ||
                string.contains(Regex("""[!@#$%?,^&*)(+=._\-<>{}\[\]|]+$""")) ||
                string.contains(Regex("""[0-9].*"""))
        )
    }

    fun checkIfListContainsSubstring (list: List<String>, searchTerm: String):  Boolean {
        val newList = mutableListOf<String>()
        if (searchTerm.isEmpty()) return false
        val searchTerm = searchTerm.replace(Regex("[^A-Za-z0-9 ]"), "").replace("", "").replace(" ", "").lowercase()
        for (item in list) newList.add(item.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "").lowercase())
        for (item in newList) if (item.equals (searchTerm, ignoreCase = true)) return true
        return false
    }

    fun stringToNumberedString (string: String): String {
        var string = string
        if (string.replace(" ", "").isNotEmpty()) {
            var lineBreakCounter = 1
            if (string.contains("\n")) {
                val stringCharacters = mutableListOf<Char>()
                stringCharacters.add(lineBreakCounter.toString().single())
                stringCharacters.add('.')
                stringCharacters.add(' ')
                lineBreakCounter += 1
                for (c in string) {
                    stringCharacters.add(c)
                    if (c == '\n') {
                        stringCharacters.add(lineBreakCounter.toString().single())
                        stringCharacters.add('.')
                        stringCharacters.add(' ')
                        lineBreakCounter += 1
                    }
                }
                string = String(stringCharacters.toCharArray())
            } else {
                string = "\n1. $string"
            }
        } else {
            string = "\n1. one\n2. two\n3. three\n"
        }
        return string
    }

    fun stringToBulletedString (string: String): String {
        var string = string
        if (string.replace(" ", "").isNotEmpty()) {
            string = if (string.contains("\n")) {
                val stringCharacters = mutableListOf<Char>()
                stringCharacters.add('-')
                stringCharacters.add(' ')
                for (c in string) {
                    stringCharacters.add(c)
                    if (c == '\n') {
                        stringCharacters.add('-')
                        stringCharacters.add(' ')
                    }
                }
                String(stringCharacters.toCharArray())
            } else {
                "\n- $string"
            }
        } else {
            string = "\n- one\n- two\n- three\n"
        }
        return string
    }

    fun stringToUncheckedString (string: String): String {
        var string = string
        if (string.replace(" ", "").isNotEmpty()) {
            string = if (string.contains("\n")) {
                val stringCharacters = mutableListOf<Char>()
                stringCharacters.add('-')
                stringCharacters.add(' ')
                stringCharacters.add('[')
                stringCharacters.add(' ')
                stringCharacters.add(']')
                stringCharacters.add(' ')
                for (c in string) {
                    stringCharacters.add(c)
                    if (c == '\n') {
                        stringCharacters.add('-')
                        stringCharacters.add(' ')
                        stringCharacters.add('[')
                        stringCharacters.add(' ')
                        stringCharacters.add(']')
                        stringCharacters.add(' ')
                    }
                }
                String(stringCharacters.toCharArray())
            } else {
                "\n- [ ] $string"
            }
        } else {
            string = "\n- [ ] one\n- [ ] two\n- [ ] three\n"
        }
        return string
    }

    fun stringToCheckedString (string: String): String {
        var string = string
        if (string.replace(" ", "").isNotEmpty()) {
            string = if (string.contains("\n")) {
                val stringCharacters = mutableListOf<Char>()
                stringCharacters.add('-')
                stringCharacters.add(' ')
                stringCharacters.add('[')
                stringCharacters.add('x')
                stringCharacters.add(']')
                stringCharacters.add(' ')
                for (c in string) {
                    stringCharacters.add(c)
                    if (c == '\n') {
                        stringCharacters.add('-')
                        stringCharacters.add(' ')
                        stringCharacters.add('[')
                        stringCharacters.add('x')
                        stringCharacters.add(']')
                        stringCharacters.add(' ')
                    }
                }
                String(stringCharacters.toCharArray())
            } else {
                "\n- [x] $string"
            }
        } else {
            string = "\n- [x] one\n- [x] two\n- [x] three\n"
        }
        return string
    }

    fun stringToTitledStrings (string: String): String {
        var string = string
        if (string.replace(" ", "").isNotEmpty()) {
            string = if (string.contains("\n")) {
                val stringCharacters = mutableListOf<Char>()
                stringCharacters.add('\n')
                stringCharacters.add('#')
                stringCharacters.add(' ')
                for (c in string) {
                    stringCharacters.add(c)
                    if (c == '\n') {
                        stringCharacters.add('\n')
                        stringCharacters.add('#')
                        stringCharacters.add(' ')
                    }
                }
                String(stringCharacters.toCharArray()) + "\n"
            } else {
                "# $string\n"
            }
        } else {
            string = "# "
        }
        return string
    }

// UI stuff
open class OnSwipeTouchListener(c: Context?) : View.OnTouchListener {
    private val gestureDetector: GestureDetector
    override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
        return gestureDetector.onTouchEvent(motionEvent!!)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick()
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleClick()
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            onLongClick()
            super.onLongPress(e)
        }

        // Determines the fling velocity and then fires the appropriate swipe event accordingly
        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val result = false
            try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            onSwipeDown()
                        } else {
                            onSwipeUp()
                        }
                    }
                }
            } catch (exception: java.lang.Exception) {
                exception.printStackTrace()
            }
            return result
        }

    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeUp() {}
    open fun onSwipeDown() {}
    open fun onClick() {}
    fun onDoubleClick() {}
    open fun onLongClick() {}

    init {
        gestureDetector = GestureDetector(c, GestureListener())
    }
}}