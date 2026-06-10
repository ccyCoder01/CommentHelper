package com.ccy.xhscommenthelper.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object PermissionHelper {
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/com.ccy.xhscommenthelper.accessibility.XhsAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
