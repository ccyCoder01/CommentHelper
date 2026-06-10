package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo

object NodeFinder {
    fun flatten(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result.add(node)
            for (index in 0 until node.childCount) {
                dfs(node.getChild(index))
            }
        }

        dfs(root)
        return result
    }

    fun findByText(root: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        return flatten(root).firstOrNull { node ->
            val text = node.text?.toString().orEmpty()
            keywords.any { keyword -> text.contains(keyword) }
        }
    }

    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }
}
