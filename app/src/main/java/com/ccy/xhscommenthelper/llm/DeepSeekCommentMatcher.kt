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
            return LlmMatchResult.Match
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
            ?.lowercase()
            ?: return LlmMatchResult.NeedsConfirmation

        return when (content) {
            "true" -> LlmMatchResult.Match
            "false" -> LlmMatchResult.Reject
            else -> LlmMatchResult.NeedsConfirmation
        }
    }

    private companion object {
        const val API_URL = "https://api.deepseek.com/chat/completions"
        const val API_KEY = "sk-360016f0edc4435ca0c5c2e8dfd733a3"
        const val REQUEST_TIMEOUT_MS = 15_000
        const val SYSTEM_PROMPT =
            "你是一个严谨的用户画像匹配判断助手。你的任务是根据用户输入 content 中的【用户画像要求】和【评论区评论】，只判断该评论作者是否符合用户画像要求中的性别和年龄条件。\n\n" +
                    "判断规则：\n" +
                    "1. 只提取并判断【用户画像要求】中的性别要求和年龄要求。\n" +
                    "2. 只根据【评论区评论】中的明确信息或强相关表达进行判断，不得凭空猜测。\n" +
                    "3. 性别判断仅在评论中明确出现性别自述或强指向表达时成立，例如：我是女生、男生表示、宝妈、宝爸、老公、老婆等。不得仅凭语气、昵称、用词风格推断性别。\n" +
                    "4. 年龄判断仅在评论中明确出现年龄、年龄段、代际或强相关身份信息时成立，例如：25岁、30+、90后、00后、大学生、初中生、宝妈多年等。若无法确认是否落入画像年龄范围，则视为不匹配。\n" +
                    "5. 如果用户画像要求同时包含性别和年龄，则评论必须同时满足两项才返回 true。\n" +
                    "6. 如果用户画像只包含性别或只包含年龄，则只判断已给出的条件。\n" +
                    "7. 如果评论信息不足、无法判断、只有弱相关暗示，返回 false。\n" +
                    "8. 如果评论内容与性别或年龄要求存在明显冲突，返回 false。\n\n" +
                    "输出要求：\n" +
                    "只能返回 true 或 false，不要返回任何解释、JSON、标点、代码块或其他内容。"
    }
}

enum class LlmMatchResult {
    Match,
    Reject,
    NeedsConfirmation
}
