package com.ccy.xhscommenthelper.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class XhsActionExecutor {
    fun openProfile(root: AccessibilityNodeInfo?): Boolean {
        val possibleNicknameNode = NodeFinder.flatten(root).firstOrNull { node ->
            val text = node.text?.toString().orEmpty().trim()
            text.isNotBlank() && text.length in 2..20 && !isSystemText(text)
        }

        val clickable = NodeFinder.findClickableParent(possibleNicknameNode)
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    fun openProfileForComment(root: AccessibilityNodeInfo?, comment: String, nickname: String? = null): Boolean {
        val targetText = nickname?.takeIf { it.isNotBlank() } ?: comment
        val targetNode = NodeFinder.flatten(root).firstOrNull { node ->
            node.text?.toString()?.trim() == targetText
        }
        val clickable = NodeFinder.findClickableParent(targetNode)
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            return true
        }
        return openProfile(root)
    }

    fun openMessageEntry(root: AccessibilityNodeInfo?): Boolean {
        val entryNode = NodeFinder.flatten(root).firstOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            MessageEntryMatcher.isProfileMessageEntry(
                text = node.text?.toString(),
                className = node.className?.toString(),
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
        }
        val clickable = NodeFinder.findClickableParent(entryNode)
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            return true
        }
        return entryNode?.takeIf { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun isSystemText(text: String): Boolean {
        val systemWords = listOf("关注", "回复", "点赞", "分享", "收藏")
        return systemWords.any { word -> text.contains(word) }
    }
}
