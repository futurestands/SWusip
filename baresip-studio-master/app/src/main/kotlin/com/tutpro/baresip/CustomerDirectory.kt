package com.tutpro.baresip

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.security.KeyStore
import java.util.UUID
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CustomerNumbers {
    fun normalise(input: String): String? {
        val raw = input.trim().removePrefix("sip:").removePrefix("sips:")
            .removePrefix("tel:").substringBefore('@').substringBefore(';')
        if (!raw.matches(Regex("[+0-9 ()\\-.]+"))) return null
        var number = raw.filter { it.isDigit() || it == '+' }
        if (number.startsWith("00")) number = "+" + number.drop(2)
        if (number.matches(Regex("0[37][0-9]{8}"))) number = "+256" + number.drop(1)
        if (number.matches(Regex("256[0-9]{9}"))) number = "+$number"
        return number.takeIf { it.matches(Regex("\\+[1-9][0-9]{7,14}")) }
    }
}

data class CustomerRecord(val id: String, val name: String, val phone: String,
    val account: String, val schemeCode: String, val schemeName: String,
    val category: String, val notes: String = "", val location: String = "", val primary: Boolean = false)

/** Field-level authenticated encryption. Only random IDs and relationships are
 * stored in cleartext. Queries decrypt locally on Dispatchers.IO after access checks.
 * Keystore invalidation fails closed; no silent data reset or plaintext fallback. */
