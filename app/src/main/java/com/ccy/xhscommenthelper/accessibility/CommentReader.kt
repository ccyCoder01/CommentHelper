package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.util.ClipboardHelper

class CommentReader(private val clipboardHelper: ClipboardHelper) {
    fun readCurrentComment(root: AccessibilityNodeInfo?): String? {
        val texts = NodeFinder.flatten(root)
            .mapNotNull { node -> node.text?.toString()?.trim() }
            .filter { text -> text.isNotBlank() }
            .filterNot { text -> CommentTextFilter.isNoiseText(text) }

        return CommentTextFilter.pickBestCommentCandidate(texts)
            ?: clipboardHelper.getText()?.trim()?.takeIf { it.isNotBlank() }
    }
}
