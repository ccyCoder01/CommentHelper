package com.ccy.xhscommenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val fixedTextKey = stringPreferencesKey("fixed_text")
    private val targetPackageKey = stringPreferencesKey("target_package")
    private val targetGenderKey = stringPreferencesKey("target_gender")
    private val targetIpLocationKey = stringPreferencesKey("target_ip_location")
    private val profileRequirementKey = stringPreferencesKey("profile_requirement")

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            fixedText = prefs[fixedTextKey] ?: UserSettings.DEFAULT_FIXED_TEXT,
            targetPackageName = prefs[targetPackageKey] ?: UserSettings.DEFAULT_TARGET_PACKAGE_NAME,
            targetGender = prefs[targetGenderKey].orEmpty(),
            targetIpLocation = prefs[targetIpLocationKey].orEmpty(),
            profileRequirement = prefs[profileRequirementKey].orEmpty()
        )
    }

    suspend fun saveFixedText(text: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[fixedTextKey] = text
        }
    }

    suspend fun saveTargetPackageName(packageName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[targetPackageKey] = packageName
        }
    }

    suspend fun saveProfileCriteria(gender: String, ipLocation: String, profileRequirement: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[targetGenderKey] = gender
            prefs[targetIpLocationKey] = ipLocation
            prefs[profileRequirementKey] = profileRequirement
        }
    }
}
