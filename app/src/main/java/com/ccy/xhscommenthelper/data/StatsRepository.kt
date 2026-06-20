package com.ccy.xhscommenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ccy.xhscommenthelper.domain.ArchiveLabelStatus
import com.ccy.xhscommenthelper.domain.ArchivedMessageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.statsDataStore by preferencesDataStore(name = "stats")

class StatsRepository(private val context: Context) {
    private val recordsKey = stringPreferencesKey("archived_message_records")

    val recordsFlow: Flow<List<ArchivedMessageRecord>> = context.statsDataStore.data.map { prefs ->
        decodeRecords(prefs[recordsKey].orEmpty())
    }

    suspend fun getAll(): List<ArchivedMessageRecord> {
        return recordsFlow.first()
    }

    suspend fun findByXhsId(xhsId: String): ArchivedMessageRecord? {
        val normalizedId = xhsId.trim()
        if (normalizedId.isBlank()) return null
        return getAll().firstOrNull { record -> record.xhsId == normalizedId }
    }

    suspend fun exists(xhsId: String): Boolean {
        return findByXhsId(xhsId) != null
    }

    suspend fun save(record: ArchivedMessageRecord) {
        val normalizedId = record.xhsId.trim()
        if (normalizedId.isBlank()) return
        context.statsDataStore.edit { prefs ->
            val records = decodeRecords(prefs[recordsKey].orEmpty()).toMutableList()
            val existingIndex = records.indexOfFirst { it.xhsId == normalizedId }
            val now = System.currentTimeMillis()
            val normalizedRecord = record.copy(
                xhsId = normalizedId,
                createdAt = records.getOrNull(existingIndex)?.createdAt ?: now,
                updatedAt = now
            )
            if (existingIndex >= 0) {
                records[existingIndex] = normalizedRecord
            } else {
                records.add(normalizedRecord)
            }
            prefs[recordsKey] = encodeRecords(records)
        }
    }

    suspend fun delete(xhsId: String) {
        val normalizedId = xhsId.trim()
        if (normalizedId.isBlank()) return
        context.statsDataStore.edit { prefs ->
            val records = decodeRecords(prefs[recordsKey].orEmpty())
                .filterNot { record -> record.xhsId == normalizedId }
            prefs[recordsKey] = encodeRecords(records)
        }
    }

    private fun encodeRecords(records: List<ArchivedMessageRecord>): String {
        return JSONArray().apply {
            records.forEach { record ->
                put(
                    JSONObject()
                        .put("xhsId", record.xhsId)
                        .put("nickname", record.nickname)
                        .put("gender", record.gender)
                        .put("ipLocation", record.ipLocation)
                        .put("comment", record.comment)
                        .put(
                            "labelStatus",
                            ArchiveLabelStatus.fromStorageValue(record.labelStatus).storageValue
                        )
                        .put("labelReason", record.labelReason)
                        .put("createdAt", record.createdAt)
                        .put("updatedAt", record.updatedAt)
                )
            }
        }.toString()
    }

    private fun decodeRecords(value: String): List<ArchivedMessageRecord> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val xhsId = item.optString("xhsId").trim()
                    if (xhsId.isBlank()) continue
                    add(
                        ArchivedMessageRecord(
                            xhsId = xhsId,
                            nickname = item.optString("nickname"),
                            gender = item.optString("gender"),
                            ipLocation = item.optString("ipLocation"),
                            comment = item.optString("comment"),
                            labelStatus = ArchiveLabelStatus.fromStorageValue(
                                item.optString("labelStatus")
                            ).storageValue,
                            labelReason = item.optString("labelReason"),
                            createdAt = item.optLong("createdAt", 0L),
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }
}
