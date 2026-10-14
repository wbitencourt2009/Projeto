package com.example.player.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saves the login (which contains the provider password) encrypted with an
 * Android Keystore key, and caches the downloaded channel list as a file.
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "content.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): Profile? {
        val stored = prefs.getString(KEY_PROFILE, null) ?: return null
        return runCatching { json.decodeFromString(Profile.serializer(), decrypt(stored)) }.getOrNull()
    }

    fun save(profile: Profile) {
        prefs.edit().putString(KEY_PROFILE, encrypt(json.encodeToString(Profile.serializer(), profile))).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        cacheFile.delete()
    }

    fun loadContent(): Content? =
        runCatching { json.decodeFromString(Content.serializer(), cacheFile.readText()) }.getOrNull()

    fun saveContent(content: Content) {
        runCatching { cacheFile.writeText(json.encodeToString(Content.serializer(), content)) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }

    private companion object {
        const val KEY_PROFILE = "profile_enc"
        const val ALIAS = "player_profile_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
