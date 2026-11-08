package com.example.whatcanicook.lidl

import android.content.Context

/**
 * Holds the signed-in Lidl cookie jar between launches.
 *
 * Stored in app-private SharedPreferences, so other apps cannot read it. These
 * are session cookies for a grocery account, not payment credentials, and they
 * expire on Lidl's side anyway — but before shipping this should move to
 * EncryptedSharedPreferences so it survives a rooted-device or backup dump.
 */
class LidlSession(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var cookieHeader: String?
        get() = prefs.getString(KEY_COOKIE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    val isConnected: Boolean get() = cookieHeader != null

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS = "lidl_session"
        private const val KEY_COOKIE = "cookie_header"
        private const val KEY_LAST_SYNC = "last_synced_at"

        /**
         * Cookies Lidl sets once a session is authenticated. Seeing one of
         * these is how we know the login actually completed rather than the
         * page merely finishing loading.
         */
        val AUTH_COOKIE_NAMES = listOf(
            "authToken", "ldi-user-context", "ldi-customertoken", "ldi-session-info",
        )

        fun looksAuthenticated(cookieHeader: String?): Boolean {
            val jar = cookieHeader ?: return false
            return AUTH_COOKIE_NAMES.any { name -> jar.contains("$name=") }
        }
    }
}
