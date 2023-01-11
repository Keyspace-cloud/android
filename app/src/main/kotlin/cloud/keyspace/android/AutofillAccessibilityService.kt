package cloud.keyspace.android

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutofillAccessibilityService : AccessibilityService() {

    val fillableTextFields = mutableMapOf<String, String>()

    init {
        fillableTextFields["password-field-label"] = "pass123"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        if (fillableTextFields.isNotEmpty()) {
            for (field in fillableTextFields) {
                fillData(rootNode, field)
            }
        }
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

    private fun fillData(rootNode: AccessibilityNodeInfo, data: MutableMap.MutableEntry<String, String>) {
        val editText = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data.value)
        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        fillableTextFields.remove(data.key)
    }

    override fun onInterrupt() { }
}