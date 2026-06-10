package com.ccy.xhscommenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentLeadDataStore by preferencesDataStore(name = "recent_lead")

data class RecentLead(
    val comment: String = "",
    val message: String = ""
)

class RecentLeadStore(private val context: Context) {
    private val recentCommentKey = stringPreferencesKey("recent_comment")
    private val recentMessageKey = stringPreferencesKey("recent_message")

    val recentLeadFlow: Flow<RecentLead> = context.recentLeadDataStore.data.map { prefs ->
        RecentLead(
            comment = prefs[recentCommentKey].orEmpty(),
            message = prefs[recentMessageKey].orEmpty()
        )
    }

    suspend fun saveComment(comment: String) {
        context.recentLeadDataStore.edit { prefs ->
            prefs[recentCommentKey] = comment
        }
    }

    suspend fun saveMessage(message: String) {
        context.recentLeadDataStore.edit { prefs ->
            prefs[recentMessageKey] = message
        }
    }
}
