package cloud.keyspace.android

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.UUID

class AutofillAccessibilityService : AccessibilityService() {

    val TYPE_LOGIN = "login"
    val TYPE_CARD = "card"

    var loginData: IOUtilities.Login? = null

    init {
        loginData = IOUtilities.Login (
            id = UUID.randomUUID().toString(),
            organizationId = null,
            type = TYPE_LOGIN,
            name = "FirstName LastName",
            notes = "notes",
            favorite = true,
            tagId = null,
            loginData = IOUtilities.LoginData (
                username = "u53rn4m3",
                password = "password123",
                passwordHistory = null,
                email = "em@il.il",
                totp = IOUtilities.Totp (
                    secret = "ASDFGHJKLASDFGHJKL",
                    backupCodes = mutableListOf("123456", "654321"),
                ),
                siteUrls = mutableListOf("https://google.com", "accounts.google.com")
            ),
            dateCreated = 1673464321,
            dateModified = 1673464321,
            frequencyAccessed = 0L,
            iconFile = "keyspace",
            customFields = null
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        if (loginData != null) fillLoginData(rootNode)
    }

    /*
    * The trick to solving the infinite autofill issue is to:
    * a. Check for autofillable fields
    * b. Use the Keyspace nub and pick an item
    * c. Grab tapped item fields and add it to map
    * d. Since the AccessibilityService runs indefinitely, make it trigger the filler function only if the data map is not empty
    * e. Clear the data map after the field has been autofilled. Since the data map is now empty, the filler function is not triggered at all     <- INFINITE AUTOFILL PREVENTED!
    * f. To autofill, use the nub again
    * */

    private fun fillLoginData(rootNode: AccessibilityNodeInfo) {
        val editText = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, loginData!!.loginData!!.password)
        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        loginData = null
    }

    override fun onInterrupt() { }

}