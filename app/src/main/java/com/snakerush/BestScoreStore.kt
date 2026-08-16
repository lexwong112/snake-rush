package com.snakerush

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The app-wide DataStore Preferences file, shared by every [BestScoreStore]. */
private val Context.bestScoreDataStore by preferencesDataStore(name = "snake_rush_prefs")

/**
 * Persists the all-time best score with DataStore Preferences.
 *
 * Exposed as a cold [Flow] so the HUD can react to the stored value when it
 * loads or changes; [recordScore] only ever *increases* the stored value, so
 * an out-of-order write can never lower the best score.
 */
class BestScoreStore(private val context: Context) {

    private val bestScoreKey = intPreferencesKey("best_score")

    /** The stored best score (0 when nothing has been saved yet). */
    val bestScore: Flow<Int> = context.bestScoreDataStore.data.map { prefs ->
        prefs[bestScoreKey] ?: 0
    }

    /** Persists [score] if it beats the currently stored best score. */
    suspend fun recordScore(score: Int) {
        context.bestScoreDataStore.edit { prefs ->
            val current = prefs[bestScoreKey] ?: 0
            if (score > current) prefs[bestScoreKey] = score
        }
    }
}
