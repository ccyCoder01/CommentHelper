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

    fun openProfileForComment(
        root: AccessibilityNodeInfo?,
        comment: String,
        nickname: String? = null
    ): Boolean {
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

    fun openClick(root: AccessibilityNodeInfo?, targetText: String = "发送"): Boolean {
        val nodes = NodeFinder.flatten(root)

        val inputNode = nodes.firstOrNull { node ->
            node.className?.toString() == "android.widget.EditText"
        }

        val inputBounds = Rect()
        inputNode?.getBoundsInScreen(inputBounds)

        nodes.forEachIndexed { index, node ->
            val text = node.text?.toString()?.trim()
            if (text != targetText) return@forEachIndexed
            if (node.className?.toString() != "android.widget.TextView") return@forEachIndexed
            if (!node.isClickable || !node.isEnabled || !node.isVisibleToUser) return@forEachIndexed

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val position = if (inputNode == null) {
                "未找到输入框"
            } else {
                when {
                    bounds.left >= inputBounds.right &&
                            bounds.top < inputBounds.bottom &&
                            bounds.bottom > inputBounds.top -> "输入框右侧"

                    bounds.bottom <= inputBounds.top -> "输入框上方"

                    bounds.top >= inputBounds.bottom -> "输入框下方"

                    else -> "输入框附近"
                }
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return false
            }
        }
        return true;
    }

    private fun isSystemText(text: String): Boolean {
        val systemWords = listOf("关注", "回复", "点赞", "分享", "收藏")
        return systemWords.any { word -> text.contains(word) }
    }
}
