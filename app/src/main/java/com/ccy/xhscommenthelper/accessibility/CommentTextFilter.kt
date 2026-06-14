package com.ccy.xhscommenthelper.accessibility

object CommentTextFilter {
    private val exactNoiseTexts = setOf(
        "关注",
        "点赞",
        "回复",
        "分享",
        "收藏",
        "赞",
        "说点什么",
        "搜索",
        "首页",
        "购物",
        "消息",
        "我"
    )

    fun isNoiseText(text: String): Boolean {
        val normalized = text.trim()
        return normalized in exactNoiseTexts
    }

    fun pickBestCommentCandidate(texts: List<String>): String? {
        return texts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isNoiseText(it) }
            .firstOrNull { it.length in 1..120 }
    }
}
