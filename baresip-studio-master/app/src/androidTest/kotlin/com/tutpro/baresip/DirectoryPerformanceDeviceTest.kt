package com.tutpro.baresip

import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteBlobTooBigException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** Opt-in, bounded profiling. Never opens the production directory or changes its key.
 * All operations retain real owner authentication. No 10,000-row run is performed. */
@RunWith(AndroidJUnit4::class)
class DirectoryPerformanceDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val stages = JSONArray()
    private var elapsedBudgetStart = 0L
    private fun <T> timed(label: String, operations: Int, action: () -> T): T {
        check(InstitutionAccess.administrator(context)) { "Normal administrator authentication required" }
        check(!Call.inCall()) { "Do not profile during a call" }
        check(SystemClock.elapsedRealtime() - elapsedBudgetStart < 90_000) { "Bounded profile budget reached; stop before another stage" }
        instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nPROFILE start: $label ($operations operations)\n") })
        val sent = Debug.getBinderSentTransactions()
        val cpu = Debug.threadCpuTimeNanos()
        val start = SystemClock.elapsedRealtimeNanos()
        var status = "completed"
        try { return action() }
        catch (error: Throwable) { status = error.javaClass.simpleName; throw error }
        finally {
            val wallMs = (SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
            stages.put(JSONObject().put("stage",label).put("operations",operations).put("wallMs",wallMs)
                .put("threadCpuMs",(Debug.threadCpuTimeNanos()-cpu)/1_000_000.0)
                .put("processBinderSentDelta",Debug.getBinderSentTransactions()-sent).put("status",status))
            instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nPROFILE finish: $label, ${wallMs.toLong()} ms, $status\n") })
        }
    }

    @Test fun boundedSyntheticCostProfile() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit bounded profile opt-in required",args.getString("swusipProfile") == "true")
        assumeTrue("Do not interrupt a call",!Call.inCall())
        val rowsLimit = args.getString("swusipProfileRows")?.toIntOrNull() ?: 20
        require(rowsLimit in 5..50) { "Profile only 5–50 synthetic rows, never 10,000" }
        val session = AuthenticatedDirectoryDeviceTest()
        val alias = "swusip.synthetic.profile.${UUID.randomUUID()}"
        var keyCreated = false
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val report = JSONObject().put("kind","bounded_synthetic_profile").put("startedUtcEpochMs",System.currentTimeMillis())
            .put("rowsLimit",rowsLimit).put("fullBulkRun",false).put("stages",stages)
            .put("limits","Small real-device samples; no measured 10,000-record stage times. Binder counts include background process traffic.")
        try {
            session.authenticateAdministrator()
            elapsedBudgetStart = SystemClock.elapsedRealtime()
            val probeBytes = 2*1024*1024+64
            val rejected = timed("sqlite_cursor_single_row_size_probe",1) {
                SQLiteDatabase.create(null).use { db ->
                    db.execSQL("CREATE TABLE synthetic_probe(id TEXT,payload TEXT)")
                    db.insertOrThrow("synthetic_probe",null,ContentValues().apply {
                        put("id","synthetic"); put("payload","A".repeat(probeBytes))
                    })
                    try { db.rawQuery("SELECT id,payload FROM synthetic_probe LIMIT 1",null).use { it.moveToFirst(); it.getString(1) }; false }
                    catch (_: SQLiteBlobTooBigException) { true }
                }
            }
            report.put("cursorProbe",JSONObject().put("syntheticAsciiBytes",probeBytes).put("defaultCursorRejectedRow",rejected))
            timed("assigned_identity_lookup",20) { repeat(20) { OfflineAuthorisation.identity(context) } }
            val key = timed("ephemeral_key_generation",1) {
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true).setUserAuthenticationValidityDurationSeconds(300)
                        .setUnlockedDeviceRequired(true).build())
                }.generateKey().also { keyCreated = true }
            }
            val info = SecretKeyFactory.getInstance("AES","AndroidKeyStore").getKeySpec(key,KeyInfo::class.java) as KeyInfo
            assertTrue(info.isUserAuthenticationRequired)
            assertEquals(300,info.userAuthenticationValidityDurationSeconds)
            report.put("profileKeyPolicy",JSONObject().put("authenticationRequired",true).put("validitySeconds",300)
                .put("unlockedDeviceRequiredConfigured",true).put("securityLevel",info.securityLevel))
            val payload = ByteArray(512) { 65 }
            // Complete each operation before opening the next Keystore operation.
            // Parallel cipher initialisation could exhaust/prune hardware slots.
            repeat(8) { round ->
                val enc = timed("cipher_factory_$round",1) { Cipher.getInstance("AES/GCM/NoPadding") }
                timed("small_encrypt_init_$round",1) { enc.init(Cipher.ENCRYPT_MODE,key); enc.updateAAD("synthetic-profile".toByteArray()) }
                val sealed = timed("small_encrypt_doFinal_$round",1) { enc.doFinal(payload) }
                val dec = Cipher.getInstance("AES/GCM/NoPadding")
                timed("small_decrypt_init_$round",1) { dec.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,enc.iv)); dec.updateAAD("synthetic-profile".toByteArray()) }
                timed("small_decrypt_doFinal_$round",1) { assertArrayEquals(payload,dec.doFinal(sealed)) }
            }
            for(size in listOf(16*1024,64*1024,256*1024)) {
                val plain = ByteArray(size) { 65 }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                timed("journal_encrypt_init_$size",1) { cipher.init(Cipher.ENCRYPT_MODE,key); cipher.updateAAD("synthetic-journal".toByteArray()) }
                val data = timed("journal_encrypt_doFinal_$size",1) { cipher.doFinal(plain) }
                val reader = Cipher.getInstance("AES/GCM/NoPadding")
                timed("journal_decrypt_init_$size",1) { reader.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,cipher.iv)); reader.updateAAD("synthetic-journal".toByteArray()) }
                timed("journal_decrypt_doFinal_$size",1) { assertArrayEquals(plain,reader.doFinal(data)) }
            }
            for(n in listOf(5,rowsLimit).distinct()) {
                val name = "swusip-synthetic-profile-${UUID.randomUUID()}.db"
                try {
                    CustomerDirectory(context,name).use { directory ->
                        val input = (CustomerImport.headers.joinToString(",")+"\n"+(1..n).joinToString("\n") {
                            "Synthetic Profile $it,+12025550123,${it.toString().padStart(8,'0')},PERF,Synthetic Profile Scheme,Domestic,"
                        }).byteInputStream()
                        val rows = timed("csv_parse_$n",n) { CustomerImport.parse(input,false) }
                        assertEquals(n,timed("repository_import_$n",n) { directory.import(rows) }.accepted)
                        val loaded = timed("repository_read_$n",n) { directory.records() }
                        assertEquals(n,loaded.size)
                        assertEquals(1,timed("loaded_account_search_$n",n) { CustomerMatching.search(loaded,n.toString().padStart(8,'0')) }.size)
                        val sql = """SELECT c.customer_id,c.account_id,p.phone_id,a.scheme_id
                            FROM customer_account c JOIN account a ON a.id=c.account_id
                            LEFT JOIN customer_phone p ON p.customer_id=c.customer_id AND EXISTS
                            (SELECT 1 FROM account_phone ap WHERE ap.account_id=c.account_id AND ap.phone_id=p.phone_id)"""
                        timed("sqlite_join_only_$n",n) { directory.readableDatabase.rawQuery(sql,null).use { assertEquals(n,it.count) } }
                        timed("repository_undo_$n",n) { directory.undoLatestImport() }
                        assertTrue(directory.records().isEmpty())
                    }
                } finally { context.deleteDatabase(name) }
            }
            report.put("status","completed")
        } catch(error: Throwable) {
            report.put("status",if(error is org.junit.AssumptionViolatedException) "blocked_owner_authentication" else "failed")
                .put("failureClass",error.javaClass.simpleName)
            throw error
        } finally {
            if(keyCreated) { store.deleteEntry(alias); report.put("ephemeralKeyDeleted",!store.containsAlias(alias)) }
            session.restoreDisplayFlag()
            val folder = File(context.getExternalFilesDir(null),"directory-profile").apply { mkdirs() }
            File(folder,"bounded-profile.json").writeText(report.toString(2))
        }
    }
}
