package cloud.keyspace.android

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ImportAccounts : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.import_accounts)

        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { onBackPressed() }

        val aegisButton: LinearLayout = findViewById(R.id.aegisButton)
        aegisButton.setOnClickListener {
            setContentView(R.layout.aegis_import)

        }

    }
}

