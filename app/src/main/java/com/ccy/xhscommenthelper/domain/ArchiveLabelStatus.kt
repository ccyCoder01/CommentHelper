package com.ccy.xhscommenthelper.domain

enum class ArchiveLabelStatus(
    val storageValue: String,
    val displayName: String
) {
    Unlabeled("unlabeled", "未标记"),
    Success("success", "成功"),
    Failure("failure", "失败");

    companion object {
        fun fromStorageValue(value: String): ArchiveLabelStatus {
            return entries.firstOrNull { status -> status.storageValue == value } ?: Unlabeled
        }

        fun fromDisplayName(value: String): ArchiveLabelStatus {
            return entries.firstOrNull { status -> status.displayName == value } ?: Unlabeled
        }
    }
}
