package com.ccy.xhscommenthelper.data

data class UserSettings(
    val fixedText: String = DEFAULT_FIXED_TEXT,
    val targetPackageName: String = DEFAULT_TARGET_PACKAGE_NAME,
    val targetGender: String = "",
    val targetIpLocation: String = "",
    val commentWhitelist: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_FIXED_TEXT = "方便的话可以了解一下，我们这边可以给你发详细介绍～"
        const val DEFAULT_TARGET_PACKAGE_NAME = "com.xingin.xhs"
    }
}
