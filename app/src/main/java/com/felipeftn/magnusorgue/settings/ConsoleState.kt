package com.felipeftn.magnusorgue.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Persistence for the console: last registration, accessories, volume and
 * the four combination pistons. Plain SharedPreferences — a handful of ints
 * doesn't justify DataStore's coroutine ceremony.
 *
 * Stop sets are stored as bitmasks, same encoding the engine uses.
 */
class ConsoleState(context: Context) {

    private val prefs = context.getSharedPreferences("console", Context.MODE_PRIVATE)

    var stopMask: Int
        get() = prefs.getInt("stops", 1)  // Principale pulled on first run
        set(value) = prefs.edit { putInt("stops", value) }

    var tremulant: Boolean
        get() = prefs.getBoolean("tremulant", false)
        set(value) = prefs.edit { putBoolean("tremulant", value) }

    var subOctaveCoupler: Boolean
        get() = prefs.getBoolean("suboctave", false)
        set(value) = prefs.edit { putBoolean("suboctave", value) }

    var volume: Float
        get() = prefs.getFloat("volume", 0.8f)
        set(value) = prefs.edit { putFloat("volume", value) }

    /**
     * Combination pistons. -1 = never set; the UI shows those as empty.
     * A stored 0 is a legitimate "all stops off" combination.
     */
    fun piston(index: Int): Int = prefs.getInt("piston$index", -1)

    fun setPiston(index: Int, mask: Int) = prefs.edit { putInt("piston$index", mask) }
}
