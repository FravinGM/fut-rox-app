package br.futrox.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda segredos (tokens de sessão) criptografados com AES-256-GCM.
 * A chave fica no Android Keystore: não sai do aparelho e não pode ser extraída nem com root comum.
 * O nome do campo entra como "associated data", então um valor não pode ser trocado por outro.
 */
internal class SecureStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("futrox_secure", Context.MODE_PRIVATE)

    init {
        // Versões antigas guardavam os tokens em texto puro neste arquivo: apaga.
        app.deleteSharedPreferences("futrox")
    }

    /** Checagem barata (sem descriptografar), segura para a thread principal. */
    fun has(name: String) = prefs.contains(name)

    fun get(name: String): String? {
        val raw = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            c.updateAAD(name.toByteArray())
            String(c.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // Valor adulterado ou chave invalidada: descarta em vez de usar.
            prefs.edit().remove(name).commit()
            null
        }
    }

    fun put(values: Map<String, String?>) {
        val ed = prefs.edit()
        for ((name, value) in values) {
            if (value == null) { ed.remove(name); continue }
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.ENCRYPT_MODE, key())
            c.updateAAD(name.toByteArray())
            val ct = c.doFinal(value.toByteArray(Charsets.UTF_8))
            ed.putString(name, Base64.encodeToString(c.iv + ct, Base64.NO_WRAP))
        }
        ed.commit()
    }

    fun clear() { prefs.edit().clear().commit() }

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "futrox_session_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
