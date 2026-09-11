package com.labuda.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LabudaSecurity {
    private const val PREFS = "labuda_security"
    private const val AES_ALIAS = "LABUDA_DATA_KEY_V1"
    private const val DEVICE_ALIAS = "LABUDA_DEVICE_KEY_V1"
    private const val PIN_HASH = "pin_hash"
    private const val PIN_SALT = "pin_salt"
    private const val TOTP_SECRET = "totp_secret"
    private const val SETUP_DONE = "setup_done"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun aesKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(AES_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(AES_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build())
        return kg.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val iv = cipher.iv
        val data = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + data, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val raw = Base64.decode(value, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val data = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun ensureDeviceKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(DEVICE_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(DEVICE_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build())
        generator.generateKeyPair()
    }

    private fun deviceProof(): Boolean = runCatching {
        ensureDeviceKey()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = ks.getEntry(DEVICE_ALIAS, null) as KeyStore.PrivateKeyEntry
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(entry.privateKey)
        signer.update(challenge)
        val signature = signer.sign()
        val verifier = java.security.Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(entry.certificate.publicKey)
        verifier.update(challenge)
        verifier.verify(signature)
    }.getOrDefault(false)

    fun isSetup(context: Context): Boolean = prefs(context).getBoolean(SETUP_DONE, false)

    fun createSetup(context: Context, pin: String): String {
        require(pin.length >= 6) { "PIN должен содержать минимум 6 символов" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val secret = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val hash = pinHash(pin, salt)
        ensureDeviceKey()
        prefs(context).edit()
            .putString(PIN_SALT, b64(salt))
            .putString(PIN_HASH, b64(hash))
            .putString(TOTP_SECRET, encrypt(base32(secret)))
            .putBoolean(SETUP_DONE, true)
            .apply()
        return base32(secret)
    }

    fun verify(context: Context, pin: String, otp: String): Boolean = runCatching {
        val p = prefs(context)
        val salt = Base64.decode(p.getString(PIN_SALT, ""), Base64.NO_WRAP)
        val expected = Base64.decode(p.getString(PIN_HASH, ""), Base64.NO_WRAP)
        val pinOk = java.security.MessageDigest.isEqual(expected, pinHash(pin, salt))
        val secret = decrypt(p.getString(TOTP_SECRET, "") ?: "")
        pinOk && verifyTotp(secret, otp) && deviceProof()
    }.getOrDefault(false)

    fun protectedValue(context: Context, value: String): String = encrypt(value)
    fun unprotectValue(value: String): String = decrypt(value)

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun verifyTotp(base32Secret: String, code: String): Boolean {
        if (!code.matches(Regex("\\d{6}"))) return false
        val now = System.currentTimeMillis() / 1000L / 30L
        return (-1L..1L).any { totp(base32Secret, now + it) == code }
    }

    private fun totp(base32Secret: String, counter: Long): String {
        val secret = base32Decode(base32Secret)
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.lastIndex].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private fun base32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(alphabet[(buffer shr bits) and 31])
            }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    private fun base32Decode(value: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (c in value.uppercase()) {
            val v = alphabet.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
