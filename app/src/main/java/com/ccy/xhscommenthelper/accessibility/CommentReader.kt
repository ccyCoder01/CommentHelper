package com.ccy.xhscommenthelper.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.domain.CommentCandidate
import com.ccy.xhscommenthelper.util.ClipboardHelper

class CommentReader(private val clipboardHelper: ClipboardHelper) {
    private companion object {
        const val COMMENT_TEXT_VIEW_ID = "com.xingin.xhs:id/0_resource_name_obfuscated"
        const val COMMENT_TEXT_CLASS_NAME = "android.widget.TextView"
    }

    fun readCurrentComment(root: AccessibilityNodeInfo?): String? {
        val texts = NodeFinder.flatten(root)
            .mapNotNull { node -> node.text?.toString()?.trim() }
            .filter { text -> text.isNotBlank() }
            .filterNot { text -> CommentTextFilter.isNoiseText(text) }

        return CommentTextFilter.pickBestCommentCandidate(texts)
            ?: clipboardHelper.getText()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun readVisibleComments(root: AccessibilityNodeInfo?): List<CommentCandidate> {
        return NodeFinder.flatten(root)
            .filter { node -> isCommentOrReplyTextNode(node) }
            .mapNotNull { node -> node.text?.toString()?.trim() }
            .filter { text -> text.isNotBlank() }
            .filterNot { text -> CommentTextFilter.isNoiseText(text) }
            .filter { text -> text.length in 2..120 }
            .distinct()
            .mapIndexed { index, text -> CommentCandidate(text = text, index = index) }
    }

    private fun isCommentOrReplyTextNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return node.viewIdResourceName == COMMENT_TEXT_VIEW_ID &&
            node.className?.toString() == COMMENT_TEXT_CLASS_NAME &&
            !bounds.isEmpty
    }
}
