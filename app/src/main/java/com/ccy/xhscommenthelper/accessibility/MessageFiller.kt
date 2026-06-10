package com.ccy.xhscommenthelper.accessibility

import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.util.ClipboardHelper

class MessageFiller(private val clipboardHelper: ClipboardHelper) {
    fun fillMessage(root: AccessibilityNodeInfo?, message: String): Boolean {
        clipboardHelper.copyText(message)
        val inputNode = findInputNode(root) ?: return false

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
        }
        if (inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return true
        }

        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun findInputNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return NodeFinder.flatten(root).firstOrNull { node ->
            val className = node.className?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString().orEmpty()
            } else {
                ""
            }

            className.contains("EditText", ignoreCase = true) ||
                text.contains("发消息") ||
                text.contains("发送消息") ||
                text.contains("说点什么") ||
                text.contains("请输入") ||
                hint.contains("发消息") ||
                hint.contains("发送消息") ||
                hint.contains("说点什么") ||
                hint.contains("请输入")
        }
    }
}
