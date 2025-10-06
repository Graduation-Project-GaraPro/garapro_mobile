package com.example.garapro.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.garapro.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.USER_PREFERENCES
)

class TokenManager(private val context: Context) {

    private val ACCESS_TOKEN_KEY = stringPreferencesKey(Constants.ACCESS_TOKEN_KEY)

    suspend fun saveAccessToken(accessToken: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
        }
    }

    fun getAccessToken(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[ACCESS_TOKEN_KEY]
        }
    }

    suspend fun getAccessTokenSync(): String? {
        return context.dataStore.data.first()[ACCESS_TOKEN_KEY]
    }

    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
        }
    }
}