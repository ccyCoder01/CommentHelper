package com.ccy.xhscommenthelper.accessibility

import com.ccy.xhscommenthelper.domain.ProfileInfo

object ProfileInfoTextExtractor {
    fun extract(texts: List<String>): ProfileInfo {
        val normalized = texts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val visibleGender = normalized.firstNotNullOfOrNull { text ->
            when {
                text == "男" || text.contains("性别：男") || text.contains("性别 男") -> "男"
                text == "女" || text.contains("性别：女") || text.contains("性别 女") -> "女"
                else -> null
            }
        }

        val ipLocation = normalized.firstOrNull { text ->
            text.contains("IP属地") || text.contains("IP 属地")
        }

        val summary = buildList {
            add("性别：${visibleGender ?: "未识别"}")
            add("IP：${ipLocation ?: "未识别"}")
        }.joinToString("\n")

        return ProfileInfo(
            visibleGender = visibleGender,
            ipLocation = ipLocation,
            summary = summary
        )
    }
}
