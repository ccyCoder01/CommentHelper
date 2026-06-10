package com.ccy.xhscommenthelper.accessibility

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

    fun openProfileForComment(root: AccessibilityNodeInfo?, comment: String): Boolean {
        val commentNode = NodeFinder.flatten(root).firstOrNull { node ->
            node.text?.toString()?.trim() == comment
        }
        val clickable = NodeFinder.findClickableParent(commentNode)
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            return true
        }
        return openProfile(root)
    }

    fun openMessageEntry(root: AccessibilityNodeInfo?): Boolean {
        val entryNode = NodeFinder.flatten(root).firstOrNull { node ->
            val text = node.text?.toString().orEmpty().trim()
            text == "发消息" ||
                text == "私信" ||
                text.contains("发消息") ||
                text.contains("私信")
        }
        val clickable = NodeFinder.findClickableParent(entryNode)
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun isSystemText(text: String): Boolean {
        val systemWords = listOf("关注", "回复", "点赞", "分享", "收藏")
        return systemWords.any { word -> text.contains(word) }
    }
}
