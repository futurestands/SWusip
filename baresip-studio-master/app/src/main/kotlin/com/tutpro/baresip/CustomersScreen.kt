package com.tutpro.baresip

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navArgument
import android.net.Uri
import androidx.navigation.compose.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
private fun directoryAccess(context: Context): Boolean {
    var allowed by remember { mutableStateOf(InstitutionAccess.agent(context)) }
    LaunchedEffect(context) {
        while(true) { allowed = InstitutionAccess.agent(context); delay(250) }
    }
    return allowed
}

fun NavGraphBuilder.customersRoute(nav: NavController, viewModel: ViewModel) {
    composable("customers?phone={phone}&scheme={scheme}&customer={customer}&account={account}", arguments = listOf(
        navArgument("phone") { defaultValue = "" }, navArgument("scheme") { defaultValue = "" },
        navArgument("customer") { defaultValue = "" }, navArgument("account") { defaultValue = "" })) {
        CustomersScreen(nav, viewModel, it.arguments?.getString("phone") ?: "", it.arguments?.getString("scheme") ?: "",
            it.arguments?.getString("customer") ?: "",it.arguments?.getString("account") ?: "")
    }
    composable("schemes") { SchemeDirectoryScreen(nav) }
    composable("directory-telephone-policy") { DirectoryTelephoneSettingsScreen(nav) }
}

