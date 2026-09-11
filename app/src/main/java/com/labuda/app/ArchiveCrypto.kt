package com.labuda.app

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ArchiveCrypto {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    /** Encrypts archive payload using a password supplied at runtime. The password is never embedded in the APK. */
    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(data)
    }

    fun decrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Неверный формат защищённого архива" }
        val salt = payload.copyOfRange(MAGIC.size, MAGIC.size + SALT_SIZE)
        val ivStart = MAGIC.size + SALT_SIZE
        val iv = payload.copyOfRange(ivStart, ivStart + IV_SIZE)
        val encrypted = payload.copyOfRange(ivStart + IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    private val MAGIC = byteArrayOf(0x4c, 0x42, 0x41, 0x31, 0x01)
}
