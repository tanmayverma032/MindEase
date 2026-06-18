package com.mindease.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")

        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PHONE = stringPreferencesKey("user_phone")

        // Sleep detection
        val SHIFT_TYPE = stringPreferencesKey("shift_type") // "day" or "night"
        val SLEEP_ONBOARDING_DONE = booleanPreferencesKey("sleep_onboarding_done")
    }

    // ---------------- FLOWS (SAFE - NO NULLS) ---------------- //

    val authTokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val token = prefs[AUTH_TOKEN] ?: ""
        Log.d("DATASTORE", "Token fetched: $token")
        token
    }

    val userIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val id = prefs[USER_ID] ?: ""
        Log.d("DATASTORE", "UserId fetched: $id")
        id
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map {
        it[IS_LOGGED_IN] ?: false
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map {
        it[USER_NAME] ?: ""
    }

    val userEmailFlow: Flow<String> = context.dataStore.data.map {
        it[USER_EMAIL] ?: ""
    }

    val userPhoneFlow: Flow<String> = context.dataStore.data.map {
        it[USER_PHONE] ?: ""
    }

    val shiftTypeFlow: Flow<String> = context.dataStore.data.map {
        it[SHIFT_TYPE] ?: "day"
    }

    val sleepOnboardingDoneFlow: Flow<Boolean> = context.dataStore.data.map {
        it[SLEEP_ONBOARDING_DONE] ?: false
    }

    // ---------------- SAVE ---------------- //

    suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_TOKEN] = token
            prefs[IS_LOGGED_IN] = true
        }
        Log.d("DATASTORE", "Token saved: $token")
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID] = userId
        }
        Log.d("DATASTORE", "UserId saved: $userId")
    }

    suspend fun saveProfile(name: String, email: String, phone: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name.ifBlank { "User" } // ✅ fallback safety
            prefs[USER_EMAIL] = email
            prefs[USER_PHONE] = phone
        }
        Log.d("DATASTORE", "Profile saved: $name")
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name.ifBlank { "User" }
        }
    }

    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_EMAIL] = email
        }
    }

    suspend fun saveUserPhone(phone: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_PHONE] = phone
        }
    }

    suspend fun saveShiftType(type: String) {
        context.dataStore.edit { prefs ->
            prefs[SHIFT_TYPE] = type
            prefs[SLEEP_ONBOARDING_DONE] = true
        }
        Log.d("DATASTORE", "Shift type saved: $type")
    }

    // ---------------- CLEAR ---------------- //

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            // Only clear auth tokens — preserve user profile data (name, email, phone)
            // so that re-login with the same account restores the user's info
            prefs.remove(AUTH_TOKEN)
            prefs.remove(IS_LOGGED_IN)
            // Keep USER_ID so we can match profile data on re-login
            // Keep USER_NAME, USER_EMAIL, USER_PHONE for persistence
            // Keep SHIFT_TYPE, SLEEP_ONBOARDING_DONE as user preferences
        }
        Log.d("DATASTORE", "Auth session cleared (profile data preserved)")
    }

    // ---------------- SCAN TIMESTAMPS (SharedPrefs) ---------------- //

    private val scanPrefs by lazy {
        context.getSharedPreferences("scan_timestamps", Context.MODE_PRIVATE)
    }

    fun getScanTimestamps(): MutableList<ScanTimestampEntry> {
        val json = scanPrefs.getString("timestamps_json", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<ScanTimestampEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(ScanTimestampEntry(
                    scanKey = obj.optString("scanKey", ""),
                    timestamp = obj.optString("timestamp", ""),
                    score = if (obj.has("score")) obj.optInt("score") else null,
                    level = obj.optString("level", null)
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("DATASTORE", "Error reading scan timestamps: ${e.message}")
            mutableListOf()
        }
    }

    fun saveScanTimestamps(entries: List<ScanTimestampEntry>) {
        try {
            val arr = org.json.JSONArray()
            entries.forEach { entry ->
                val obj = org.json.JSONObject()
                obj.put("scanKey", entry.scanKey)
                obj.put("timestamp", entry.timestamp)
                entry.score?.let { obj.put("score", it) }
                entry.level?.let { obj.put("level", it) }
                arr.put(obj)
            }
            scanPrefs.edit().putString("timestamps_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("DATASTORE", "Error saving scan timestamps: ${e.message}")
        }
    }

    // ---------------- HIDDEN (DELETED) SCANS ---------------- //

    fun getHiddenScans(): Set<String> {
        return scanPrefs.getStringSet("hidden_scans", emptySet()) ?: emptySet()
    }

    fun addHiddenScan(scanFingerprint: String) {
        val current = getHiddenScans().toMutableSet()
        current.add(scanFingerprint)
        scanPrefs.edit().putStringSet("hidden_scans", current).apply()
        Log.d("DATASTORE", "Hidden scan: $scanFingerprint (total: ${current.size})")
    }
}

/**
 * Represents a locally-saved scan timestamp entry.
 * Backend doesn't return timestamps, so we save them when scans happen.
 */
data class ScanTimestampEntry(
    val scanKey: String,
    val timestamp: String,
    val score: Int?,
    val level: String?
)

/**
 * Generate a unique fingerprint for a scan based on ALL input parameters.
 * Used to identify scans for hiding (delete) since backend doesn't provide IDs.
 */
fun generateScanFingerprint(item: com.mindease.network.HistoryItem): String {
    return "${item.blink_rate}_${item.heart_rate}_${item.sleep_hours}_${item.steps}_" +
           "${item.post_exercise_hr}_${item.age}_${item.gender}_${item.worklife}_" +
           "${item.chest_pain}_${item.cholesterol}_${item.ecg_result}_${item.body_temp}_" +
           "${item.stress_score}_${item.stress_level}"
}