package com.ccy.xhscommenthelper.accessibility

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object NodeDebugDumper {
    private const val TAG = "XhsNodeDump"

    fun dump(root: AccessibilityNodeInfo?, scene: String) {
        val nodes = NodeFinder.flatten(root)
        Log.d(TAG, "===== $scene node dump start, count=${nodes.size} =====")
        nodes.forEachIndexed { index, node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.d(
                TAG,
                "#$index text=${node.text.safeValue()} " +
                    "viewId=${node.viewIdResourceName.safeValue()} " +
                    "class=${node.className.safeValue()} " +
                    "bounds=${bounds.toShortString()}"
            )
        }
        Log.d(TAG, "===== $scene node dump end =====")
    }

    private fun CharSequence?.safeValue(): String {
        return this?.toString()
            ?.replace("\n", "\\n")
            ?.takeIf { it.isNotBlank() }
            ?: "<empty>"
    }

    private fun String?.safeValue(): String {
        return this
            ?.replace("\n", "\\n")
            ?.takeIf { it.isNotBlank() }
            ?: "<empty>"
    }
}
