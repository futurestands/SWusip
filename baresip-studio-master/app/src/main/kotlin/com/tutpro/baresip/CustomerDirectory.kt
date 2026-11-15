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

data class CustomerRecord(val id: String, val name: String, val phone: String,
    val account: String, val schemeCode: String, val schemeName: String,
    val category: String, val notes: String = "", val location: String = "", val primary: Boolean = false,
    val originalPhone: String? = null,val phoneStatus: String = "LEGACY_UNVERIFIED",val phoneCorrection: String = "",val phonePlan: String = "")
data class ImportReviewRecord(val sourceRow: Int,val values: List<String>,val status: String,val reason: String)

/** Field-level authenticated encryption. Only random IDs and relationships are
 * stored in cleartext. Queries decrypt locally on Dispatchers.IO after access checks.
 * Keystore invalidation fails closed; no silent data reset or plaintext fallback. */
class CustomerDirectory(context: Context,
    databaseName: String = "swusip-directory.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    private val context: Context = context.applicationContext
    private val directoryKey: SecretKey by lazy { loadKey() }
    private val crypto by lazy { DirectoryCipherPages(::key) { readableDatabase } }
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
        createV2(db)
    }
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS directory_page(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_chunk(id TEXT PRIMARY KEY,batch_id TEXT NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS phone_provenance(id TEXT PRIMARY KEY,customer_id TEXT NOT NULL REFERENCES customer(id),account_id TEXT NOT NULL REFERENCES account(id),phone_id TEXT NOT NULL REFERENCES phone(id),batch_id TEXT NOT NULL REFERENCES import_batch(id) DEFERRABLE INITIALLY DEFERRED,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_review(id TEXT PRIMARY KEY,batch_id TEXT NOT NULL REFERENCES import_batch(id) DEFERRABLE INITIALLY DEFERRED,payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS customer_account_account ON customer_account(account_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS customer_phone_phone ON customer_phone(phone_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS account_phone_phone ON account_phone(phone_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS note_customer ON note(customer_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS provenance_customer ON phone_provenance(customer_id,account_id,phone_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS provenance_account ON phone_provenance(account_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS provenance_phone ON phone_provenance(phone_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS provenance_batch ON phone_provenance(batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS review_batch ON import_review(batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS chunk_batch ON import_chunk(batch_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if(old == 1 && new == 2) createV2(db)
        else error("Unsupported directory migration; existing data retained")
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
        return crypto.seal(table,id,json)
    }
    private fun decrypt(table: String, id: String, payload: String,cache: MutableMap<String,JSONObject> = mutableMapOf()): JSONObject = crypto.open(table,id,payload,cache)
    private fun requireAgent() { check(InstitutionAccess.agent(context)) { "Institutional agent authorisation and device unlock required" } }
    private fun entities(table: String,cache: MutableMap<String,JSONObject> = mutableMapOf()): Map<String, JSONObject> {
        val records = linkedMapOf<String, JSONObject>()
        readableDatabase.rawQuery("SELECT id,payload FROM $table", null).use { c ->
            while (c.moveToNext()) records[c.getString(0)] = decrypt(table, c.getString(0), c.getString(1),cache)
        }
        return records
    }
    fun records(): List<CustomerRecord> = records(mutableMapOf())
    private fun records(cache: MutableMap<String,JSONObject>): List<CustomerRecord> {
        requireAgent()
        val customers = entities("customer",cache); val phones = entities("phone",cache)
        val accounts = entities("account",cache); val schemes = entities("scheme",cache)
        val origins=mutableMapOf<Triple<String,String,String>,JSONObject>()
        readableDatabase.rawQuery("""SELECT p.id,p.customer_id,p.account_id,p.phone_id,p.payload FROM phone_provenance p
            JOIN (SELECT MAX(rowid) latest FROM phone_provenance GROUP BY customer_id,account_id,phone_id) newest ON p.rowid=newest.latest""",null).use { c ->
            while(c.moveToNext()) {
                val origin=decrypt("phone_provenance",c.getString(0),c.getString(4),cache)
                check(origin.getString("customerId")==c.getString(1) && origin.getString("accountId")==c.getString(2) && origin.getString("phoneId")==c.getString(3)) { "Phone provenance relationship mismatch" }
                origins[Triple(c.getString(1),c.getString(2),c.getString(3))]=origin
            }
        }
        val result = mutableListOf<CustomerRecord>()
        readableDatabase.rawQuery("""SELECT c.customer_id,c.account_id,p.phone_id,a.scheme_id
            FROM customer_account c JOIN account a ON a.id=c.account_id
            LEFT JOIN customer_phone p ON p.customer_id=c.customer_id AND EXISTS
                (SELECT 1 FROM account_phone ap WHERE ap.account_id=c.account_id AND ap.phone_id=p.phone_id)""", null).use { c ->
            while(c.moveToNext()) {
                val person = customers.getValue(c.getString(0)); val account = accounts.getValue(c.getString(1))
                val scheme = schemes.getValue(c.getString(3))
                val origin=origins[Triple(c.getString(0),c.getString(1),c.getString(2))]
                result += CustomerRecord(c.getString(0), person.getString("name"),
                    phones[c.getString(2)]?.optString("number") ?: "", account.getString("number"),
                    scheme.getString("code"), scheme.getString("name"), account.optString("category"),
                    person.optString("notes"), person.optString("location"),
                    person.optString("primaryNumber") == phones[c.getString(2)]?.optString("number"),
                    origin?.getString("original"),origin?.getString("status") ?: "LEGACY_UNVERIFIED",origin?.optString("correctionReason").orEmpty(),origin?.optString("policyVersion").orEmpty())
            }
        }
        requireAgent()
        return result
    }
    fun lookup(number: String): List<CustomerRecord> {
        return CustomerMatching.lookup(records(),number,DirectoryTelephoneSettings.normalisation(context))
    }

    data class ImportResult(val accepted: Int, val updated: Int, val skipped: Int,
        val rejected: Int, val errors: List<String>, val batchId: String,val corrected: Int = 0,val ambiguous: Int = 0) {
        val validRows: Int get() = accepted+updated+skipped
    }

    fun preview(rows: List<CustomerImportRow>): List<CustomerImportRow> {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        val cache=mutableMapOf<String,JSONObject>()
        val current=records(cache)
        val catalog=entities("scheme",cache).values.map { SchemeEntry(it.getString("code"),it.getString("name")) }
        return CustomerImportPlanner.preview(current,rows,catalog,DirectoryTelephoneSettings.normalisation(context))
    }

    /** Atomic, additive import. Existing ownership and scheme conflicts are
     * rejected; adding a phone to an existing account is an authorised update.
     * Every created entity/link is journalled for latest-batch undo. */
    fun import(rows: List<CustomerImportRow>): ImportResult {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        require(rows.size <= CustomerImport.MAX_ROWS) { "Import exceeds 10,000 rows" }
        val db = writableDatabase; val batch = UUID.randomUUID().toString()
        var accepted = 0; var updated = 0; var skipped = 0; var rejected = 0; var corrected = 0; var ambiguous = 0
        val errors = mutableListOf<String>()
        val actor=OfflineAuthorisation.identity(context)
        val policy=DirectoryTelephoneSettings.normalisation(context)
        val pages=crypto.Writer(); val journal=crypto.JournalWriter(batch)
        db.beginTransaction()
        try {
            val cache=mutableMapOf<String,JSONObject>()
            val current = records(cache)
            val schemes = entities("scheme",cache).toMutableMap()
            val accounts = entities("account",cache).toMutableMap()
            val phones = entities("phone",cache).toMutableMap()
            val planned=CustomerImportPlanner.preview(current,rows,schemes.values.map { SchemeEntry(it.getString("code"),it.getString("name")) },policy)
            val byAccount = current.groupBy { it.schemeCode.lowercase(java.util.Locale.ROOT) to it.account }
                .mapValues { it.value.toMutableList() }.toMutableMap()
            val byScheme = schemes.entries.associate { it.value.getString("code").lowercase(java.util.Locale.ROOT) to it.key }.toMutableMap()
            val accountIds = accounts.entries.associate { (it.value.getString("schemeId") to it.value.getString("number")) to it.key }.toMutableMap()
            val phoneIds = phones.entries.associate { it.value.getString("number") to it.key }.toMutableMap()
            fun create(table: String, data: JSONObject, foreign: Map<String,String> = emptyMap()): String {
                val id = UUID.randomUUID().toString()
                val values = ContentValues().apply {
                    put("id",id); put("payload",pages.add(table,id,data))
                    foreign.forEach { (column,value) -> put(column,value) }
                }
                db.insertOrThrow(table,null,values)
                when(table) {
                    "scheme" -> { schemes[id] = data; byScheme[data.getString("code").lowercase(java.util.Locale.ROOT)] = id }
                    "account" -> { accounts[id] = data; accountIds[data.getString("schemeId") to data.getString("number")] = id }
                    "phone" -> { phones[id] = data; phoneIds[data.getString("number")] = id }
                }
                journal.add(JSONObject().put("table",table).put("id",id)
                    .put("after",fingerprint(values.getAsString("payload"))))
                return id
            }
            fun replaceAccount(id: String,data: JSONObject) {
                val before=db.rawQuery("SELECT payload FROM account WHERE id=?",arrayOf(id)).use { c -> check(c.moveToFirst());c.getString(0) }
                val after=pages.add("account",id,data)
                db.update("account",ContentValues().apply { put("payload",after) },"id=?",arrayOf(id))
                journal.add(JSONObject().put("table","account").put("id",id).put("before",before).put("after",fingerprint(after)))
                accounts[id]=data
            }
            fun link(table: String, left: Pair<String,String>, right: Pair<String,String>) {
                val values = ContentValues().apply { put(left.first,left.second); put(right.first,right.second) }
                if(db.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_IGNORE) != -1L)
                    journal.add(JSONObject().put("table",table).put("leftKey",left.first).put("left",left.second)
                        .put("rightKey",right.first).put("right",right.second))
            }
            fun reject(row: CustomerImportRow,problem: String) {
                rejected++; errors += "Row ${row.row}: $problem"
                val uncertain=row.phone?.status==PhoneValidationStatus.AMBIGUOUS || row.scheme?.error?.startsWith("Ambiguous")==true
                if(uncertain) ambiguous++
                create("import_review",JSONObject().put("sourceRow",row.row)
                    .put("values",JSONArray(row.values.take(7).map { it.take(512) }))
                    .put("truncated",row.values.any { it.length>512 }).put("status",if(uncertain) "AMBIGUOUS" else "REJECTED")
                    .put("reason",problem).put("original",row.phone?.original?.take(2000).orEmpty())
                    .put("canonical",row.phone?.canonical ?: JSONObject.NULL).put("phoneStatus",row.phone?.status?.name ?: "INVALID")
                    .put("correctionReason",row.phone?.correctionReason.orEmpty()).put("policyVersion",policy.version)
                    .put("createdAt",System.currentTimeMillis()).put("actor",actor),mapOf("batch_id" to batch))
            }
            planned.forEach { row ->
                if(row.error != null) { reject(row,row.error);return@forEach }
                val v = row.values; val phone=requireNotNull(row.phone); val number=requireNotNull(phone.canonical)
                val resolved=requireNotNull(row.scheme)
                val accountKey = resolved.code.lowercase(java.util.Locale.ROOT) to v[2]
                val existing = byAccount[accountKey].orEmpty()
                val schemeIdExisting = byScheme[accountKey.first]
                val scheme = schemeIdExisting?.let { schemes.getValue(it) }
                if(existing.any { !it.name.equals(v[0],true) || (v[5].isNotBlank() && it.category.isNotBlank() && it.category != v[5]) } ||
                    (scheme != null && SchemeNames.key(scheme.getString("name")) != SchemeNames.key(resolved.name))) {
                    reject(row,"Account ownership, category or scheme conflict"); return@forEach
                }
                val now = System.currentTimeMillis()
                val schemeId = schemeIdExisting ?: create("scheme",JSONObject().put("code",resolved.code).put("name",resolved.name)
                    .put("nameKey",SchemeNames.key(resolved.name)).put("generatedCode",resolved.generated))
                val accountId = accountIds[schemeId to v[2]] ?: create("account",JSONObject().put("number",v[2]).put("schemeId",schemeId)
                    .put("category",v[5]).put("createdAt",now),mapOf("scheme_id" to schemeId))
                val account=accounts.getValue(accountId)
                var filledCategory=false
                if(account.optString("category").isBlank() && v[5].isNotBlank()) {
                    replaceAccount(accountId,JSONObject(account.toString()).put("category",v[5]).put("updatedAt",now).put("updatedBy",actor))
                    filledCategory=true
                    byAccount[accountKey]?.let { entries -> for(i in entries.indices) entries[i]=entries[i].copy(category=v[5]) }
                }
                // Name alone never merges different people. Additional numbers on
                // the same account can extend an existing, matching customer.
                val customerId = existing.firstOrNull()?.id ?: create("customer",JSONObject()
                    .put("name",v[0]).put("notes",v[6]).put("primaryNumber",number)
                    .put("createdAt",now).put("updatedAt",now).put("createdBy",actor))
                val phoneId = phoneIds[number] ?:
                    create("phone",JSONObject().put("number",number))
                link("customer_account","customer_id" to customerId,"account_id" to accountId)
                link("customer_phone","customer_id" to customerId,"phone_id" to phoneId)
                link("account_phone","account_id" to accountId,"phone_id" to phoneId)
                create("phone_provenance",JSONObject().put("customerId",customerId).put("accountId",accountId).put("phoneId",phoneId)
                    .put("original",phone.original).put("canonical",number).put("status",phone.status.name).put("kind",phone.kind.name)
                    .put("reason",phone.reason).put("correctionReason",phone.correctionReason).put("policyVersion",phone.policyVersion)
                    .put("importedSchemeName",v[4]).put("createdAt",now).put("actor",actor),
                    mapOf("customer_id" to customerId,"account_id" to accountId,"phone_id" to phoneId,"batch_id" to batch))
                if(phone.status==PhoneValidationStatus.CORRECTED) corrected++
                if(existing.any { it.phone == number }) {
                    if(filledCategory) updated++ else skipped++
                } else {
                    byAccount.getOrPut(accountKey) { mutableListOf() }.add(CustomerRecord(customerId,v[0],number,v[2],resolved.code,resolved.name,accounts.getValue(accountId).optString("category")))
                    if(existing.isEmpty()) accepted++ else updated++
                }
            }
            pages.flush(); journal.flush()
            check(InstitutionAccess.administrator(context)) { "Administrator authorisation expired" }
            db.insertOrThrow("import_batch",null,ContentValues().apply {
                put("id",batch); put("payload",encrypt("import_batch",batch,JSONObject()
                    .put("journalVersion",2).put("createdAt",System.currentTimeMillis()).put("actor",actor)
                    .put("chunkIds",JSONArray(journal.chunkIds)).put("pageIds",JSONArray(pages.pageIds))))
            })
            check(InstitutionAccess.administrator(context)) { "Administrator authorisation expired before commit" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return ImportResult(accepted,updated,skipped,rejected,errors,batch,corrected,ambiguous)
    }

    fun undoLatestImport() {
        check(InstitutionAccess.administrator(context)) { "Administrator role required" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id=db.rawQuery("SELECT id FROM import_batch ORDER BY rowid DESC LIMIT 1",null).use { c ->
                check(c.moveToFirst()) { "No import batch to undo" };c.getString(0)
            }
            val header=decrypt("import_batch",id,crypto.batchPayload(id))
            val tables=setOf("customer","phone","account","scheme","phone_provenance","import_review")
            val links=mapOf("customer_account" to ("customer_id" to "account_id"),"customer_phone" to ("customer_id" to "phone_id"),"account_phone" to ("account_id" to "phone_id"))
            val cache=mutableMapOf<String,JSONObject>()
            fun reverse(changes: JSONArray) {
                for(i in changes.length()-1 downTo 0) {
                    val change = changes.getJSONObject(i); val table = change.getString("table")
                    if(change.has("id")) {
                        check(table in tables)
                        db.rawQuery("SELECT payload FROM $table WHERE id=?",arrayOf(change.getString("id"))).use { entity ->
                            check(entity.moveToFirst() && fingerprint(entity.getString(0)) == change.getString("after")) {
                                "A record changed since import; undo would discard a later edit"
                            }
                            // References alone are insufficient: authenticate immutable page contents too.
                            if(entity.getString(0).startsWith(DirectoryCipherPages.REFERENCE)) decrypt(table,change.getString("id"),entity.getString(0),cache)
                        }
                        if(change.has("before")) db.update(table,ContentValues().apply { put("payload",change.getString("before")) },"id=?",arrayOf(change.getString("id")))
                        else db.delete(table,"id=?",arrayOf(change.getString("id")))
                    }
                    else {
                        val left = change.getString("leftKey"); val right = change.getString("rightKey")
                        check(links[table] == (left to right))
                        check(db.delete(table,"$left=? AND $right=?",arrayOf(change.getString("left"),change.getString("right")))==1) { "An import relationship changed; undo refused" }
                    }
                }
            }
            if(header.optInt("journalVersion")==2) {
                val chunks=header.getJSONArray("chunkIds")
                val ids=(0 until chunks.length()).map { chunks.getString(it) }
                check(ids.distinct().size==ids.size)
                ids.reversed().forEach { chunkId ->
                    val payload=db.rawQuery("SELECT payload FROM import_chunk WHERE id=? AND batch_id=?",arrayOf(chunkId,id)).use { c ->
                        check(c.moveToFirst()) { "Authenticated import chunk missing" };c.getString(0)
                    }
                    check(payload.length <= 64*1024)
                    reverse(decrypt("import_chunk",chunkId,payload).getJSONArray("changes"))
                }
                val pages=header.getJSONArray("pageIds")
                for(i in 0 until pages.length()) {
                    val pageId=pages.getString(i);val reference=DirectoryCipherPages.REFERENCE+pageId
                    for(table in tables+"note") db.rawQuery("SELECT 1 FROM $table WHERE payload=? LIMIT 1",arrayOf(reference)).use { c ->
                        check(!c.moveToFirst()) { "A later record still references this page; undo refused" }
                    }
                    check(db.delete("directory_page","id=?",arrayOf(pageId))==1) { "Import page missing" }
                }
            } else reverse(header.getJSONArray("changes"))
            db.delete("import_batch","id=?",arrayOf(id))
            check(InstitutionAccess.administrator(context)) { "Administrator authorisation expired before undo commit" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun fingerprint(value: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()),Base64.NO_WRAP)

    fun reviewRows(): List<ImportReviewRecord> {
        check(InstitutionAccess.administrator(context))
        val cache=mutableMapOf<String,JSONObject>()
        val result=entities("import_review",cache).values.map { row ->
            val values=row.getJSONArray("values")
            ImportReviewRecord(row.getInt("sourceRow"),(0 until values.length()).map { values.getString(it) },row.getString("status"),row.getString("reason"))
        }
        check(InstitutionAccess.administrator(context));return result
    }

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
        saveInteraction(customerId,InteractionDraft(text))
    }

    fun saveInteraction(customerId: String, draft: InteractionDraft) {
        requireAgent()
        val id = UUID.randomUUID().toString()
        val payload = encrypt("note",id,JSONObject().put("text",draft.text.trim())
            .put("author",OfflineAuthorisation.identity(context)).put("createdAt",System.currentTimeMillis())
            .put("outcome",draft.outcome.name).put("followUp",if(draft.followUpRequired) "PENDING" else "NOT_REQUIRED"))
        requireAgent()
        writableDatabase.insertOrThrow("note",null,ContentValues().apply {
            put("id",id); put("customer_id",customerId)
            put("payload",payload)
        })
    }

    fun interactions(customerId: String): List<CustomerInteraction> {
        requireAgent(); val notes = mutableListOf<CustomerInteraction>()
        readableDatabase.rawQuery("SELECT id,payload FROM note WHERE customer_id=? ORDER BY rowid DESC",arrayOf(customerId)).use { c ->
            while(c.moveToNext()) {
                val note = decrypt("note",c.getString(0),c.getString(1))
                notes += CustomerInteraction(c.getString(0),customerId,note.getString("text"),note.getString("author"),
                    note.getLong("createdAt"),InteractionOutcome.stored(note.optString("outcome")),
                    FollowUpState.stored(note.optString("followUp")),note.optString("completedBy"),note.optLong("completedAt"))
            }
        }
        requireAgent(); return notes
    }

    fun callNotes(customerId: String): List<String> = interactions(customerId).map {
        "${it.text}\n${it.author} · ${java.util.Date(it.createdAt)}"
    }

    fun pendingFollowUps(): Map<String,Int> {
        requireAgent(); val counts = mutableMapOf<String,Int>()
        readableDatabase.rawQuery("SELECT id,customer_id,payload FROM note",null).use { c ->
            while(c.moveToNext()) {
                val note = decrypt("note",c.getString(0),c.getString(2))
                if(FollowUpState.stored(note.optString("followUp")) == FollowUpState.PENDING)
                    counts[c.getString(1)] = counts.getOrDefault(c.getString(1),0) + 1
            }
        }
        requireAgent(); return counts
    }

    fun completeFollowUp(customerId: String, noteId: String) {
        requireAgent(); val db = writableDatabase
        db.beginTransaction()
        try {
            val note = db.rawQuery("SELECT payload FROM note WHERE id=? AND customer_id=?",arrayOf(noteId,customerId)).use { c ->
                check(c.moveToFirst()) { "Interaction missing" }; decrypt("note",noteId,c.getString(0))
            }
            check(FollowUpState.stored(note.optString("followUp")) == FollowUpState.PENDING) { "Follow-up is not pending" }
            note.put("followUp",FollowUpState.COMPLETE.name).put("completedBy",OfflineAuthorisation.identity(context))
                .put("completedAt",System.currentTimeMillis())
            val payload = encrypt("note",noteId,note)
            requireAgent()
            check(db.update("note",ContentValues().apply { put("payload",payload) },"id=? AND customer_id=?",arrayOf(noteId,customerId)) == 1)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
