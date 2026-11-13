package com.tutpro.baresip

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable encrypted spreadsheet export. No plaintext export file is created.
 * AES-256-GCM; PBKDF2-HMAC-SHA256 (600,000 iterations), random salt and nonce.
 * The passphrase must be transferred separately from the export file. */
object CustomerExport {
    private val magic = "SWUSIP-DIRECTORY-1\n".toByteArray()
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password,salt,600_000,256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
        finally { spec.clearPassword() }
    }
    fun seal(csv: String, password: CharArray): ByteArray {
        require(password.size >= 16) { "Use an export passphrase of at least 16 characters" }
        val bytes = csv.toByteArray(Charsets.UTF_8)
        require(bytes.size <= CustomerImport.MAX_BYTES) { "Export exceeds the 5 MB portable spreadsheet limit" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt)); cipher.updateAAD(magic)
        return magic + salt + cipher.iv + cipher.doFinal(bytes)
    }
    fun open(blob: ByteArray, password: CharArray): String {
        require(blob.size in (magic.size+44)..(CustomerImport.MAX_BYTES+magic.size+44))
        require(blob.copyOfRange(0,magic.size).contentEquals(magic)) { "Unknown export format" }
        val start = magic.size
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,blob.copyOfRange(start,start+16)),
            GCMParameterSpec(128,blob.copyOfRange(start+16,start+28)))
        cipher.updateAAD(magic)
        return String(cipher.doFinal(blob.copyOfRange(start+28,blob.size)),Charsets.UTF_8)
    }
    fun spreadsheet(records: List<CustomerRecord>): String {
        fun field(value: String) = "\"" + value.replace("\"","\"\"") + "\""
        return CustomerImport.headers.joinToString(",") + "\r\n" + records.joinToString("\r\n") {
            listOf(it.name,it.phone,it.account,it.schemeCode,it.schemeName,it.category,it.notes).joinToString(",",transform = ::field)
        }
    }
}
