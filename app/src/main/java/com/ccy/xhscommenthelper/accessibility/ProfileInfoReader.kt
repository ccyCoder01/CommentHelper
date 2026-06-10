package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.domain.ProfileInfo

class ProfileInfoReader {
    fun read(root: AccessibilityNodeInfo?): ProfileInfo {
        val nodeTexts = NodeFinder.flatten(root).map { node ->
            ProfileNodeText(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewIdResourceName = node.viewIdResourceName,
                className = node.className?.toString()
            )
        }

        return ProfileInfoTextExtractor.extractFromNodes(nodeTexts)
    }
}
