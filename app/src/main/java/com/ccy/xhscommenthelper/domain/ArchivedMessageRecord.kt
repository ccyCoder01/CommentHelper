package com.ccy.xhscommenthelper.domain

data class ArchivedMessageRecord(
    val xhsId: String,
    val nickname: String = "",
    val gender: String = "",
    val ipLocation: String = "",
    val comment: String = "",
    val labelStatus: String = ArchiveLabelStatus.Unlabeled.storageValue,
    val labelReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
