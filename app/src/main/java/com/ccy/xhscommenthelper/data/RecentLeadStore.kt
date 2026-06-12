package com.ccy.xhscommenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentLeadDataStore by preferencesDataStore(name = "recent_lead")

data class RecentLead(
    val comment: String = ""
)

class RecentLeadStore(private val context: Context) {
    private val recentCommentKey = stringPreferencesKey("recent_comment")

    val recentLeadFlow: Flow<RecentLead> = context.recentLeadDataStore.data.map { prefs ->
        RecentLead(
            comment = prefs[recentCommentKey].orEmpty()
        )
    }

    suspend fun saveComment(comment: String) {
        context.recentLeadDataStore.edit { prefs ->
            prefs[recentCommentKey] = comment
        }
    }
}