@Composable
fun CustomersScreen(nav: NavController, viewModel: ViewModel, initialQuery: String = "", initialScheme: String = "",initialCustomer: String="",initialAccount: String="") {
    InstitutionScreenPrivacy()
    val ctx = LocalContext.current
    val authorised = directoryAccess(ctx)
    val administrator = administratorAccess()
    var query by remember { mutableStateOf(initialQuery) }
    var scheme by remember { mutableStateOf(initialScheme) }
    var records by remember { mutableStateOf<List<CustomerRecord>>(emptyList()) }
    var pendingFollowUps by remember { mutableStateOf<Map<String,Int>>(emptyMap()) }
    var followUpsOnly by remember { mutableStateOf(false) }
    var followUpsReady by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Loading authorised local directory…") }
    var selected by remember { mutableStateOf<CustomerRecord?>(null) }
    var preview by remember { mutableStateOf<List<CustomerImportRow>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var passwordAction by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var reviews by remember { mutableStateOf<List<ImportReviewRecord>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(administrator) {
        if(!administrator) {
            manage = false; preview = null; confirmUndo = false; passwordAction = ""
            passphrase = ""; exportBytes = null; restoreUri = null; reviews=null
        }
    }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val data = exportBytes; exportBytes = null
        if(uri != null && data != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(InstitutionAccess.administrator(ctx))
                    requireNotNull(ctx.contentResolver.openOutputStream(uri)).use { it.write(data) }
                }
                message = "Encrypted customer spreadsheet saved. Keep its passphrase separately. Interaction notes remain on this device."
            } catch (_: Exception) { message = "Export could not be saved." }
        }
    }
    val restoreExport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) { restoreUri = uri; passwordAction = "restore" }
    }
    val excelTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if(uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctx.assets.open("swusip-customers.xlsx").use { input ->
                        requireNotNull(ctx.contentResolver.openOutputStream(uri)).use { input.copyTo(it) }
                    }
                }
                message = "Excel template saved. Telephone and account columns use Text format."
            } catch (_: Exception) { message = "Could not save Excel template." }
        }
    }
    val template = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri != null) scope.launch {
            try { withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(CustomerImport.requiredHeaders.joinToString(",") + "\r\n") }
            }; message = "Template saved. In Excel format account_number as Text before entering values."
            } catch (_: Exception) { message = "Could not save template." }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null && InstitutionAccess.administrator(ctx)) scope.launch {
            busy = true
            try {
                preview = withContext(Dispatchers.IO) {
                    val mime = ctx.contentResolver.getType(uri)
                    ctx.contentResolver.openInputStream(uri)?.let {
                        val buffered = it.buffered(); buffered.mark(4)
                        val zip = buffered.read() == 0x50 && buffered.read() == 0x4b; buffered.reset()
                        val parsed = CustomerImport.parse(buffered, zip,DirectoryTelephoneSettings.normalisation(ctx))
                        CustomerDirectory(ctx).use { directory -> directory.preview(parsed) }
                    } ?: error("Unable to open document")
                }
                message = "Preview ready. No data has been changed."
            } catch (e: IllegalArgumentException) { message = e.message ?: "Invalid import document" }
            catch (_: Exception) { message = "Import could not be read. Check the template and file format." }
            finally { busy = false }
        }
    }
    LaunchedEffect(authorised) {
        if(!authorised) {
            records = emptyList(); pendingFollowUps = emptyMap(); followUpsReady = false; selected = null; preview = null
            passphrase = ""; passwordAction = ""; exportBytes = null; restoreUri = null
            return@LaunchedEffect
        }
        try {
            records = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.records() } }
            pendingFollowUps = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.pendingFollowUps() } }
            followUpsReady = true
            message = if(records.isEmpty()) "No customer records imported on this device." else "${records.size} customer account / phone entries"
            if(initialCustomer.isNotEmpty()) selected=records.firstOrNull { it.id==initialCustomer && (initialAccount.isEmpty() || it.account==initialAccount) && (initialQuery.isEmpty() || it.phone==initialQuery) }
            else if(initialQuery.isNotEmpty()) {
                val found = records.filter { it.phone == initialQuery }.distinctBy { it.id }
                if(found.size == 1) selected = found.first()
            }
        } catch (_: Exception) { message = "Directory unavailable. Unlock a secure device or contact your administrator." }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SWUSIP · Customers", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { nav.navigateUp() }) { Text("Back to call centre") }
        if (!authorised) {
            Text("An institutional agent role and an unlocked secure device are required to view customer information.")
            WorkspaceUnlock()
            DeviceEnrolment()
            return@Column
        }
        OutlinedTextField(query, { query = it }, label = { Text("Name, telephone, account or scheme") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(scheme, { scheme = it }, label = { Text("Filter by scheme code or name") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { nav.navigate("schemes") }) { Text("Browse water schemes") }
        FilterChip(selected = followUpsOnly && followUpsReady,enabled = followUpsReady,onClick = { followUpsOnly = !followUpsOnly },
            label = { Text(if(followUpsReady) "Local pending follow-ups (${pendingFollowUps.values.sum()})" else "Follow-up status unavailable until directory loads") })
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (administrator) {
            TextButton(onClick = { manage = !manage }) { Text(if(manage) "Close directory management" else "Manage directory") }
            if(manage) Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            Text("Required: customer_name, phone_number, account_number, scheme_name. Legacy seven-column files remain supported.")
            TextButton(onClick={ nav.navigate("directory-telephone-policy") }) { Text("Telephone and outgoing dialling policy") }
            TextButton(onClick={scope.launch {
                try { reviews=withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.reviewRows() } } }
                catch(_:Exception) { message="Review records unavailable. Check administrator authorisation." }
            }}) { Text("Review ambiguous or rejected rows") }
            Row {
                TextButton(enabled = !busy, onClick = { template.launch("swusip-customers.csv") }) { Text("Save template") }
                TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Text("Import CSV / XLSX") }
            }
            TextButton(onClick = { excelTemplate.launch("swusip-customers.xlsx") }) { Text("Save Excel template") }
            Row {
                TextButton(enabled = !busy,onClick = { passwordAction = "export" }) { Text("Encrypted export") }
                TextButton(enabled = !busy,onClick = { restoreExport.launch(arrayOf("application/octet-stream")) }) { Text("Restore export") }
            }
            TextButton(enabled = !busy, onClick = { confirmUndo = true }) { Text("Undo latest import") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CustomerMatching.search(records,query,scheme).filter { !followUpsReady || !followUpsOnly || pendingFollowUps.getOrDefault(it.id,0) > 0 }) { record ->
                OutlinedCard(onClick = { selected = record }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(record.name, style = MaterialTheme.typography.titleMedium)
                        Text(record.phone)
                        Text("Account ${record.account} · ${record.schemeName} (${record.schemeCode})")
                        if(followUpsReady && pendingFollowUps.getOrDefault(record.id,0) > 0) Text("Follow-up pending (${pendingFollowUps[record.id]})")
                    }
                }
            }
        }
    }
    if(authorised && confirmUndo && administratorAccess()) AlertDialog(
        onDismissRequest = { if(!busy) confirmUndo = false },
        title = { Text("Undo latest import?") },
        text = { Text("Reverse only the most recent import batch. Earlier records remain. If later edits or notes depend on this batch, reversal will be refused to protect them.") },
        dismissButton = { TextButton(enabled = !busy,onClick = { confirmUndo = false }) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.undoLatestImport() } }
                        records = withContext(Dispatchers.IO) { runCatching { CustomerDirectory(ctx).use { it.records() } }.getOrDefault(emptyList()) }
                        message = "Latest import undone. Earlier customer data preserved."
                    } catch (_: Exception) { message = "No batch could be safely undone." }
                    finally { busy = false; confirmUndo = false }
                }
            }) { Text("Confirm undo") } })
    if(authorised && passwordAction.isNotEmpty()) AlertDialog(
        onDismissRequest = { if(!busy) { passwordAction = ""; passphrase = "" } },
        title = { Text(if(passwordAction == "export") "Encrypt customer spreadsheet" else "Open encrypted spreadsheet") },
        text = { Column {
            Text("Use at least 16 characters. Keep this passphrase separate from the file. Export includes customer spreadsheet fields; interaction notes stay on this device.")
            OutlinedTextField(passphrase,{ passphrase = it },label = { Text("Export passphrase") },visualTransformation = PasswordVisualTransformation())
        } },
        confirmButton = { TextButton(enabled = !busy && passphrase.length >= 16,onClick = { scope.launch {
            busy = true; val secret = passphrase.toCharArray(); passphrase = ""
            try {
                if(passwordAction == "export") {
                    exportBytes = withContext(Dispatchers.IO) {
                        check(InstitutionAccess.administrator(ctx))
                        val csv = CustomerDirectory(ctx).use { CustomerExport.spreadsheet(it.records()) }
                        CustomerExport.seal(csv,secret)
                    }
                    passwordAction = ""; saveExport.launch("swusip-customers.swusip-directory")
                } else {
                    preview = withContext(Dispatchers.IO) {
                        check(InstitutionAccess.administrator(ctx))
                        val input = requireNotNull(ctx.contentResolver.openInputStream(requireNotNull(restoreUri)))
                        val bytes = input.use {
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while(true) { val n = it.read(buffer); if(n < 0) break; out.write(buffer,0,n); require(out.size() <= CustomerImport.MAX_BYTES+128) }
                            out.toByteArray()
                        }
                        val rows = CustomerImport.parse(CustomerExport.open(bytes,secret).byteInputStream(),false,DirectoryTelephoneSettings.normalisation(ctx))
                        CustomerDirectory(ctx).use { it.preview(rows) }
                    }
                    passwordAction = ""; message = "Restored spreadsheet ready for preview. Existing data has not changed."
                }
            } catch (_: Exception) { message = "Could not process encrypted spreadsheet. Check passphrase, authorisation and file size."; passwordAction = "" }
            finally { secret.fill('\u0000'); busy = false; restoreUri = null }
        } }) { Text("Continue") } },
        dismissButton = { TextButton(enabled = !busy,onClick = { passwordAction = ""; passphrase = "" }) { Text("Cancel") } })
    if(authorised) preview?.let { rows ->
        AlertDialog(onDismissRequest = { if(!busy) preview = null },
            title = { Text("Review ${rows.size} rows before import") },
            text = { LazyColumn {
                item { Text("Accepted for processing: ${rows.count { it.error == null }} · Proposed corrections: ${rows.count { it.error==null && it.phone?.status==PhoneValidationStatus.CORRECTED }} · Needs review: ${rows.count { it.error!=null }}. Confirming stores review rows separately; they cannot identify callers. Existing data is never deleted.") }
                items(rows) { row -> Column(Modifier.padding(vertical=6.dp)) {
                    Text("Row ${row.row} · ${row.action}: "+(row.error ?: "${row.values[0]} · Account ${row.values[2]} · ${row.scheme?.name ?: row.values[4]}"))
                    row.phone?.let { phone ->
                        Text("Imported: ${phone.original}")
                        Text("Canonical: ${phone.canonical ?: "Not assigned"} · ${phone.status.name}")
                        Text(if(phone.corrections.isEmpty()) phone.reason else phone.correctionReason)
                    }
                    if(row.scheme?.generated==true) Text("A stable internal scheme identifier will be created; no scheme code is required.")
                } }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                try {
                    val result = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.import(rows) } }
                    records = withContext(Dispatchers.IO) { runCatching { CustomerDirectory(ctx).use { it.records() } }.getOrDefault(emptyList()) }
                    message = "Accepted ${result.validRows} (new ${result.accepted}, updated ${result.updated}, duplicate ${result.skipped}) · Corrected ${result.corrected} · Ambiguous ${result.ambiguous} · Other rejected ${result.rejected-result.ambiguous}" +
                        if(result.errors.isEmpty()) "" else "\n" + result.errors.joinToString("\n")
                    preview = null
                } catch (_: Exception) { message = "Import failed; changes were rolled back." }
                finally { busy = false }
            } }) { Text("Confirm import and review list") } },
            dismissButton = { TextButton(enabled = !busy,onClick = { preview = null }) { Text("Cancel") } })
    }
    if(administrator) reviews?.let { rows -> AlertDialog(onDismissRequest={reviews=null},title={Text("Administrator import review")},text={
        LazyColumn(Modifier.heightIn(max=460.dp)) {
            if(rows.isEmpty()) item { Text("No unresolved import rows stored on this phone.") }
            items(rows) { row -> Column(Modifier.padding(vertical=8.dp)) {
                Text("Row ${row.sourceRow} · ${row.status}");Text(row.reason)
                Text(row.values.take(5).joinToString(" · "))
                Text("Correct the authoritative spreadsheet and preview it again. This row is excluded from incoming customer identification.")
            } }
        }
    },confirmButton={TextButton(onClick={reviews=null}) {Text("Close review")}}) }
    if(authorised) selected?.let { person ->
        CustomerProfileDialog(person, records.filter { it.id == person.id }, records, onClose = { selected = null }, onCallback = {
            if(InstitutionAccess.agent(ctx) && !Call.inCall()) {
                when(val destination=OutboundDiallingFormatter.format(person.phone,DirectoryTelephoneSettings.outgoing(ctx))) {
                    is OutboundDestination.Ready -> { viewModel.dialerState.callUri.value=destination.number;nav.navigate("main") { popUpTo("main") { inclusive=true } } }
                    is OutboundDestination.Blocked -> { message=destination.reason;selected=null }
                }
            }
        }, onInteractionsChanged = {
            scope.launch {
                try {
                    pendingFollowUps = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.pendingFollowUps() } }
                    followUpsReady = true
                } catch (_: Exception) { followUpsReady = false; message = "Follow-up status unavailable. Unlock the workspace and reopen Customers." }
            }
        }, onUpdated = {
            scope.launch { records = withContext(Dispatchers.IO) { runCatching { CustomerDirectory(ctx).use { it.records() } }.getOrDefault(emptyList()) }; selected = null }
        })
    }
}

