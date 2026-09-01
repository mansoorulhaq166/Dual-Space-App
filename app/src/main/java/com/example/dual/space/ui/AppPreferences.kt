package com.example.dual.space.ui

import android.app.ActivityManager
import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** Lightweight local preferences shared with the :black engine process. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_LOW_MEMORY)) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo()
            runCatching { activityManager?.getMemoryInfo(memory) }
            val conservativeDefault = memory.totalMem in 1..(3L * 1024 * 1024 * 1024)
            prefs.edit().putBoolean(KEY_LOW_MEMORY, conservativeDefault).apply()
        }
    }

    var lowMemoryMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_MEMORY, false)
        set(value) { prefs.edit().putBoolean(KEY_LOW_MEMORY, value).apply() }

    val appLockEnabled: Boolean get() = prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)

    fun hiddenKeys(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet().orEmpty()
    fun favoriteKeys(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()
    fun aliasFor(cloneKey: String): String? = prefs.getString(KEY_ALIAS_PREFIX + cloneKey, null)
        ?.trim()?.takeIf { it.isNotEmpty() }
    fun recentKeys(): List<String> = prefs.getString(KEY_RECENT, null)
        ?.split('\n')?.filter { it.isNotBlank() }?.distinct()?.take(MAX_RECENT).orEmpty()

    fun setHidden(cloneKey: String, hidden: Boolean) = mutateSet(KEY_HIDDEN, cloneKey, hidden)
    fun setFavorite(cloneKey: String, favorite: Boolean) = mutateSet(KEY_FAVORITES, cloneKey, favorite)

    fun setAlias(cloneKey: String, alias: String?) {
        val cleaned = alias?.trim()?.take(28)?.takeIf { it.isNotEmpty() }
        val editor = prefs.edit()
        if (cleaned == null) editor.remove(KEY_ALIAS_PREFIX + cloneKey)
        else editor.putString(KEY_ALIAS_PREFIX + cloneKey, cleaned)
        editor.apply()
    }

    fun markLaunched(cloneKey: String) {
        val next = buildList {
            add(cloneKey)
            addAll(recentKeys().filterNot { it == cloneKey })
        }.take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, next.joinToString("\n")).apply()
    }

    fun removeCloneMetadata(cloneKey: String) {
        mutateSet(KEY_HIDDEN, cloneKey, false)
        mutateSet(KEY_FAVORITES, cloneKey, false)
        setAlias(cloneKey, null)
        val next = recentKeys().filterNot { it == cloneKey }
        prefs.edit().putString(KEY_RECENT, next.joinToString("\n")).apply()
    }

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) return false
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        return prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .commit()
    }

    fun verifyPin(pin: String): Boolean {
        val saltText = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expectedText = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return runCatching {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(expectedText, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, hash(pin, salt))
        }.getOrDefault(false)
    }

    fun disablePin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    private fun mutateSet(key: String, value: String, add: Boolean) {
        val next = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (add) next += value else next -= value
        prefs.edit().putStringSet(key, next).apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        repeat(12_000) {
            digest.update(pin.toByteArray(Charsets.UTF_8))
            digest.update(salt)
        }
        return digest.digest()
    }

    companion object {
        const val NAME = "dual_space_settings"
        const val KEY_LOW_MEMORY = "low_memory_mode"
        private const val KEY_HIDDEN = "hidden_clone_keys"
        private const val KEY_FAVORITES = "favorite_clone_keys"
        private const val KEY_RECENT = "recent_clone_keys"
        private const val KEY_ALIAS_PREFIX = "clone_alias_"
        private const val MAX_RECENT = 8
        private const val KEY_PIN_HASH = "app_lock_hash"
        private const val KEY_PIN_SALT = "app_lock_salt"
    }
}
