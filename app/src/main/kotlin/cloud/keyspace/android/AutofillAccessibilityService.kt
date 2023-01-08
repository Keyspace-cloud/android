import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutofillAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        Log.d("KS-ACC-TEST", event.source.toString())

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return
        }

        val nodeInfo = event.source ?: return

        // Iterate through all the form fields and autofill them
        val children = nodeInfo.findAccessibilityNodeInfosByViewId("username")
        val bundle = Bundle()
        bundle.putString (AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "autofill_value")
        for (child in children) {
            child.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }
        bundle.clear()
    }

    override fun onInterrupt() {

    }
}