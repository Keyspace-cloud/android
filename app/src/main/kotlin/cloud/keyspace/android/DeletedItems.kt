package cloud.keyspace.android

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.yahiaangelo.markdownedittext.MarkdownEditText
import java.util.*


class DeletedItems : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.deleted_items)

        val configData = getSharedPreferences (applicationContext.packageName + "_configuration_data", MODE_PRIVATE)



    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, StartHere::class.java)
        this.startActivity(intent)
        finishAffinity()
    }
}