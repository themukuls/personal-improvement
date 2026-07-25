package com.chiefofstaff.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * The SQLCipher passphrase (SYS-09). Generated once, 256 bits, and stored in an
 * EncryptedSharedPreferences file whose master key lives in the Android Keystore (hardware-backed
 * where available). The passphrase therefore never exists in plaintext on disk and never leaves
 * the device — consistent with the privacy boundary (§6.8): only assembled context egresses.
 */
object DatabaseKey {
    private const val PREF_FILE = "cos_secure_prefs"
    private const val KEY_PASSPHRASE = "db_passphrase"

    fun getOrCreate(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        prefs.getString(KEY_PASSPHRASE, null)?.let { return hexToBytes(it) }

        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PASSPHRASE, bytesToHex(fresh)).apply()
        return fresh
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
