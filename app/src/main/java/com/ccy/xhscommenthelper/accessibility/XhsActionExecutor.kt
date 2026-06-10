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

    private fun isSystemText(text: String): Boolean {
        val systemWords = listOf("关注", "回复", "点赞", "分享", "收藏")
        return systemWords.any { word -> text.contains(word) }
    }
}
