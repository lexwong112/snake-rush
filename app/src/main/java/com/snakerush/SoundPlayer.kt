package com.snakerush

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Thin wrapper around [SoundPool] for Snake Rush's small synthesized SFX set
 * (see `tools/generate_sounds.py` for how the `res/raw` clips are produced).
 * The engine never knows about this — it only fires plain callbacks that
 * [MainActivity] routes here.
 */
class SoundPlayer(context: Context) {

    enum class Effect { EAT, GAME_OVER, START, PAUSE }

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: Map<Effect, Int> = mapOf(
        Effect.EAT to soundPool.load(context, R.raw.sfx_eat, 1),
        Effect.GAME_OVER to soundPool.load(context, R.raw.sfx_game_over, 1),
        Effect.START to soundPool.load(context, R.raw.sfx_start, 1),
        Effect.PAUSE to soundPool.load(context, R.raw.sfx_pause, 1),
    )

    /** Plays the effect; a no-op if its clip has not finished loading yet. */
    fun play(effect: Effect) {
        val id = soundIds[effect] ?: return
        if (id != 0) soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun pauseAll() = soundPool.autoPause()

    fun resumeAll() = soundPool.autoResume()

    fun release() = soundPool.release()
}
