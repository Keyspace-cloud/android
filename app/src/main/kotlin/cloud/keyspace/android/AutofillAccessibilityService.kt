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
                fillPassword(rootNode, field)
            }
        }
    }

    private fun fillPassword(rootNode: AccessibilityNodeInfo, password: MutableMap.MutableEntry<String, String>) {
        val editText = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            password.value
        )
        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        fillableTextFields.remove(password.key)
    }

    override fun onInterrupt() { }
}