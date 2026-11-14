package org.ascensionassociation.fltr.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the pairing (URL, token, pin) encrypted with an AES-GCM key that
 * lives in the Android Keystore and never leaves it, as docs/API.md asks.
 * (androidx.security:security-crypto is deprecated, so this uses the
 * Keystore directly.)
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences("fltr_pairing", Context.MODE_PRIVATE)

    fun load(): Pairing? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val json = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).decodeToString()
            FltrApi.JSON.decodeFromString(Pairing.serializer(), json)
        }.getOrElse {
            // The key is gone (e.g. the app's data was restored elsewhere): start over.
            clear()
            null
        }
    }

    fun save(pairing: Pairing) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(FltrApi.JSON.encodeToString(Pairing.serializer(), pairing).encodeToByteArray())
        prefs.edit {
            putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString(KEY_DATA, Base64.encodeToString(data, Base64.NO_WRAP))
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "fltr_pairing_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
    }
}
