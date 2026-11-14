package com.tutpro.baresip

import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Offline device-bound role grants. The issuer's private key never enters the
 * APK. Debug builds trust only the local pilot issuer; release builds require a
 * separately supplied institutional public key. Grants do not configure the PBX. */
object OfflineAuthorisation {
    @Volatile private var cached: Pair<String,List<String>>? = null
    fun challenge(ctx: Context): String {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        return MessageDigest.getInstance("SHA-256").digest((ctx.packageName + ":" + id).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    private fun verify(ctx: Context, text: String): List<String> {
        require(text.length <= 16384)
        cached?.takeIf { it.first == text }?.let {
            require(it.second[3].toLong() > System.currentTimeMillis())
            return it.second
        }
        val grant = JSONObject(text)
        val payload = Base64.decode(grant.getString("payload"),Base64.NO_WRAP)
        val signature = Base64.decode(grant.getString("signature"),Base64.NO_WRAP)
        val asset = if(BuildConfig.DEBUG) "pilot-authority.der" else "institution-authority.der"
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(ctx.assets.open(asset).use { it.readBytes() }))
        require(Signature.getInstance("SHA256withRSA").run { initVerify(publicKey); update(payload); verify(signature) })
        val fields = String(payload,Charsets.UTF_8).split('\n')
        require(fields.size == 5 && fields[0] == "SWUSIP-GRANT-V1" && fields[1] == challenge(ctx))
        require(fields[2] in setOf("agent","administrator","revoked"))
        require(fields[3].toLong() > System.currentTimeMillis())
        require(fields[4].matches(Regex("[A-Za-z0-9_.-]{1,64}")))
        cached = text to fields
        return fields
    }
    fun install(ctx: Context, text: String) {
        val fields = verify(ctx,text)
        check(InstitutionAccess.unlocked(ctx)) { "Unlock a secure device before enrolling" }
        val prefs = ctx.getSharedPreferences("institution-authorisation",Context.MODE_PRIVATE)
        require(fields[3].toLong() >= prefs.getLong("newestGrant",0)) { "Older authorisation cannot replace a newer grant" }
        prefs.edit().putString("grant",text).putLong("newestGrant",fields[3].toLong())
            .putLong("lastObservedTime",System.currentTimeMillis()).apply()
    }
    fun role(ctx: Context): String = try {
        val prefs = ctx.getSharedPreferences("institution-authorisation",Context.MODE_PRIVATE)
        val now = System.currentTimeMillis(); val previous = prefs.getLong("lastObservedTime",0)
        require(now >= previous - 300_000) { "Device clock moved backwards" }
        if(now > previous + 60_000) prefs.edit().putLong("lastObservedTime",now).apply()
        val grant = prefs.getString("grant",null)
        if(grant == null) "unassigned" else verify(ctx,grant)[2]
    } catch (_: Exception) { "unassigned" }
    fun identity(ctx: Context): String = try {
        val managed = ctx.getSystemService(android.content.RestrictionsManager::class.java)
            .applicationRestrictions.getString("swusip_agent_id")
        val grant = ctx.getSharedPreferences("institution-authorisation",Context.MODE_PRIVATE).getString("grant",null)
        when {
            managed != null && managed.matches(Regex("[A-Za-z0-9_.-]{1,64}")) -> managed
            grant == null -> "institution-agent"
            else -> verify(ctx,grant)[4]
        }
    } catch (_: Exception) { "institution-agent" }
    /** Historical attribution is an assigned device identity, not proof that an agent answered. */
    fun assignedIdentity(ctx: Context): String? = if(role(ctx) in setOf("agent", "administrator")) {
        val managedId = ctx.getSystemService(android.content.RestrictionsManager::class.java)
            .applicationRestrictions.getString("swusip_agent_id")
        (managedId ?: identity(ctx)).takeIf { it != "institution-agent" && it.matches(Regex("[A-Za-z0-9_.-]{1,64}")) }
    } else null
}
