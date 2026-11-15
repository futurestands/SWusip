package com.tutpro.baresip

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Same non-exportable, authenticated Keystore key as v1. Pages are immutable.
 * AAD authenticates the page ID; its encrypted entry keys bind table + record ID.
 * Plaintext page caches belong to one authorised operation, never a global session.
 */
internal class DirectoryCipherPages(private val key: () -> SecretKey,private val db: () -> SQLiteDatabase) {
    companion object {
        const val REFERENCE = "SWP2:"
        const val PAGE_BYTES = 64*1024
        const val PAGE_ENTRIES = 128
        const val CHUNK_BYTES = 32*1024
    }
    fun seal(table: String,id: String,data: JSONObject): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key()); cipher.updateAAD("$table:$id".toByteArray())
        return Base64.encodeToString(cipher.iv+cipher.doFinal(data.toString().toByteArray()),Base64.NO_WRAP)
    }
    fun open(table: String,id: String,payload: String,cache: MutableMap<String,JSONObject> = mutableMapOf()): JSONObject {
        if(!payload.startsWith(REFERENCE)) return direct(table,id,payload)
        val pageId=payload.removePrefix(REFERENCE)
        require(pageId.matches(Regex("[0-9a-f-]{36}"))) { "Invalid encrypted page reference" }
        val page=cache.getOrPut(pageId) {
            val raw=db().rawQuery("SELECT payload FROM directory_page WHERE id=?",arrayOf(pageId)).use { c ->
                check(c.moveToFirst()) { "Encrypted directory page missing" }; c.getString(0)
            }
            check(raw.length <= 100*1024) { "Encrypted directory page exceeds its bound" }
            direct("directory_page",pageId,raw).also {
                check(it.getInt("version") == 2 && it.getJSONObject("entries").length() <= PAGE_ENTRIES)
            }
        }
        // A reference moved to a different row/table must never select another entry.
        return page.getJSONObject("entries").getJSONObject("$table:$id")
    }
    private fun direct(table: String,id: String,payload: String): JSONObject {
        val bytes=Base64.decode(payload,Base64.NO_WRAP)
        require(bytes.size >= 28) { "Invalid encrypted directory payload" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD("$table:$id".toByteArray())
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
    }

    inner class Writer {
        val pageIds=mutableListOf<String>()
        private var id=UUID.randomUUID().toString()
        private var entries=JSONObject()
        private var bytes=64
        fun add(table: String,recordId: String,value: JSONObject): String {
            val name="$table:$recordId"
            val encoded=value.toString()
            val size=encoded.toByteArray().size+name.toByteArray().size+8
            require(size+64 <= PAGE_BYTES) { "Record exceeds encrypted-page size; review its cell content" }
            if(entries.length() > 0 && (bytes+size > PAGE_BYTES || entries.length() >= PAGE_ENTRIES || entries.has(name))) flush()
            entries.put(name,JSONObject(encoded)); bytes+=size
            return REFERENCE+id
        }
        fun flush() {
            if(entries.length() == 0) return
            val body=JSONObject().put("version",2).put("entries",entries)
            check(body.toString().toByteArray().size <= PAGE_BYTES)
            db().insertOrThrow("directory_page",null,ContentValues().apply {
                put("id",id); put("payload",seal("directory_page",id,body))
            })
            pageIds.add(id); id=UUID.randomUUID().toString(); entries=JSONObject(); bytes=64
        }
    }

    inner class JournalWriter(private val batch: String) {
        val chunkIds=mutableListOf<String>()
        private var changes=JSONArray()
        private var bytes=64
        fun add(change: JSONObject) {
            val size=change.toString().toByteArray().size+2
            require(size+64 <= CHUNK_BYTES)
            if(changes.length() > 0 && (bytes+size > CHUNK_BYTES || changes.length() >= PAGE_ENTRIES)) flush()
            changes.put(change); bytes+=size
        }
        fun flush() {
            if(changes.length() == 0) return
            val id=UUID.randomUUID().toString()
            db().insertOrThrow("import_chunk",null,ContentValues().apply {
                put("id",id); put("batch_id",batch)
                put("payload",seal("import_chunk",id,JSONObject().put("changes",changes)))
            })
            chunkIds.add(id); changes=JSONArray(); bytes=64
        }
    }

    /** Read old oversized Base64 journals in bounded SQL cells, not a giant cursor row. */
    fun batchPayload(id: String): String {
        val length=db().rawQuery("SELECT length(payload) FROM import_batch WHERE id=?",arrayOf(id)).use { c ->
            check(c.moveToFirst()) { "Import batch missing" }; c.getInt(0)
        }
        require(length in 1..(32*1024*1024)) { "Import journal size is invalid" }
        val text=StringBuilder(length)
        var offset=1
        while(offset <= length) {
            db().rawQuery("SELECT substr(payload,?,?) FROM import_batch WHERE id=?",arrayOf(offset.toString(),CHUNK_BYTES.toString(),id)).use { c ->
                check(c.moveToFirst()); text.append(c.getString(0))
            }
            offset+=CHUNK_BYTES
        }
        check(text.length == length)
        return text.toString()
    }
}
