package com.tutpro.baresip

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/** Non-secret institution policy, never SIP passwords or PBX configuration. */
object DirectoryTelephoneSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("directory-telephone-policy",Context.MODE_PRIVATE)
    fun normalisation(context: Context): PhoneNormalisationPolicy {
        return runCatching {
            PhoneNormalisationPolicy(prefs(context).getStringSet("known_access_prefixes",emptySet()).orEmpty().toSet())
        }.getOrDefault(PhoneNormalisationPolicy())
    }
    private fun proof(style: OutboundNumberStyle,prefix:String) = "${UgandaNumberingPlan().version}|${style.name}|$prefix"
    fun outgoing(context: Context): OutboundDiallingProfile = runCatching { readOutgoing(context) }.getOrDefault(OutboundDiallingProfile())
    private fun readOutgoing(context: Context): OutboundDiallingProfile {
        val p=prefs(context)
        val style=OutboundNumberStyle.entries.find { it.name==p.getString("style","") } ?: OutboundNumberStyle.UGANDA_NATIONAL
        val prefix=p.getString("outgoing_prefix","").orEmpty()
        val reference=p.getString("verification_reference","").orEmpty()
        val verified=p.getBoolean("verified",false) && p.getString("proof","")==proof(style,prefix) && reference.isNotBlank()
        return OutboundDiallingProfile(style,prefix,verified,reference)
    }
    fun save(context: Context,policy: PhoneNormalisationPolicy,profile: OutboundDiallingProfile) {
        check(InstitutionAccess.administrator(context)) { "Administrator authorisation required" }
        if(profile.verified) {
            require(profile.verificationReference.isNotBlank()) { "Record the approved PBX test reference before enabling a profile" }
            require(profile.style != OutboundNumberStyle.PBX_ACCESS_NATIONAL || profile.accessPrefix.matches(Regex("[1-9]"))) { "Use one approved access digit" }
        }
        require(profile.verificationReference.length <= 200)
        val edit=prefs(context).edit().putStringSet("known_access_prefixes",policy.accessPrefixes)
            .putString("style",profile.style.name).putString("outgoing_prefix",profile.accessPrefix)
            .putBoolean("verified",profile.verified).putString("verification_reference",profile.verificationReference)
            .putString("proof",if(profile.verified) proof(profile.style,profile.accessPrefix) else "")
        check(InstitutionAccess.administrator(context))
        check(edit.commit()) { "Policy could not be saved" }
    }
    fun callerNumber(context: Context,identity:String): String? = PhoneNormaliser.normalise(identity,normalisation(context)).takeIf { it.accepted }?.canonical
}

@Composable
fun DirectoryTelephoneSettingsScreen(nav: NavController) {
    InstitutionScreenPrivacy()
    val ctx=LocalContext.current
    val administrator=administratorAccess()
    val current=remember { DirectoryTelephoneSettings.outgoing(ctx) }
    var prefixes by remember { mutableStateOf(DirectoryTelephoneSettings.normalisation(ctx).accessPrefixes.sorted().joinToString(",")) }
    var style by remember { mutableStateOf(current.style) }
    var prefix by remember { mutableStateOf(current.accessPrefix) }
    var reference by remember { mutableStateOf(current.verificationReference) }
    var verified by remember { mutableStateOf(current.verified) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("SWUSIP · Telephone policy",style=MaterialTheme.typography.headlineSmall)
        TextButton(onClick={ nav.navigateUp() }) { Text("Back to Customers") }
        if(!administrator) { Text("An authenticated administrator role is required."); WorkspaceUnlock(); return@Column }
        Text("Uganda numbering plan: ${UgandaNumberingPlan().version}. Unrecognised prefixes require review.")
        OutlinedTextField(prefixes,{prefixes=it},label={Text("Known incoming/import PBX access prefixes (optional, comma separated)")},modifier=Modifier.fillMaxWidth())
        Text("Leave blank unless these prefixes are known to be added by your office PBX. No prefix is guessed.")
        Text("Customer call-back formatting",style=MaterialTheme.typography.titleMedium)
        Text("The existing manual dialler is unchanged. New profiles are disabled until an administrator records an approved genuine Grandstream test.")
        Box {
            TextButton(onClick={menu=true}) { Text(when(style) {
                OutboundNumberStyle.UGANDA_NATIONAL -> "Uganda national: 07XXXXXXXX / 03…"
                OutboundNumberStyle.UGANDA_COUNTRY_CODE -> "Country code: 2567XXXXXXXX"
                OutboundNumberStyle.E164_PLUS -> "International: +2567XXXXXXXX"
                OutboundNumberStyle.PBX_ACCESS_NATIONAL -> "Approved access digit + national number"
            }) }
            DropdownMenu(menu,{menu=false}) {
                OutboundNumberStyle.entries.forEach { value -> DropdownMenuItem(text={Text(value.name.replace('_',' '))},onClick={style=value;verified=false;menu=false}) }
            }
        }
        if(style==OutboundNumberStyle.PBX_ACCESS_NATIONAL) OutlinedTextField(prefix,{prefix=it;verified=false},label={Text("Approved outbound access digit")})
        OutlinedTextField(reference,{if(it.length<=200) reference=it;verified=false},label={Text("Approved PBX test date/reference (no passwords)")},modifier=Modifier.fillMaxWidth())
        Row {
            Checkbox(verified,{verified=it},enabled=reference.isNotBlank())
            Text("I verified this exact profile with an approved genuine PBX test")
        }
        Text("Saving an unverified profile leaves customer call-back disabled. This screen does not test or change the PBX.")
        Button(onClick={
            status=try {
                val access=prefixes.split(',').map {it.trim()}.filter {it.isNotEmpty()}.toSet()
                DirectoryTelephoneSettings.save(ctx,PhoneNormalisationPolicy(access),OutboundDiallingProfile(style,prefix,verified,reference))
                "Policy saved. Outgoing profile ${if(verified) "enabled by administrator confirmation" else "disabled pending verification"}."
            } catch(_:Exception) { "Policy not saved. Check your administrator session, prefix values and test reference." }
        }) { Text("Save authorised policy") }
        Text(status)
    }
}
