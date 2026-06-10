package com.ccy.xhscommenthelper.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardHelper(private val context: Context) {
    fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xhs_message", text))
    }

    fun getText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}
