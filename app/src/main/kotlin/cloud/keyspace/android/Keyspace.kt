package cloud.keyspace.android

import android.app.Application
import android.content.Context
import android.widget.Toast
import org.acra.config.mailSenderConfiguration
import org.acra.config.toastConfiguration
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class Keyspace : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            toastConfiguration { // Todo replace with dialog or notification
                //required
                text = getString(R.string.crash_send_logs_description)
                //defaults to Toast.LENGTH_LONG
                length = Toast.LENGTH_LONG
                enabled = true
            }

            mailSenderConfiguration {
                //required
                mailTo = "acra@yourserver.com"
                //defaults to true
                reportAsFile = true
                //defaults to ACRA-report.stacktrace
                reportFileName = "Crash.txt"
                //defaults to "<applicationId> Crash Report"
                subject = getString(R.string.crash_send_logs_description)
                //defaults to empty
                body = getString(R.string.crash_send_logs_description)
                enabled = true
            }

        }

    }
}