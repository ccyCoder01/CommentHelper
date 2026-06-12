package com.ccy.xhscommenthelper.domain

data class CommentCandidate(
    val text: String,
    val index: Int,
    val nickname: String? = null
)
