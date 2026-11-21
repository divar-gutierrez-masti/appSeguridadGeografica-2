package com.upds.tesis.seguridad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AESHelper {

    private val KEY_ALIAS = "TESIS_UPDS_AES_KEY"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private val GCM_TAG_LENGTH = 128

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): String {
        val key = getKey()
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)

        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ivLength = iv.size.toByte()

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(1 + iv.size + cipherText.size).apply {
            this[0] = ivLength
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(cipherText, 0, this, 1 + iv.size, cipherText.size)
        }

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherTextCombined: String): String {
        val key = getKey()

        val combinedBytes = Base64.decode(cipherTextCombined, Base64.NO_WRAP)

        val ivLength = combinedBytes[0].toInt()
        val iv = combinedBytes.sliceArray(1 until 1 + ivLength)
        val cipherText = combinedBytes.sliceArray(1 + ivLength until combinedBytes.size)

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
