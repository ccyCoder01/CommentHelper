package com.ccy.xhscommenthelper

import com.ccy.xhscommenthelper.accessibility.CommentTextFilter
import com.ccy.xhscommenthelper.accessibility.VisibleTextNode
import com.ccy.xhscommenthelper.accessibility.VisibleCommentBlockParser
import com.ccy.xhscommenthelper.accessibility.MessageEntryMatcher
import com.ccy.xhscommenthelper.accessibility.ProfileNodeText
import com.ccy.xhscommenthelper.accessibility.ProfileInfoTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
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
    fun visibleCommentBlockParserKeepsOnlyProfileNicknames() {
        val blocks = VisibleCommentBlockParser.parse(
            listOf(
                VisibleTextNode("蛤蛤大孝", 183, 360, 351, 421),
                VisibleTextNode("变 傅首尔  了", 183, 421, 1035, 494),
                VisibleTextNode("1小时前 广东 回复", 183, 507, 783, 567),
                VisibleTextNode("Kircby", 183, 652, 295, 713),
                VisibleTextNode("底子还是好啊 换底子不行的胖三十斤脸已经要肿成猪头肉了[捂脸R]", 183, 713, 1035, 852),
                VisibleTextNode("4小时前 上海 回复", 183, 865, 783, 925),
                VisibleTextNode("泷凌", 183, 1010, 267, 1071),
                VisibleTextNode("潜力股啊[赞R]", 183, 1071, 1035, 1144),
                VisibleTextNode("8小时前 广东 回复", 183, 1157, 783, 1217)
            )
        )

        assertEquals(listOf("蛤蛤大孝", "Kircby", "泷凌"), blocks.map { it.nickname })
        assertEquals(listOf("变 傅首尔  了", "底子还是好啊 换底子不行的胖三十斤脸已经要肿成猪头肉了[捂脸R]", "潜力股啊[赞R]"), blocks.map { it.commentText })
    }

    @Test
    fun visibleCommentBlockParserAllowsDistantMetaRow() {
        val blocks = VisibleCommentBlockParser.parse(
            listOf(
                VisibleTextNode("8岁魔法少女", 183, 1252, 415, 1313),
                VisibleTextNode("大美女...你想干嘛就干嘛吧[微笑R]", 183, 1313, 1035, 1386),
                VisibleTextNode("昨天 11:41 北京 回复", 183, 1783, 783, 1843)
            )
        )

        assertEquals(listOf("8岁魔法少女"), blocks.map { it.nickname })
    }

    @Test
    fun visibleCommentBlockParserSkipsMediaMarkerBeforeMetaRow() {
        val blocks = VisibleCommentBlockParser.parse(
            listOf(
                VisibleTextNode("我是马栏山的摄影师CC", 183, 327, 611, 388),
                VisibleTextNode("这个算啥", 183, 388, 1035, 461),
                VisibleTextNode("共4张", 716, 618, 816, 666),
                VisibleTextNode("2天前 湖南 回复", 183, 703, 783, 763)
            )
        )

        assertEquals(listOf("我是马栏山的摄影师CC"), blocks.map { it.nickname })
        assertEquals(listOf("这个算啥"), blocks.map { it.commentText })
    }

    @Test
    fun messageEntryMatcherOnlyAcceptsProfileSendPrivateMessageButton() {
        assertTrue(
            MessageEntryMatcher.isProfileMessageEntry(
                text = "发私信",
                className = "android.widget.TextView",
                left = 492,
                top = 924,
                right = 924,
                bottom = 1020
            )
        )
        assertFalse(
            MessageEntryMatcher.isProfileMessageEntry(
                text = "私信",
                className = "android.widget.TextView",
                left = 492,
                top = 924,
                right = 924,
                bottom = 1020
            )
        )
        assertFalse(
            MessageEntryMatcher.isProfileMessageEntry(
                text = "发消息",
                className = "android.widget.TextView",
                left = 492,
                top = 924,
                right = 924,
                bottom = 1020
            )
        )
        assertFalse(
            MessageEntryMatcher.isProfileMessageEntry(
                text = "发私信",
                className = "android.view.View",
                left = 492,
                top = 924,
                right = 924,
                bottom = 1020
            )
        )
        assertFalse(
            MessageEntryMatcher.isProfileMessageEntry(
                text = "发私信",
                className = "android.widget.TextView",
                left = 492,
                top = 924,
                right = 520,
                bottom = 940
            )
        )
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
