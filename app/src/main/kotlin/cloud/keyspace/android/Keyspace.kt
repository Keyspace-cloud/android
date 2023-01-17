package cloud.keyspace.android

import android.app.Application
import android.content.Context
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import java.sql.Time
import java.time.Instant
import java.time.format.DateTimeFormatter

class Keyspace : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.KEY_VALUE_LIST

            dialog {
                text = getString(R.string.crash_send_logs_description)
                title = " " + getString(R.string.app_name)
                positiveButtonText = getString(R.string.crash_send_positive_button_text)
                negativeButtonText = getString(R.string.exit)
                resIcon = R.drawable.keyspace
                resTheme = android.R.style.Theme_Material_Dialog
            }

            mailSender {
                mailTo = getString(R.string.support_email)
                reportAsFile = true
                reportFileName = "bug_report_${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}.txt"
                subject = getString(R.string.crash_send_logs_email_subject)
                body = getString(R.string.crash_send_logs_email_body)
            }

        }

    }
}