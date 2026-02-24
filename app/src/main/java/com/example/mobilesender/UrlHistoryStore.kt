package com.example.mobilesender

import android.content.Context

object UrlHistoryStore {
    private const val PREF_NAME = "url_history"
    private const val KEY_H5_URLS = "h5_urls"
    private const val SEP = "\n"
    private const val MAX_SIZE = 12

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_H5_URLS, "")
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP).map { it.trim() }.filter { it.isNotBlank() }
    }

    fun save(context: Context, url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return

        val list = load(context).toMutableList()
        list.removeAll { it.equals(clean, ignoreCase = true) }
        list.add(0, clean)
        val limited = list.take(MAX_SIZE)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_H5_URLS, limited.joinToString(SEP))
            .apply()
    }
}
