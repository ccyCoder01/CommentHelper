package com.ccy.xhscommenthelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class XhsAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AccessibilityBridge.service = this
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (AccessibilityBridge.service === this) {
            AccessibilityBridge.service = null
        }
        super.onDestroy()
    }

    fun getRoot(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