class CustomerDirectory(context: Context,
    databaseName: String = "swusip-directory.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    private val context: Context = context.applicationContext
    private val directoryKey: SecretKey by lazy { loadKey() }
    companion object { private val keyLock = Any() }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customer(id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE scheme(id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE account(id TEXT PRIMARY KEY, scheme_id TEXT NOT NULL REFERENCES scheme(id), payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE phone(id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE customer_phone(customer_id TEXT REFERENCES customer(id), phone_id TEXT REFERENCES phone(id), PRIMARY KEY(customer_id,phone_id))")
        db.execSQL("CREATE TABLE customer_account(customer_id TEXT REFERENCES customer(id), account_id TEXT REFERENCES account(id), PRIMARY KEY(customer_id,account_id))")
        db.execSQL("CREATE TABLE account_phone(account_id TEXT REFERENCES account(id), phone_id TEXT REFERENCES phone(id), PRIMARY KEY(account_id,phone_id))")
        db.execSQL("CREATE TABLE import_batch(id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE note(id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customer(id), payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        error("Unsupported directory migration; existing data retained")
    }
    private fun key(): SecretKey = directoryKey
    private fun loadKey(): SecretKey {
        synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("swusip.directory.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("swusip.directory.v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(300)
                .setUnlockedDeviceRequired(true).build())
        }.generateKey()
        }
    }
    private fun encrypt(table: String, id: String, json: JSONObject): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("$table:$id".toByteArray())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(json.toString().toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(table: String, id: String, payload: String): JSONObject {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("$table:$id".toByteArray())
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    private fun requireAgent() { check(InstitutionAccess.agent(context)) { "Institutional agent authorisation and device unlock required" } }
    private fun entities(table: String): Map<String, JSONObject> {
        val records = linkedMapOf<String, JSONObject>()
        readableDatabase.rawQuery("SELECT id,payload FROM $table", null).use { c ->
            while (c.moveToNext()) records[c.getString(0)] = decrypt(table, c.getString(0), c.getString(1))
        }
        return records
    }
    fun records(): List<CustomerRecord> {
        requireAgent()
        val customers = entities("customer"); val phones = entities("phone")
        val accounts = entities("account"); val schemes = entities("scheme")
        val result = mutableListOf<CustomerRecord>()
        readableDatabase.rawQuery("""SELECT c.customer_id,c.account_id,p.phone_id,a.scheme_id
            FROM customer_account c JOIN account a ON a.id=c.account_id
            LEFT JOIN customer_phone p ON p.customer_id=c.customer_id AND EXISTS
                (SELECT 1 FROM account_phone ap WHERE ap.account_id=c.account_id AND ap.phone_id=p.phone_id)""", null).use { c ->
            while(c.moveToNext()) {
                val person = customers.getValue(c.getString(0)); val account = accounts.getValue(c.getString(1))
                val scheme = schemes.getValue(c.getString(3))
                result += CustomerRecord(c.getString(0), person.getString("name"),
                    phones[c.getString(2)]?.optString("number") ?: "", account.getString("number"),
                    scheme.getString("code"), scheme.getString("name"), account.optString("category"),
                    person.optString("notes"), person.optString("location"),
                    person.optString("primaryNumber") == phones[c.getString(2)]?.optString("number"))
            }
        }
        requireAgent()
        return result
    }
    fun lookup(number: String): List<CustomerRecord> {
        return CustomerMatching.lookup(records(),number)
    }

    data class ImportResult(val accepted: Int, val updated: Int, val skipped: Int,
        val rejected: Int, val errors: List<String>, val batchId: String)

    fun preview(rows: List<CustomerImportRow>): List<CustomerImportRow> {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        return CustomerImportPlanner.preview(records(),rows)
    }

    /** Atomic, additive import. Existing ownership and scheme conflicts are
     * rejected; adding a phone to an existing account is an authorised update.
     * Every created entity/link is journalled for latest-batch undo. */
    fun import(rows: List<CustomerImportRow>): ImportResult {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        require(rows.size <= CustomerImport.MAX_ROWS) { "Import exceeds 10,000 rows" }
        val db = writableDatabase; val batch = UUID.randomUUID().toString()
        var accepted = 0; var updated = 0; var skipped = 0; var rejected = 0
        val errors = mutableListOf<String>(); val changes = JSONArray()
        db.beginTransaction()
        try {
            val current = records().toMutableList()
            val schemes = entities("scheme").toMutableMap()
            val accounts = entities("account").toMutableMap()
            val phones = entities("phone").toMutableMap()
            val byAccount = current.groupBy { it.schemeCode.lowercase(java.util.Locale.ROOT) to it.account }
                .mapValues { it.value.toMutableList() }.toMutableMap()
            val byScheme = schemes.entries.associate { it.value.getString("code").lowercase(java.util.Locale.ROOT) to it.key }.toMutableMap()
            val accountIds = accounts.entries.associate { (it.value.getString("schemeId") to it.value.getString("number")) to it.key }.toMutableMap()
            val phoneIds = phones.entries.associate { it.value.getString("number") to it.key }.toMutableMap()
            fun create(table: String, data: JSONObject, foreign: Pair<String,String>? = null): String {
                val id = UUID.randomUUID().toString()
                val values = ContentValues().apply {
                    put("id",id); put("payload",encrypt(table,id,data))
                    foreign?.let { put(it.first,it.second) }
                }
                db.insertOrThrow(table,null,values)
                when(table) {
                    "scheme" -> { schemes[id] = data; byScheme[data.getString("code").lowercase(java.util.Locale.ROOT)] = id }
                    "account" -> { accounts[id] = data; accountIds[data.getString("schemeId") to data.getString("number")] = id }
                    "phone" -> { phones[id] = data; phoneIds[data.getString("number")] = id }
                }
                changes.put(JSONObject().put("table",table).put("id",id)
                    .put("after",fingerprint(values.getAsString("payload"))))
                return id
            }
            fun link(table: String, left: Pair<String,String>, right: Pair<String,String>) {
                val values = ContentValues().apply { put(left.first,left.second); put(right.first,right.second) }
                if(db.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_IGNORE) != -1L)
                    changes.put(JSONObject().put("table",table).put("leftKey",left.first).put("left",left.second)
                        .put("rightKey",right.first).put("right",right.second))
            }
            rows.forEach { row ->
                val problem = row.error ?: CustomerImport.validate(row.values)
                if(problem != null) { rejected++; errors += "Row ${row.row}: $problem"; return@forEach }
                val v = row.values; val number = CustomerNumbers.normalise(v[1])!!
                val accountKey = v[3].lowercase(java.util.Locale.ROOT) to v[2]
                val existing = byAccount[accountKey].orEmpty()
                val schemeIdExisting = byScheme[accountKey.first]
                val scheme = schemeIdExisting?.let { schemes.getValue(it) }
                if(existing.any { !it.name.equals(v[0],true) || it.category != v[5] } ||
                    (scheme != null && !scheme.getString("name").equals(v[4],true))) {
                    rejected++; errors += "Row ${row.row}: account ownership, category or scheme conflict"; return@forEach
                }
                if(existing.any { it.phone == number }) { skipped++; return@forEach }
                val now = System.currentTimeMillis()
                val schemeId = schemeIdExisting ?: create("scheme",JSONObject().put("code",v[3]).put("name",v[4]))
                val accountId = accountIds[schemeId to v[2]] ?: create("account",JSONObject().put("number",v[2]).put("schemeId",schemeId)
                    .put("category",v[5]).put("createdAt",now), "scheme_id" to schemeId)
                // Name alone never merges different people. Additional numbers on
                // the same account can extend an existing, matching customer.
                val customerId = existing.firstOrNull()?.id ?: create("customer",JSONObject()
                    .put("name",v[0]).put("notes",v[6]).put("primaryNumber",number)
                    .put("createdAt",now).put("updatedAt",now).put("createdBy",OfflineAuthorisation.identity(context)))
                val phoneId = phoneIds[number] ?:
                    create("phone",JSONObject().put("number",number))
                link("customer_account","customer_id" to customerId,"account_id" to accountId)
                link("customer_phone","customer_id" to customerId,"phone_id" to phoneId)
                link("account_phone","account_id" to accountId,"phone_id" to phoneId)
                byAccount.getOrPut(accountKey) { mutableListOf() }.add(CustomerRecord(customerId,v[0],number,v[2],v[3],v[4],v[5]))
                if(existing.isEmpty()) accepted++ else updated++
            }
            check(InstitutionAccess.administrator(context)) { "Administrator authorisation expired" }
            db.insertOrThrow("import_batch",null,ContentValues().apply {
                put("id",batch); put("payload",encrypt("import_batch",batch,JSONObject()
                    .put("createdAt",System.currentTimeMillis()).put("actor",OfflineAuthorisation.identity(context)).put("changes",changes)))
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return ImportResult(accepted,updated,skipped,rejected,errors,batch)
    }

    fun undoLatestImport() {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT id,payload FROM import_batch ORDER BY rowid DESC LIMIT 1",null).use { c ->
                check(c.moveToFirst()) { "No import batch to undo" }
                val id = c.getString(0); val changes = decrypt("import_batch",id,c.getString(1)).getJSONArray("changes")
                val tables = setOf("customer","phone","account","scheme","customer_phone","customer_account","account_phone")
                for(i in changes.length()-1 downTo 0) {
                    val change = changes.getJSONObject(i); val table = change.getString("table")
                    check(table in tables)
                    if(change.has("id")) {
                        db.rawQuery("SELECT payload FROM $table WHERE id=?",arrayOf(change.getString("id"))).use { entity ->
                            check(entity.moveToFirst() && fingerprint(entity.getString(0)) == change.getString("after")) {
                                "A record changed since import; undo would discard a later edit"
                            }
                        }
                        db.delete(table,"id=?",arrayOf(change.getString("id")))
                    }
                    else {
                        val left = change.getString("leftKey"); val right = change.getString("rightKey")
                        check(left in setOf("customer_id","account_id") && right in setOf("phone_id","account_id"))
                        db.delete(table,"$left=? AND $right=?",arrayOf(change.getString("left"),change.getString("right")))
                    }
                }
                db.delete("import_batch","id=?",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun fingerprint(value: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()),Base64.NO_WRAP)

    fun updateCustomer(id: String, name: String, location: String) {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        require(name.isNotBlank() && name.length <= 2000 && location.length <= 2000)
        val data = entities("customer")[id] ?: error("Customer missing")
        data.put("name",name.trim()).put("location",location.trim()).put("updatedAt",System.currentTimeMillis())
            .put("updatedBy",OfflineAuthorisation.identity(context))
        check(InstitutionAccess.administrator(context))
        writableDatabase.update("customer",ContentValues().apply { put("payload",encrypt("customer",id,data)) },"id=?",arrayOf(id))
    }

    fun associateAccount(customerId: String, accountNumber: String, schemeCode: String) {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val customer = entities("customer")[customerId] ?: error("Customer missing")
            val schemeId = entities("scheme").entries.single { it.value.getString("code").equals(schemeCode.trim(),true) }.key
            val accountId = entities("account").entries.single {
                it.value.getString("number") == accountNumber.trim() && it.value.getString("schemeId") == schemeId
            }.key
            db.insertWithOnConflict("customer_account",null,ContentValues().apply {
                put("customer_id",customerId); put("account_id",accountId)
            },SQLiteDatabase.CONFLICT_IGNORE)
            db.execSQL("INSERT OR IGNORE INTO account_phone(account_id,phone_id) SELECT ?,phone_id FROM customer_phone WHERE customer_id=?",arrayOf(accountId,customerId))
            customer.put("updatedAt",System.currentTimeMillis()).put("updatedBy",OfflineAuthorisation.identity(context))
            check(InstitutionAccess.administrator(context))
            db.update("customer",ContentValues().apply { put("payload",encrypt("customer",customerId,customer)) },"id=?",arrayOf(customerId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun addCallNote(customerId: String, text: String) {
        requireAgent(); require(text.isNotBlank() && text.length <= 2000)
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("note",null,ContentValues().apply {
            put("id",id); put("customer_id",customerId)
            put("payload",encrypt("note",id,JSONObject().put("text",text.trim())
                .put("author",OfflineAuthorisation.identity(context)).put("createdAt",System.currentTimeMillis())))
        })
    }

    fun callNotes(customerId: String): List<String> {
        requireAgent(); val notes = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT id,payload FROM note WHERE customer_id=? ORDER BY rowid DESC",arrayOf(customerId)).use { c ->
            while(c.moveToNext()) {
                val note = decrypt("note",c.getString(0),c.getString(1))
                notes += "${note.getString("text")}\n${note.getString("author")} · ${java.util.Date(note.getLong("createdAt"))}"
            }
        }
        requireAgent(); return notes
    }
}
