package cloud.keyspace.android

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.graphics.BitmapFactory
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi


class PasswordAutofill: AutofillService() {

    val usernameFields = listOf<String>(
        "email",
        "user",
        "input",
        "id",
        "identity",
        "identifier",
        "uid",
        "candidate",
        "udid",
        "name"
    )

    val passwordFields = listOf<String>(
        "pass",
        "phrase",
        "key",
        "crypt",
        "address",
        "pin",
        "cvv",
        "cvc"
    )

    @RequiresApi(Build.VERSION_CODES.R)
    @Nullable

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val context: List<FillContext> = request.fillContexts
        val assistStructure: AssistStructure = context[context.size - 1].structure
        val autofillFields: MutableList<ViewNode?> = ArrayList()

        //getUsernameFields(assistStructure.getWindowNodeAt(0)?.rootViewNode, autofillFields)
        if (autofillFields.size == 0) return

        val autofillMenu = RemoteViews (packageName, R.layout.autofill_menu)
        val account = "key@space.com"
        autofillMenu.setTextViewText (R.id.email, account)
        autofillMenu.setImageViewBitmap(
            R.id.siteLogo, BitmapFactory.decodeResource(resources, R.drawable.ic_baseline_website_24)
        )

        val field: ViewNode? = autofillFields[0]

        val accountDataset = Dataset.Builder(autofillMenu)
            .setValue(
                field?.autofillId!!,
                AutofillValue.forText(account)
            ).build()

        val response = FillResponse.Builder()
            .addDataset(accountDataset)
            .build()

        callback.onSuccess(response)

    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        TODO("Not yet implemented")
    }

    private fun getUsernameFields(node: ViewNode?, autofillFields: MutableList<ViewNode?>): String? {
        var type: String? = null
        if (node!!.className!!.contains("EditText")) {
            val viewId = node.idEntry
            Log.d("VIEWNODE", viewId.toString())

            for (field in usernameFields) {
                if (viewId != null && viewId.contains(field, ignoreCase = true)) {
                    autofillFields.add(node)
                    type = "username"
                }
            }

            for (field in passwordFields) {
                if (viewId != null && viewId.contains(field, ignoreCase = true)) {
                    autofillFields.add(node)
                    type = "password"
                }
            }

        }
        for (i in 0 until node.childCount) {
            getUsernameFields(node.getChildAt(i), autofillFields)
        }
        return type
    }

}