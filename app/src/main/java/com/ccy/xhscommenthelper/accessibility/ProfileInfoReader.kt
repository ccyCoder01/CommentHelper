package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.domain.ProfileInfo

class ProfileInfoReader {
    fun read(root: AccessibilityNodeInfo?): ProfileInfo {
        val texts = NodeFinder.flatten(root)
            .mapNotNull { node -> node.text?.toString()?.trim() }
            .filter { text -> text.isNotBlank() }

        return ProfileInfoTextExtractor.extract(texts)
    }
}
