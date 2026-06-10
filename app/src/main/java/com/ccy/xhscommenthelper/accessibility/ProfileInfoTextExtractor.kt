package com.ccy.xhscommenthelper.accessibility

import com.ccy.xhscommenthelper.domain.ProfileInfo

object ProfileInfoTextExtractor {
    fun extract(texts: List<String>): ProfileInfo {
        return extractFromNodes(texts.map { ProfileNodeText(text = it) })
    }

    fun extractFromNodes(nodes: List<ProfileNodeText>): ProfileInfo {
        val textValues = nodes.flatMap { node ->
            listOfNotNull(node.text, node.contentDescription)
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val idValues = nodes.mapNotNull { it.viewIdResourceName?.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val visibleGender = textValues.firstNotNullOfOrNull { parseGenderFromText(it) }
            ?: idValues.firstNotNullOfOrNull { parseGenderFromId(it) }

        val ipLocation = textValues.firstNotNullOfOrNull { parseIpLocation(it) }

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

    private fun parseGenderFromText(text: String): String? {
        val normalized = text.trim()
        return when {
            normalized.contains("男", ignoreCase = true) -> "男"

            normalized.contains("女", ignoreCase = true) -> "女"

            else -> null
        }
    }

    private fun parseGenderFromId(viewId: String): String? {
        val normalized = viewId.lowercase()
        val femaleTokens = listOf("gender_female", "female", "woman")
        val maleTokens = listOf("gender_male", "male", "man")
        return when {
            femaleTokens.any { normalized.contains(it) } -> "女"
            maleTokens.any { normalized.contains(it) } -> "男"
            else -> null
        }
    }

    private fun parseIpLocation(text: String): String? {
        val match = Regex("""IP\s*(?:属地)?\s*[:：]?\s*(.+)""", RegexOption.IGNORE_CASE)
            .find(text.trim())
            ?: return null
        return match.groupValues.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
