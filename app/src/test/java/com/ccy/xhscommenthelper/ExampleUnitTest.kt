package com.ccy.xhscommenthelper

import com.ccy.xhscommenthelper.accessibility.CommentTextFilter
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
}
