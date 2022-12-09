package cloud.keyspace.android

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.utils.HexMessageEncoder
import com.sun.jna.NativeLong
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime


class DeveloperOptions : AppCompatActivity() {
    fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.developer_options)

        val crypto = CryptoUtilities(applicationContext, this@DeveloperOptions)

        val blake2bOutput = findViewById<TextView>(R.id.blake2bOutput)
        val blake2bPassphraseInput = findViewById<EditText>(R.id.blake2bPassphraseInput)
        val blake2bWordsInput = findViewById<EditText>(R.id.blake2bWordsInput)
        val blake2bHashButton = findViewById<MaterialButton>(R.id.blake2bHashButton)

        blake2bHashButton.setOnClickListener {
            val bip39Seed: ByteArray? = crypto.wordsToSeed(blake2bWordsInput.text.toString().toCharArray(), blake2bPassphraseInput.text.toString().toCharArray())

            var rootSignSeed: ByteArray?
            val rootSignSeedElapsedTime = measureNanoTime {
                rootSignSeed = crypto.kdf (
                    masterKey = bip39Seed!!,
                    context = crypto.ROOT_SIGNED_SEED_CONTEXT,
                    keyId = crypto.ROOT_SIGNED_SEED_KEY_ID
                )
            }

            var vaultKey: ByteArray?
            val vaultKeyElapsedTime = measureNanoTime {
                vaultKey = crypto.kdf (
                    masterKey = bip39Seed!!,
                    context = crypto.VAULT_KEY_CONTEXT,
                    keyId = crypto.VAULT_KEY_KEY_ID
                )
            }

            val ed25519Keypair = crypto.ed25519Keypair(rootSignSeed!!)

            val zdt: ZonedDateTime = ZonedDateTime.now()
            val output = """
________________
Timestamp: ${zdt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}
Passphrase (as UTF8 String): ${blake2bPassphraseInput.text}
Words input: ${blake2bWordsInput.text}
Bip39 seed from words: ${bip39Seed?.toHexString()}
Words ByteArray size: ${bip39Seed?.size}
---
Time taken: $rootSignSeedElapsedTime nanoseconds / ${TimeUnit.MILLISECONDS.convert(rootSignSeedElapsedTime, TimeUnit.NANOSECONDS)} milliseconds
rootSignSeed: ${rootSignSeed?.toHexString()}
---
Time taken: $vaultKeyElapsedTime nanoseconds / ${TimeUnit.MILLISECONDS.convert(vaultKeyElapsedTime, TimeUnit.NANOSECONDS)} milliseconds
vaultKey: ${vaultKey?.toHexString()}
---
privateKey: ${ed25519Keypair.privateKey.toHexString()}
publicKey: ${ed25519Keypair.publicKey.toHexString()}
"""

            blake2bOutput.text = output + blake2bOutput.text

            Log.wtf("KeyspaceDevOptions -> vaultKey TEST", vaultKey?.toHexString())
            Log.wtf("KeyspaceDevOptions -> rootSignSeed TEST", rootSignSeed?.toHexString())
            Log.wtf("KeyspaceDevOptions -> privateKey TEST", ed25519Keypair.privateKey.toHexString())
            Log.wtf("KeyspaceDevOptions -> publicKey TEST", ed25519Keypair.publicKey.toHexString())
        }

        var algorithm: PwHash.Alg = PwHash.Alg.PWHASH_ALG_ARGON2I13

        val argon2AlgoPicker = findViewById<RadioGroup>(R.id.argon2AlgoPicker)
        var algoAsString = "argon2i13"
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val saltInput = findViewById<EditText>(R.id.saltInput)
        val hashLengthInput = findViewById<EditText>(R.id.hashLength)
        val opsLimitInput = findViewById<EditText>(R.id.opsLimitInput)
        val memoryLimitInput = findViewById<EditText>(R.id.memoryLimitInput)
        val argon2HashButton = findViewById<MaterialButton>(R.id.argon2HashButton)
        val argon2Output = findViewById<TextView>(R.id.argon2Output)

        argon2AlgoPicker.setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener { arg0, id ->
            when (id) {
                R.id.argon2i13 -> {
                    algorithm = PwHash.Alg.PWHASH_ALG_ARGON2I13
                    algoAsString = "argon2i13"
                }
                R.id.argon2id13 -> {
                    algorithm = PwHash.Alg.PWHASH_ALG_ARGON2ID13
                    algoAsString = "argon2id13"
                }
            }
        })

        val sha256 = crypto.choppedSha256(saltInput.text.toString().toCharArray())
        val seed = crypto.wordsToSeed(saltInput.text.toString().toCharArray(), passwordInput.text.toString().toCharArray())!!

        argon2Output.text = ""
        argon2HashButton.setOnClickListener {

            val sodium = SodiumAndroid()
            val lazySodium = LazySodiumAndroid(sodium, StandardCharsets.UTF_8)

            try {

                lateinit var hash: String
                val elapsedTime = measureNanoTime {
                    hash = lazySodium.cryptoPwHash(
                        passwordInput.text.toString(),
                        hashLengthInput.text.toString().toInt(),
                        sha256,
                        opsLimitInput.text.toString().toLong(),
                        NativeLong(memoryLimitInput.text.toString().toLong()), // if using KiB, convert to kB and cast to NativeLong (due to LibSodium using C / NDK)
                        algorithm
                    )
                }

                val byteArrayHash = HexMessageEncoder().decode(hash)

                val zdt: ZonedDateTime = ZonedDateTime.now()
                val output = """
________________
Timestamp: ${zdt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}
Time taken: ${elapsedTime} nanoseconds / ${TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS)} milliseconds
Password (as UTF8 String): ${passwordInput.text.toString()}
Salt input (words): ${saltInput.text}
Salt (as chopped UTF8 HexString): ${sha256.toHexString()}
Seed (as UTF8 HexString): ${seed.toHexString()}
Hash length: ${hashLengthInput.text}
Hash cycles (opslimit): ${opsLimitInput.text}
Memory limit (kilobytes): ${memoryLimitInput.text}
Algorithm: $algoAsString
Hashed output: ${byteArrayHash.toHexString()}
"""

                argon2Output.text = output + argon2Output.getText()
                Log.wtf("KeyspaceDevOptions -> Argon2iTest", output)

            } catch (exception: Exception) {
                Toast.makeText(applicationContext, "Weird data entered! Check libsodium docs :(", Toast.LENGTH_SHORT).show()
            }


        }

        val bip39Text = findViewById<TextView>(R.id.bip39Text)
        val bip39PassphraseSeedInput = findViewById<EditText>(R.id.bip39PassphraseSeedInput)
        val bip39SeedText = findViewById<TextView>(R.id.bip39SeedText)
        val bip39Button = findViewById<MaterialButton>(R.id.bip39Button)

        bip39Button.setOnClickListener {
            val bip39: CryptoUtilities.Bip39 = crypto.bip39(bip39PassphraseSeedInput.text.toString().toCharArray())
            bip39Text.text = bip39.words!!.joinToString("")
            bip39SeedText.text = "Seed (as UTF8 HexString): " + bip39.seed?.toHexString()
        }

    }
}