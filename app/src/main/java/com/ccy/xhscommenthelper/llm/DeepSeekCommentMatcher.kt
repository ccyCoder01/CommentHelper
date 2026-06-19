package com.ccy.xhscommenthelper.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekCommentMatcher {
    suspend fun match(requirement: String, comment: String): LlmMatchResult {
        val normalizedRequirement = requirement.trim()
        val normalizedComment = comment.trim()
        if (normalizedRequirement.isBlank() || normalizedComment.isBlank()) {
            return LlmMatchResult.NeedsConfirmation
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = REQUEST_TIMEOUT_MS
                    readTimeout = REQUEST_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $API_KEY")
                }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(buildRequestBody(normalizedRequirement, normalizedComment).toString())
                }

                val body = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }
                connection.disconnect()
                parseResponse(body)
            }.getOrElse {
                LlmMatchResult.NeedsConfirmation
            }
        }
    }

    private fun buildRequestBody(requirement: String, comment: String): JSONObject {
        return JSONObject()
            .put("model", "deepseek-v4-flash")
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", SYSTEM_PROMPT)
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                "【用户画像要求】\n$requirement\n\n【评论区评论】\n$comment"
                            )
                    )
            )
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("reasoning_effort", "high")
            .put("stream", false)
    }

    private fun parseResponse(body: String): LlmMatchResult {
        val content = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            ?: return LlmMatchResult.NeedsConfirmation

        val json = JSONObject(content)
        val decision = LlmDecision.from(json.optString("decision"))
            ?: return LlmMatchResult.NeedsConfirmation
        val gender = LlmGender.from(json.optString("gender"))
            ?: return LlmMatchResult.NeedsConfirmation
        val age = LlmAgeMatch.from(json.optString("age"))
            ?: return LlmMatchResult.NeedsConfirmation

        return LlmMatchResult.Result(
            decision = decision,
            gender = gender,
            age = age
        )
    }

    private companion object {
        const val API_URL = "https://api.deepseek.com/chat/completions"
        const val API_KEY = "sk-360016f0edc4435ca0c5c2e8dfd733a3"
        const val REQUEST_TIMEOUT_MS = 15_000

        const val SYSTEM_PROMPT =
            "你是一个严谨的用户画像匹配判断助手。你的任务是根据用户输入 content 中的【用户画像要求】和【评论区评论】，判断该评论作者是否明确符合用户画像要求中的性别和年龄条件。\n\n" +
                    "判断规则：\n" +
                    "1. 只提取并判断【用户画像要求】中的性别要求和年龄要求，不判断 IP。\n" +
                    "2. 只根据【评论区评论】中的明确信息或强相关表达进行判断，不得凭空猜测。\n" +
                    "3. 性别判断仅在评论中明确出现性别自述或强指向表达时成立，例如：我是女生、男生表示、宝妈、宝爸、老公、老婆等。不得仅凭语气、昵称、用词风格推断性别。\n" +
                    "4. 年龄判断仅在评论中明确出现年龄、年龄段、代际或强相关身份信息时成立，例如：25岁、30+、90后、00后、大学生、初中生、宝妈多年等。\n" +
                    "5. 如果评论能明确满足用户画像中的性别和年龄要求，decision 返回 match。\n" +
                    "6. 如果评论能明确与用户画像中的性别或年龄要求冲突，decision 返回 reject。\n" +
                    "7. 如果评论无法明确判断性别或年龄是否符合要求，decision 返回 unknown。\n" +
                    "8. gender 字段表示评论中明确识别出的性别，只能是 男、女、unknown。\n" +
                    "9. age 字段表示评论中年龄是否符合画像，只能是 match、reject、unknown。\n\n" +
                    "输出要求：\n" +
                    "只能返回一行 JSON，不要返回任何解释、代码块或其他内容。\n" +
                    "JSON 格式必须为：{\"decision\":\"match|reject|unknown\",\"gender\":\"男|女|unknown\",\"age\":\"match|reject|unknown\"}"
    }
}

sealed class LlmMatchResult {
    data class Result(
        val decision: LlmDecision,
        val gender: LlmGender,
        val age: LlmAgeMatch
    ) : LlmMatchResult()

    object NeedsConfirmation : LlmMatchResult()
}

enum class LlmDecision {
    Match,
    Reject,
    Unknown;

    companion object {
        fun from(value: String): LlmDecision? {
            return when (value.trim().lowercase()) {
                "match" -> Match
                "reject" -> Reject
                "unknown" -> Unknown
                else -> null
            }
        }
    }
}

enum class LlmGender {
    Male,
    Female,
    Unknown;

    companion object {
        fun from(value: String): LlmGender? {
            return when (value.trim()) {
                "男" -> Male
                "女" -> Female
                "unknown" -> Unknown
                else -> null
            }
        }
    }
}

enum class LlmAgeMatch {
    Match,
    Reject,
    Unknown;

    companion object {
        fun from(value: String): LlmAgeMatch? {
            return when (value.trim().lowercase()) {
                "match" -> Match
                "reject" -> Reject
                "unknown" -> Unknown
                else -> null
            }
        }
    }
}
