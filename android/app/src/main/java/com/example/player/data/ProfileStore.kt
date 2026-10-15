package com.example.player.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
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
        prefs.getString(KEY_PROFILE, null)?.let { stored ->
            runCatching { json.decodeFromString(Profile.serializer(), decrypt(stored)) }.getOrNull()?.let { return it }
        }
        // Devices whose Keystore is broken keep the login in app-private storage instead.
        return prefs.getString(KEY_PROFILE_PLAIN, null)?.let {
            runCatching { json.decodeFromString(Profile.serializer(), it) }.getOrNull()
        }
    }

    fun save(profile: Profile) {
        val plain = json.encodeToString(Profile.serializer(), profile)
        val encrypted = runCatching { encrypt(plain) }.getOrNull()
        val editor = prefs.edit()
        if (encrypted != null && runCatching { decrypt(encrypted) == plain }.getOrDefault(false)) {
            editor.putString(KEY_PROFILE, encrypted).remove(KEY_PROFILE_PLAIN)
        } else {
            editor.putString(KEY_PROFILE_PLAIN, plain).remove(KEY_PROFILE)
        }
        editor.commit() // synchronous: the login must survive the app being closed right away
    }

    fun clear() {
        prefs.edit().clear().commit()
        cacheFile.delete()
    }

    // Streamed to and from disk: a full provider list can be tens of MB, too big for one String
    // on 1 GB devices like the Mi TV Stick.
    @OptIn(ExperimentalSerializationApi::class)
    fun loadContent(): Content? = runCatching {
        cacheFile.inputStream().buffered().use { json.decodeFromStream(Content.serializer(), it) }
    }.getOrNull()

    @OptIn(ExperimentalSerializationApi::class)
    fun saveContent(content: Content) {
        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        runCatching {
            tmp.outputStream().buffered().use { json.encodeToStream(Content.serializer(), content, it) }
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
        }.onFailure { tmp.delete() }
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
        const val KEY_PROFILE_PLAIN = "profile_plain"
        const val ALIAS = "player_profile_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
