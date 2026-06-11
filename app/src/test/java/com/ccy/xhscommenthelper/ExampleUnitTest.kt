package com.ccy.xhscommenthelper

import com.ccy.xhscommenthelper.accessibility.CommentTextFilter
import com.ccy.xhscommenthelper.accessibility.ProfileNodeText
import com.ccy.xhscommenthelper.accessibility.ProfileInfoTextExtractor
import com.ccy.xhscommenthelper.domain.MessageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun messageBuilderUsesDocumentedTemplate() {
        val message = MessageBuilder.build(
            comment = "这个多少钱呀",
            fixedText = "方便的话可以了解一下，我们这边可以给你发详细介绍～"
        )

        assertEquals(
            "刚刚看到你评论：“这个多少钱呀”\n方便的话可以了解一下，我们这边可以给你发详细介绍～",
            message
        )
    }

    @Test
    fun commentTextFilterRejectsNoiseAndShortText() {
        assertTrue(CommentTextFilter.isNoiseText("关注"))
        assertTrue(CommentTextFilter.isNoiseText("赞"))
        assertTrue(CommentTextFilter.isNoiseText("说点什么"))
        assertFalse(CommentTextFilter.isNoiseText("这个多少钱呀"))
    }

    @Test
    fun commentTextFilterPicksFirstReasonableCandidate() {
        val candidate = CommentTextFilter.pickBestCommentCandidate(
            listOf("关注", "这个多少钱呀", "回复")
        )

        assertEquals("这个多少钱呀", candidate)
    }

    @Test
    fun profileInfoExtractorReadsVisibleGenderAndIpOnly() {
        val info = ProfileInfoTextExtractor.extract(
            listOf("小红书号：123", "女，25岁", "IP属地：浙江")
        )

        assertEquals("女", info.visibleGender)
        assertEquals("浙江", info.ipLocation)
    }

    @Test
    fun profileInfoExtractorParsesIpColonFormats() {
        assertEquals("天津", ProfileInfoTextExtractor.extract(listOf("IP：天津")).ipLocation)
        assertEquals("天津", ProfileInfoTextExtractor.extract(listOf("IP: 天津")).ipLocation)
        assertEquals("浙江", ProfileInfoTextExtractor.extract(listOf("IP属地：浙江")).ipLocation)
        assertEquals("广东", ProfileInfoTextExtractor.extract(listOf("IP 属地 广东")).ipLocation)
    }

    @Test
    fun profileInfoExtractorParsesGenderFromContentDescription() {
        val maleText = ProfileInfoTextExtractor.extractFromNodes(
            listOf(ProfileNodeText(contentDescription = "男，28岁"))
        )

        assertEquals("男", maleText.visibleGender)
    }

    @Test
    fun profileInfoExtractorParsesGenderFromViewId() {
        val info = ProfileInfoTextExtractor.extractFromNodes(
            listOf(ProfileNodeText(viewIdResourceName = "com.xingin.xhs:id/gender_female"))
        )

        assertEquals("女", info.visibleGender)
    }

    @Test
    fun profileInfoExtractorKeepsUnknownValuesNull() {
        val info = ProfileInfoTextExtractor.extractFromNodes(
            listOf(ProfileNodeText(text = "小红书号：123", viewIdResourceName = "com.xingin.xhs:id/avatar"))
        )

        assertEquals(null, info.visibleGender)
        assertEquals(null, info.ipLocation)
    }
}
