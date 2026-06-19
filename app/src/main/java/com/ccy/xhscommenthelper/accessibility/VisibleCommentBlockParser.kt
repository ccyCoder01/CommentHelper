package com.ccy.xhscommenthelper.accessibility

data class VisibleTextNode(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
}

data class VisibleCommentBlock(
    val nickname: String,
    val commentText: String,
    val metaText: String,
    val ipLocation: String?
)

object VisibleCommentBlockParser {
    private const val MAX_LEFT_DELTA = 24
    private const val MAX_NICKNAME_TO_COMMENT_GAP = 48
    private const val MAX_COMMENT_TO_META_GAP = 640

    fun parse(nodes: List<VisibleTextNode>): List<VisibleCommentBlock> {
        val ordered = nodes
            .filter { node -> node.text.isNotBlank() }
            .sortedWith(compareBy<VisibleTextNode> { it.top }.thenBy { it.left })

        return buildList {
            var index = 0
            while (index <= ordered.lastIndex - 2) {
                val nickname = ordered[index]
                val comment = ordered[index + 1]
                val metaIndex = findMetaIndexAfterComment(ordered, comment, index + 2)
                val meta = metaIndex?.let { ordered[it] }

                if (meta != null && isCommentBlock(nickname, comment, meta)) {
                    add(
                        VisibleCommentBlock(
                            nickname = nickname.text.trim(),
                            commentText = comment.text.trim(),
                            metaText = meta.text.trim(),
                            ipLocation = parseIpLocation(meta.text)
                        )
                    )
                    index = metaIndex + 1
                } else {
                    index += 1
                }
            }
        }
    }

    private fun isCommentBlock(
        nickname: VisibleTextNode,
        comment: VisibleTextNode,
        meta: VisibleTextNode
    ): Boolean {
        return sameColumn(nickname, comment) &&
            sameColumn(comment, meta) &&
            isStacked(nickname, comment, MAX_NICKNAME_TO_COMMENT_GAP) &&
            isStacked(comment, meta, MAX_COMMENT_TO_META_GAP) &&
            looksLikeNickname(nickname.text) &&
            looksLikeComment(comment.text) &&
            looksLikeMeta(meta.text) &&
            comment.width >= nickname.width
    }

    private fun sameColumn(first: VisibleTextNode, second: VisibleTextNode): Boolean {
        return kotlin.math.abs(first.left - second.left) <= MAX_LEFT_DELTA
    }

    private fun findMetaIndexAfterComment(
        nodes: List<VisibleTextNode>,
        comment: VisibleTextNode,
        startIndex: Int
    ): Int? {
        var index = startIndex
        while (index <= nodes.lastIndex) {
            val node = nodes[index]
            if (node.top - comment.bottom > MAX_COMMENT_TO_META_GAP) return null
            if (sameColumn(comment, node) && looksLikeMeta(node.text)) return index
            index += 1
        }
        return null
    }

    private fun isStacked(upper: VisibleTextNode, lower: VisibleTextNode, maxGap: Int): Boolean {
        val gap = lower.top - upper.bottom
        return gap in 0..maxGap || upper.bottom == lower.top
    }

    private fun looksLikeNickname(text: String): Boolean {
        val value = text.trim()
        return value.length in 1..24 &&
            !CommentTextFilter.isNoiseText(value) &&
            !looksLikeMeta(value)
    }

    private fun looksLikeComment(text: String): Boolean {
        val value = text.trim()
        return value.length in 1..120 &&
            !CommentTextFilter.isNoiseText(value) &&
            !looksLikeMeta(value)
    }

    private fun looksLikeMeta(text: String): Boolean {
        val value = text.trim()
        return value.contains("回复") ||
            value.contains("刚刚") ||
            value.contains("分钟前") ||
            value.contains("小时前") ||
            value.contains("昨天") ||
            value.contains("前天") ||
            Regex("""\d{1,2}-\d{1,2}""").containsMatchIn(value)
    }

    private fun parseIpLocation(text: String): String? {
        val value = text.trim()
        return IP_LOCATION_OPTIONS.firstOrNull { location ->
            Regex("""(^|\s)$location(\s|$)""").containsMatchIn(value)
        }
    }

    private val IP_LOCATION_OPTIONS = listOf(
        "北京",
        "天津",
        "河北",
        "山西",
        "内蒙古",
        "辽宁",
        "吉林",
        "黑龙江",
        "上海",
        "江苏",
        "浙江",
        "安徽",
        "福建",
        "江西",
        "山东",
        "河南",
        "湖北",
        "湖南",
        "广东",
        "广西",
        "海南",
        "重庆",
        "四川",
        "贵州",
        "云南",
        "西藏",
        "陕西",
        "甘肃",
        "青海",
        "宁夏",
        "新疆",
        "香港",
        "澳门",
        "台湾"
    )
}
