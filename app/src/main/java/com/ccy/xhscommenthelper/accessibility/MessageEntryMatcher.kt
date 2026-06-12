package com.ccy.xhscommenthelper.accessibility

object MessageEntryMatcher {
    private const val MESSAGE_ENTRY_TEXT = "发私信"
    private const val TEXT_VIEW_CLASS_NAME = "android.widget.TextView"
    private const val MIN_BUTTON_WIDTH = 160
    private const val MIN_BUTTON_HEIGHT = 40
    private const val MAX_BUTTON_HEIGHT = 180

    fun isProfileMessageEntry(
        text: String?,
        className: String?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean {
        val width = right - left
        val height = bottom - top
        return text?.trim() == MESSAGE_ENTRY_TEXT &&
            className == TEXT_VIEW_CLASS_NAME &&
            width >= MIN_BUTTON_WIDTH &&
            height in MIN_BUTTON_HEIGHT..MAX_BUTTON_HEIGHT
    }
}