@Composable
private fun CustomerProfileDialog(person: CustomerRecord, entries: List<CustomerRecord>, allRecords: List<CustomerRecord>,
    onClose: () -> Unit, onCallback: () -> Unit, onInteractionsChanged: () -> Unit, onUpdated: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var notes by remember(person.id) { mutableStateOf<List<CustomerInteraction>>(emptyList()) }
    var draft by remember(person.id) { mutableStateOf("") }
    var name by remember(person.id) { mutableStateOf(person.name) }
    var location by remember(person.id) { mutableStateOf(person.location) }
    var status by remember { mutableStateOf("") }
    var linkScheme by remember { mutableStateOf("") }
    var linkAccount by remember { mutableStateOf("") }
    var confirmLink by remember { mutableStateOf(false) }
    var outcome by remember(person.id) { mutableStateOf(InteractionOutcome.UNSPECIFIED) }
    var followUp by remember(person.id) { mutableStateOf(false) }
    var outcomeMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(person.id) {
        try { notes = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.interactions(person.id) } } }
        catch (_: Exception) { status = "Interaction history unavailable. Check workspace authorisation." }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(person.name) }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.distinct().forEach { Text("${it.phone}${if(it.primary) " (Primary)" else ""}\nAccount ${it.account}\n${it.schemeName} (${it.schemeCode})\n${it.category}") }
            entries.distinctBy { Triple(it.phone,it.account,it.schemeCode) }.forEach {
                Text("Imported number: ${it.originalPhone ?: "Not recorded by the older importer"}")
                Text("Validation: ${it.phoneStatus}${if(it.phonePlan.isEmpty()) "" else " · ${it.phonePlan}"}")
                if(it.phoneCorrection.isNotEmpty()) Text(it.phoneCorrection)
            }
            if(person.location.isNotBlank()) Text("Service location: ${person.location}")
            if(person.notes.isNotBlank()) Text("Customer notes: ${person.notes}")
            TextButton(enabled = !Call.inCall(),onClick = onCallback) { Text("Call back using dialler") }
            Text("Interaction notes", style = MaterialTheme.typography.titleSmall)
            notes.forEach { note ->
                Text(note.text)
                Text("${note.author} · ${java.util.Date(note.createdAt)}",style = MaterialTheme.typography.bodySmall)
                Text("${note.outcome.label} · ${note.followUp.label}")
                if(note.followUp == FollowUpState.COMPLETE) Text("Completed by ${note.completedBy} · ${java.util.Date(note.completedAt)}",
                    style = MaterialTheme.typography.bodySmall)
                if(note.followUp == FollowUpState.PENDING) TextButton(enabled = !saving,onClick = { scope.launch {
                    saving = true
                    try {
                        withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.completeFollowUp(person.id,note.id) } }
                        notes = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.interactions(person.id) } }
                        onInteractionsChanged(); status = "Follow-up marked complete"
                    } catch (_: Exception) { status = "Could not complete follow-up. Check workspace authorisation." }
                    finally { saving = false }
                } }) { Text("Mark follow-up complete") }
                HorizontalDivider()
            }
            OutlinedTextField(draft,{ if(it.length <= 2000) draft = it },enabled = !saving,label = { Text("New call note") })
            Box {
                TextButton(enabled = !saving,onClick = { outcomeMenu = true }) { Text("Call outcome: ${outcome.label}") }
                DropdownMenu(expanded = outcomeMenu,onDismissRequest = { outcomeMenu = false }) {
                    InteractionOutcome.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label) },onClick = {
                        outcome = value; if(value == InteractionOutcome.CALLBACK_REQUIRED) followUp = true; outcomeMenu = false
                    }) }
                }
            }
            Row {
                Checkbox(checked = followUp,onCheckedChange = { followUp = it },enabled = !saving && outcome != InteractionOutcome.CALLBACK_REQUIRED)
                Text("Follow-up required on this phone")
            }
            TextButton(enabled = draft.isNotBlank() && !saving,onClick = { scope.launch {
                saving = true
                try {
                    val entry = InteractionDraft(draft,outcome,followUp)
                    withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.saveInteraction(person.id,entry) } }
                    notes = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.interactions(person.id) } }
                    draft = ""; outcome = InteractionOutcome.UNSPECIFIED; followUp = false
                    onInteractionsChanged(); status = "Call note and outcome saved"
                } catch (_: Exception) { status = "Check workspace authorisation and refresh history before retrying the note." }
                finally { saving = false }
            } }) { Text("Save call note") }
            if(administratorAccess()) {
                OutlinedTextField(name,{ name = it },label = { Text("Customer name") })
                OutlinedTextField(location,{ location = it },label = { Text("Service location (optional)") })
                TextButton(onClick = { scope.launch {
                    try { withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.updateCustomer(person.id,name,location) } }; onUpdated() }
                    catch (_: Exception) { status = "Customer update failed" }
                } }) { Text("Save authorised changes") }
                OutlinedTextField(linkScheme,{ linkScheme = it },label = { Text("Existing account scheme code") })
                OutlinedTextField(linkAccount,{ linkAccount = it },label = { Text("Existing account number to associate") })
                TextButton(enabled = linkScheme.isNotBlank() && linkAccount.isNotBlank(),onClick = { confirmLink = true }) { Text("Review account association") }
            }
            Text(status)
        }
    },confirmButton = { TextButton(onClick = onClose) { Text("Close profile") } })
    if(confirmLink && administratorAccess()) {
        val matches = allRecords.filter { it.schemeCode.equals(linkScheme.trim(),true) && it.account == linkAccount.trim() }
        AlertDialog(onDismissRequest = { confirmLink = false }, title = { Text("Associate an existing account") },
            text = { Text(if(matches.isEmpty()) "No matching account. Check the number and scheme code." else
                "Associate account ${linkAccount.trim()} in ${matches.first().schemeName} with ${person.name}? Current customer associations (${matches.map { it.name }.distinct().joinToString()}) will remain. Confirm only after verifying institutional ownership records.") },
            confirmButton = { TextButton(enabled = matches.isNotEmpty(),onClick = { scope.launch {
                try { withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.associateAccount(person.id,linkAccount,linkScheme) } }; confirmLink = false; onUpdated() }
                catch (_: Exception) { status = "Account association failed"; confirmLink = false }
            } }) { Text("Confirm association") } },dismissButton = { TextButton(onClick = { confirmLink = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SchemeDirectoryScreen(nav: NavController) {
    InstitutionScreenPrivacy()
    val ctx = LocalContext.current; val allowed = directoryAccess(ctx)
    var entries by remember { mutableStateOf<List<CustomerRecord>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(allowed) {
        entries = emptyList(); error = ""
        if(allowed) try { entries = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.records() } } }
        catch (_: Exception) { error = "Scheme directory unavailable. Check workspace authorisation." }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Text("SWUSIP · Scheme Directory",style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { nav.navigateUp() }) { Text("Back to customers") }
        if(!allowed) { WorkspaceUnlock(); DeviceEnrolment(); return@Column }
        OutlinedTextField(query,{ query = it }, label = { Text("Search scheme name or code") },modifier = Modifier.fillMaxWidth())
        if(error.isNotEmpty()) Text(error)
        LazyColumn { items(entries.distinctBy { it.schemeCode }.filter { it.schemeCode.contains(query,true) || it.schemeName.contains(query,true) }) { scheme ->
            TextButton(onClick = { nav.navigate("customers?scheme=${Uri.encode(scheme.schemeCode)}") }) {
                Text("${scheme.schemeName} (${scheme.schemeCode})")
            }
        } }
    }
}

/** Called from the real Call object, never from generated caller data. No lookup
 * result is written to notifications or logs. Answer controls render immediately. */
@Composable
fun CallCustomerPanel(number: String, onOpen: (CustomerRecord?) -> Unit) {
    val ctx = LocalContext.current
    val authorised = directoryAccess(ctx)
    val policy=DirectoryTelephoneSettings.normalisation(ctx)
    var matches by remember(number) { mutableStateOf<List<CustomerRecord>>(emptyList()) }
    var state by remember(number) { mutableStateOf("Looking up customer…") }
    LaunchedEffect(number, authorised,policy.version) {
        if(!authorised) { matches = emptyList(); return@LaunchedEffect }
        try {
            matches = withContext(Dispatchers.IO) { CustomerDirectory(ctx).use { it.lookup(number) } }
            state = when(matches.size) { 0 -> "Unknown Customer"; 1 -> matches.first().name; else -> "Multiple customer accounts (${matches.size})" }
        } catch (_: Exception) { state = "Unlock to access customer directory" }
    }
    if (authorised) {
        if(matches.isNotEmpty()) InstitutionScreenPrivacy()
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(state, style = MaterialTheme.typography.titleMedium)
            when(val identity = CustomerMatching.identify(matches,number,policy)) {
                is CallerIdentity.Single -> identity.record.let { Text("${it.phone} · Account ${it.account}\n${it.schemeName} (${it.schemeCode})") }
                is CallerIdentity.Multiple -> {
                    Text("Confirm the caller’s scheme and account; no account has been selected.")
                    LazyColumn(Modifier.heightIn(max=180.dp)) { items(identity.matches) { match ->
                        TextButton(onClick={onOpen(match)}) { Text("${match.name} · Account ${match.account}\n${match.schemeName}") }
                    } }
                }
                CallerIdentity.Unknown -> Unit
            }
            TextButton(onClick = {onOpen(matches.singleOrNull())}) { Text(if(matches.size > 1) "Review matching customers" else if(matches.size == 1) "Open customer profile" else "Search customer directory") }
        }
    } else { Text("Unlock the institutional workspace to identify this caller."); WorkspaceUnlock() }
}
