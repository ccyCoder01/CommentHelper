package com.ccy.xhscommenthelper.domain

object MessageBuilder {
    fun build(comment: String, fixedText: String): String {
        return "刚刚看到你评论：“$comment”\n$fixedText"
    }
}
