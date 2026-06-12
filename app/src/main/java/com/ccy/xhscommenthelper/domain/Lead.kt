package com.ccy.xhscommenthelper.domain

data class Lead(
    val nickname: String? = null,
    val comment: String? = null,
    val profileOpened: Boolean = false,
    val status: LeadStatus = LeadStatus.IDLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
