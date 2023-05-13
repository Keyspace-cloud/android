package cloud.keyspace.android

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ImportAccounts : AppCompatActivity() {

    lateinit var crypto: CryptoUtilities
    lateinit var keyring: CryptoUtilities.Keyring

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.import_accounts)

        crypto = CryptoUtilities(applicationContext, this)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_import_accounts),
            intent = intent
        )
        keyring = intentData.first

        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        val aegisButton: LinearLayout = findViewById(R.id.aegisButton)
        aegisButton.setOnClickListener {
            crypto.secureStartActivity (
                nextActivity = ImportAccountsAegis(),
                nextActivityClassNameAsString = getString(R.string.title_activity_import_aegis),
                keyring = keyring,
                itemId = null
            )
        }

    }
}

