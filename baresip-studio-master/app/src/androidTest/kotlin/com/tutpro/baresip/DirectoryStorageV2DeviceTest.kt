package com.tutpro.baresip

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey

/** Real authentication and real Keystore; isolated databases only. Authenticate
 * through Settings so migration checks do not first open the existing directory. */
@RunWith(AndroidJUnit4::class)
class DirectoryStorageV2DeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val session=AuthenticatedDirectoryDeviceTest()
    private val names=mutableListOf<String>()
    private fun name():String = "swusip-synthetic-v2-${UUID.randomUUID()}.db".also { names.add(it) }
    @Before fun authenticate() {
        assumeTrue("Explicit storage verification opt-in",InstrumentationRegistry.getArguments().getString("swusipStorageV2")=="true")
        assumeTrue("Do not interrupt a call",!Call.inCall())
        session.authenticateAdministrator(openDirectory=false)
    }
    @After fun cleanup() {
        names.forEach { context.deleteDatabase(it) }
        session.restoreDisplayFlag()
    }
    private fun rows(text:String)=CustomerImport.parse((CustomerImport.requiredHeaders.joinToString(",")+"\n"+text).byteInputStream(),false)

    @Test fun encryptedExportRestoreKeepsTextAccountsAndRejectsWrongPassword() {
        val password="Synthetic device export passphrase".toCharArray()
        CustomerDirectory(context,name()).use { source ->
            source.import(rows("Synthetic Export One,0700123456,00000001,Synthetic Export Scheme\nSynthetic Export Two,0700123457,00000002,Synthetic Export Scheme"))
            val bytes=CustomerExport.seal(CustomerExport.spreadsheet(source.records()),password)
            assertFalse(String(bytes,Charsets.ISO_8859_1).contains("Synthetic Export"))
            try {CustomerExport.open(bytes,"Wrong synthetic password".toCharArray());fail("Wrong password accepted")} catch(_:javax.crypto.AEADBadTagException) { }
            val restored=CustomerImport.parse(CustomerExport.open(bytes,password).byteInputStream(),false)
            CustomerDirectory(context,name()).use { target ->
                assertEquals(2,target.import(restored).accepted)
                assertEquals(setOf("00000001","00000002"),target.records().map {it.account}.toSet())
                target.undoLatestImport();assertTrue(target.records().isEmpty())
            }
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
            try {CustomerExport.open(bytes,password);fail("Tampered export accepted")} catch(_:javax.crypto.AEADBadTagException) { }
        }
        password.fill('\u0000')
    }

    @Test fun boundedPagedImportReadSearchAndUndoProfile() {
        CustomerDirectory(context,name()).use { directory ->
            val count=if(InstrumentationRegistry.getArguments().getString("swusipLarge")=="true") 10000 else 1000
            val input=rows((1..count).joinToString("\n") { "Synthetic Profile $it,+12025550123,${it.toString().padStart(8,'0')},Synthetic Profile Scheme" })
            val timings=JSONObject().put("rows",count)
            fun <T> timed(label:String,run:()->T):T {
                val start=android.os.SystemClock.elapsedRealtimeNanos()
                return run().also { timings.put(label+"Ms",(android.os.SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0) }
            }
            assertEquals(count,timed("import") { directory.import(input) }.accepted)
            val records=timed("read") { directory.records() };assertEquals(count,records.size)
            assertEquals(count,timed("callerLookup") { CustomerMatching.lookup(records,"sip:+12025550123@pbx.invalid") }.size)
            assertEquals(1,timed("accountSearch") { CustomerMatching.search(records,"00001000") }.size)
            timed("undo") { directory.undoLatestImport() };assertTrue(directory.records().isEmpty())
            InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {putString("stream","\nSYNTHETIC_PAGED_PROFILE $timings\n")})
        }
    }

    @Test fun fourColumnsKeepOriginalsResolveSchemesReviewInvalidAndUndo() {
        CustomerDirectory(context,name()).use { directory ->
            val input=rows("Synthetic One,0700123456,0001, Synthetic Scheme \nSynthetic Two,700123456,0002,synthetic  SCHEME\nSynthetic Bad,123456789,0003,Synthetic Scheme")
            val preview=directory.preview(input)
            assertEquals(2,preview.count { it.error==null });assertEquals(1,preview.count { it.error!=null })
            val result=directory.import(preview)
            assertEquals(2,result.accepted);assertEquals(2,result.corrected);assertEquals(1,result.ambiguous)
            val records=directory.records()
            assertEquals(1,records.map { it.schemeCode }.distinct().size)
            assertEquals(setOf("0001","0002"),records.map {it.account}.toSet())
            assertEquals(setOf("0700123456","700123456"),records.map {it.originalPhone}.toSet())
            assertEquals(2,directory.lookup("sip:+256700123456@pbx.invalid").size)
            assertEquals(1,directory.reviewRows().size)
            assertTrue(directory.reviewRows().single().status=="AMBIGUOUS")
            directory.undoLatestImport()
            assertTrue(directory.records().isEmpty());assertTrue(directory.reviewRows().isEmpty())
            directory.readableDatabase.rawQuery("SELECT COUNT(*) FROM directory_page",null).use {it.moveToFirst();assertEquals(0,it.getInt(0))}
        }
    }

    @Test fun pagedCipherAndReferenceTamperingNeverReturnAnotherCustomer() {
        CustomerDirectory(context,name()).use { directory ->
            val input=rows((1..50).joinToString("\n") {"Synthetic $it,+12025550123,${it.toString().padStart(8,'0')},Synthetic Scheme"})
            assertEquals(50,directory.import(input).accepted)
            val db=directory.writableDatabase
            val refs=mutableListOf<Pair<String,String>>()
            db.rawQuery("SELECT id,payload FROM customer",null).use { while(it.moveToNext()) refs.add(it.getString(0) to it.getString(1)) }
            assertTrue(refs.all { it.second.startsWith(DirectoryCipherPages.REFERENCE) })
            val first=refs.first();val other=refs.first { it.second!=first.second }
            db.beginTransaction()
            try {
                db.update("customer",ContentValues().apply {put("payload",other.second)},"id=?",arrayOf(first.first))
                try { directory.records();fail("Cross-page reference returned data") } catch(_:Exception) { }
            } finally { db.endTransaction() }
            assertEquals(50,directory.records().size)
            val pageId=first.second.removePrefix(DirectoryCipherPages.REFERENCE)
            val cipher=db.rawQuery("SELECT payload FROM directory_page WHERE id=?",arrayOf(pageId)).use {it.moveToFirst();it.getString(0)}
            db.beginTransaction()
            try {
                db.update("directory_page",ContentValues().apply {put("payload",(if(cipher[0]=='A') "B" else "A")+cipher.drop(1))},"id=?",arrayOf(pageId))
                try { directory.records();fail("Corrupt page accepted") } catch(_:Exception) { }
                try { directory.undoLatestImport();fail("Undo accepted corrupt page") } catch(_:Exception) { }
            } finally { db.endTransaction() }
            assertEquals(50,directory.records().size)
            directory.undoLatestImport();assertTrue(directory.records().isEmpty())
        }
    }

    @Test fun journalTamperingIsAtomicAndOptionalCategoryFillCanBeReversed() {
        CustomerDirectory(context,name()).use { directory ->
            directory.import(rows("Synthetic One,700123456,0001,Synthetic Scheme"))
            val code=directory.records().single().schemeCode
            val legacy=CustomerImport.parse((CustomerImport.headers.joinToString(",")+"\nSynthetic One,+256700123456,0001,$code,Synthetic Scheme,Domestic,Do not overwrite old notes").byteInputStream(),false)
            assertTrue(directory.preview(legacy).single().action.contains("category"))
            assertEquals(1,directory.import(legacy).updated)
            assertEquals("Domestic",directory.records().single().category)
            val db=directory.writableDatabase
            val chunk=db.rawQuery("SELECT id,payload FROM import_chunk WHERE batch_id=(SELECT id FROM import_batch ORDER BY rowid DESC LIMIT 1) LIMIT 1",null).use {it.moveToFirst();it.getString(0) to it.getString(1)}
            db.update("import_chunk",ContentValues().apply {put("payload","corrupt")},"id=?",arrayOf(chunk.first))
            try {directory.undoLatestImport();fail("Corrupt journal accepted")} catch(_:Exception) { }
            assertEquals("Domestic",directory.records().single().category)
            db.update("import_chunk",ContentValues().apply {put("payload",chunk.second)},"id=?",arrayOf(chunk.first))
            directory.undoLatestImport();assertEquals("",directory.records().single().category)
            assertEquals("700123456",directory.records().single().originalPhone)
        }
    }

    @Test fun legacySchemaPayloadsAndOversizedJournalRemainReadable() {
        // Let the real implementation initialise its existing key if this is a fresh pilot.
        CustomerDirectory(context,name()).use { it.import(rows("Synthetic Seed,+12025550123,SEED,Synthetic Seed Scheme")) }
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        val key=store.getKey("swusip.directory.v1",null) as SecretKey
        fun seal(table:String,id:String,body:JSONObject):String {
            val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD("$table:$id".toByteArray())
            return Base64.encodeToString(c.iv+c.doFinal(body.toString().toByteArray()),Base64.NO_WRAP)
        }
        val fixture=name()
        val oldCustomer=seal("customer","legacy-customer",JSONObject().put("name","Synthetic Legacy").put("notes","Original legacy note").put("primaryNumber","+256700123456"))
        context.openOrCreateDatabase(fixture,0,null).use { db ->
            listOf("customer(id TEXT PRIMARY KEY,payload TEXT NOT NULL)","scheme(id TEXT PRIMARY KEY,payload TEXT NOT NULL)",
                "account(id TEXT PRIMARY KEY,scheme_id TEXT NOT NULL REFERENCES scheme(id),payload TEXT NOT NULL)","phone(id TEXT PRIMARY KEY,payload TEXT NOT NULL)",
                "customer_phone(customer_id TEXT REFERENCES customer(id),phone_id TEXT REFERENCES phone(id),PRIMARY KEY(customer_id,phone_id))",
                "customer_account(customer_id TEXT REFERENCES customer(id),account_id TEXT REFERENCES account(id),PRIMARY KEY(customer_id,account_id))",
                "account_phone(account_id TEXT REFERENCES account(id),phone_id TEXT REFERENCES phone(id),PRIMARY KEY(account_id,phone_id))",
                "import_batch(id TEXT PRIMARY KEY,payload TEXT NOT NULL)","note(id TEXT PRIMARY KEY,customer_id TEXT NOT NULL REFERENCES customer(id),payload TEXT NOT NULL)")
                .forEach {db.execSQL("CREATE TABLE $it")}
            db.execSQL("INSERT INTO customer VALUES(?,?)",arrayOf("legacy-customer",oldCustomer))
            db.execSQL("INSERT INTO scheme VALUES(?,?)",arrayOf("legacy-scheme",seal("scheme","legacy-scheme",JSONObject().put("code","LEGACY").put("name","Synthetic Legacy Scheme"))))
            db.execSQL("INSERT INTO account VALUES(?,?,?)",arrayOf("legacy-account","legacy-scheme",seal("account","legacy-account",JSONObject().put("number","0000123").put("schemeId","legacy-scheme").put("category","Domestic"))))
            db.execSQL("INSERT INTO phone VALUES(?,?)",arrayOf("legacy-phone",seal("phone","legacy-phone",JSONObject().put("number","+256700123456"))))
            db.execSQL("INSERT INTO customer_account VALUES('legacy-customer','legacy-account')")
            db.execSQL("INSERT INTO customer_phone VALUES('legacy-customer','legacy-phone')")
            db.execSQL("INSERT INTO account_phone VALUES('legacy-account','legacy-phone')")
            val journal=seal("import_batch","legacy-batch",JSONObject().put("changes",JSONArray()).put("syntheticPadding","A".repeat(2*1024*1024)))
            db.execSQL("INSERT INTO import_batch VALUES(?,?)",arrayOf("legacy-batch",journal))
            db.version=1
        }
        CustomerDirectory(context,fixture).use { directory ->
            val db=directory.readableDatabase
            assertEquals(2,db.version)
            db.rawQuery("SELECT payload FROM customer WHERE id='legacy-customer'",null).use {it.moveToFirst();assertEquals(oldCustomer,it.getString(0))}
            val record=directory.records().single()
            assertEquals("0000123",record.account);assertEquals("Original legacy note",record.notes)
            assertNull(record.originalPhone);assertEquals("LEGACY_UNVERIFIED",record.phoneStatus)
            directory.undoLatestImport() // Empty old batch, deliberately larger than a cursor window.
            assertEquals("Synthetic Legacy",directory.records().single().name)
            directory.import(rows("Synthetic New,700123457,0000124,Synthetic Legacy Scheme"))
            assertEquals(setOf("LEGACY"),directory.records().map {it.schemeCode}.toSet())
            directory.undoLatestImport();assertEquals("0000123",directory.records().single().account)
        }
    }
}
