package com.tutpro.baresip

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.provider.Settings
import android.os.SystemClock
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Managed application restrictions are set by the institution's Android management
 * authority, not an in-app PIN, hidden gesture or an agent-controlled preference. */
object InstitutionAccess {
    @Volatile private var authenticatedUntil = 0L
    internal fun credentialConfirmed() { authenticatedUntil = SystemClock.elapsedRealtime() + 300_000L }
    private fun session(): Boolean = SystemClock.elapsedRealtime() < authenticatedUntil
    fun unlocked(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return keyguard.isDeviceSecure && !keyguard.isKeyguardLocked
    }
    private fun role(context: Context): String {
        val managed = context.getSystemService(RestrictionsManager::class.java)
            .applicationRestrictions.getString("swusip_role")
        return managed ?: OfflineAuthorisation.role(context)
    }
    fun administrator(context: Context): Boolean = unlocked(context) && session() && role(context) == "administrator"
    fun agent(context: Context): Boolean = unlocked(context) && session() && managedAgent(context)
    fun managedAgent(context: Context): Boolean = role(context) in setOf("agent", "administrator")
}

@Composable
fun WorkspaceUnlock() {
    val ctx = LocalContext.current
    var enrolled by remember { mutableStateOf(InstitutionAccess.managedAgent(ctx)) }
    LaunchedEffect(ctx) { while(true) { enrolled = InstitutionAccess.managedAgent(ctx); delay(250) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(it.resultCode == Activity.RESULT_OK) InstitutionAccess.credentialConfirmed()
    }
    if(enrolled) Button(onClick = {
        ctx.getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent("Unlock SWUSIP", "Authenticate to access the institutional workspace")
            ?.let { launcher.launch(it) }
    }) { Text("Unlock institutional workspace") }
}

@Composable
fun administratorAccess(): Boolean {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(InstitutionAccess.administrator(ctx)) }
    LaunchedEffect(ctx) { while(true) { allowed = InstitutionAccess.administrator(ctx); delay(250) } }
    return allowed
}

@Composable
fun DeviceEnrolment() {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(!InstitutionAccess.managedAgent(ctx)) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            result = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buffer = CharArray(16385); val length = reader.read(buffer)
                        require(length in 1..16384); String(buffer,0,length)
                    } ?: error("No authorisation file")
                    OfflineAuthorisation.install(ctx,text)
                }
                "Phone authorised. Unlock the institutional workspace to continue."
            } catch (_: Exception) { "Authorisation rejected. Use an unexpired file issued for this phone and keep a secure device lock enabled." }
        }
    }
    TextButton(onClick = { expanded = !expanded }) { Text("Phone authorisation") }
    if(expanded) {
        Text("Device enrolment", style = MaterialTheme.typography.titleMedium)
        Text("Give this device code to the SWUSIP administrator:")
        androidx.compose.foundation.text.selection.SelectionContainer { Text(OfflineAuthorisation.challenge(ctx)) }
        TextButton(onClick = { picker.launch(arrayOf("application/json", "text/plain")) }) { Text("Load phone authorisation file") }
        Text(result)
    }
}

fun NavGraphBuilder.agentSettingsRoute(nav: NavController) {
    composable("agent-settings") { AgentSettingsScreen(nav) }
}

@Composable
fun AgentSettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SWUSIP · Agent Settings", style = MaterialTheme.typography.headlineSmall)
        WorkspaceUnlock()
        DeviceEnrolment()
        AgentWorkspaceStatus()
        Text("South Western Umbrella of Water and Sanitation")
        Text("Assigned extension: " + (BaresipService.uas.value.firstOrNull()?.account?.aor
            ?.substringAfter("sip:")?.substringBefore("@") ?: "Not provisioned"))
        Text("Connection status is shown on the dialler. Contact your administrator if registration fails.")
        Button(onClick = { nav.navigate("audio") }) { Text("Microphone and audio") }
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }) { Text("Notification preferences") }
        Text("Background calls: allow SWUSIP notifications and microphone access. In Android battery settings, allow background activity for reliable incoming calls. A default phone or SMS role is not required.")
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Secure device lock") }
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${ctx.packageName}"))) }) { Text("Android app and battery settings") }
        Text("Help: enter the destination number and tap Call. During a call use Mute, Speaker, Hold, Transfer, Keypad or Hang up. Keep SWUSIP running for incoming calls.")
        Button(onClick = { nav.navigate("about") }) { Text("About SWUSIP") }
        if (administratorAccess()) {
            Button(onClick = { nav.navigate("settings") }) { Text("Administrator settings") }
            Button(onClick = { nav.navigate("accounts") }) { Text("Extension provisioning") }
        } else Text("Administrator settings require a signed administrator authorisation and workspace unlock. Your local PBX extensions are unchanged.")
        TextButton(onClick = { nav.navigateUp() }) { Text("Back to call centre") }
    }
}
