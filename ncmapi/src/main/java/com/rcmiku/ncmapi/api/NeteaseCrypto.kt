package com.rcmiku.ncmapi.api

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * NCM 官方端点的请求加密（weapi / eapi），自包含实现，对齐 MeiloX 的 Encryptor。
 * 用于 App 直连 music.163.com / interface.music.163.com，不再经代理。
 */
internal object NeteaseCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_SALT = "36cd479b6b5"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    // NCM 网页版 weapi 官方 RSA 公钥（固定）
    private val WEAPI_PUBLIC_KEY: PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(
                Base64.getDecoder().decode(
                    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzm" +
                        "Fbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/" +
                        "DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
                )
            )
        )

    private val secureRandom = SecureRandom()

    /** 返回 form-urlencoded 用的 params / encSecKey。 */
    fun weapi(json: String): Map<String, String> {
        val secret = (0 until 16)
            .map { BASE62[secureRandom.nextInt(BASE62.length)] }
            .joinToString("")
        val first = Base64.getEncoder()
            .encodeToString(aesCbc(json.toByteArray(Charsets.UTF_8), PRESET_KEY, IV))
        val params = Base64.getEncoder()
            .encodeToString(aesCbc(first.toByteArray(Charsets.UTF_8), secret, IV))
        val rsa = Cipher.getInstance("RSA/ECB/NoPadding")
        rsa.init(Cipher.ENCRYPT_MODE, WEAPI_PUBLIC_KEY)
        val encSecKey = rsa
            .doFinal(secret.reversed().toByteArray(Charsets.UTF_8))
            .toHex()
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    /** 返回 form-urlencoded 用的 params（AES-ECB 大写 hex）。 */
    fun eapi(path: String, json: String): Map<String, String> {
        val digest = MessageDigest.getInstance("MD5")
            .digest("nobody${path}use${json}md5forencrypt".toByteArray(Charsets.UTF_8))
            .toHex()
        val plain = "$path-$EAPI_SALT-$json-$EAPI_SALT-$digest"
        val params = aesEcb(plain.toByteArray(Charsets.UTF_8), EAPI_KEY).toHex().uppercase()
        return mapOf("params" to params)
    }

    /** eapi 响应可能带 EAPI-key 的 AES-ECB 加密（e_r 机制），备用解码。 */
    fun eapiDecryptHex(hex: String): String {
        val bytes = hex.decodeHex() ?: return ""
        return aesEcbDecrypt(bytes, EAPI_KEY)
    }

    private fun aesCbc(input: ByteArray, key: String, iv: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        )
        return cipher.doFinal(input)
    }

    private fun aesEcb(input: ByteArray, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(input)
    }

    private fun aesEcbDecrypt(input: ByteArray, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return String(cipher.doFinal(input), Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.decodeHex(): ByteArray? = try {
        val clean = replace("\\s".toRegex(), "")
        val b = ByteArray(clean.length / 2)
        for (i in 0 until b.size) {
            b[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        b
    } catch (e: Exception) {
        null
    }
}
