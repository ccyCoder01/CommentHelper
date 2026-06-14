package com.ccy.xhscommenthelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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

    fun performSwipeToNextArea(): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.76f
        val endY = displayMetrics.heightPixels * 0.40f
        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
