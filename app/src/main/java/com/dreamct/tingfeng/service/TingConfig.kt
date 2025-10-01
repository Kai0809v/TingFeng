package com.dreamct.tingfeng.service

import android.content.Context

object TingConfig {

    private const val PREF_NAME = "ting_config"
    private const val KEY_SHOULD_RESTART = "should_restart"


    @JvmStatic
    fun setShouldRestart(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOULD_RESTART, value)
            .apply()
    }

    @JvmStatic
    fun shouldRestart(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_RESTART, false)
    }
}
