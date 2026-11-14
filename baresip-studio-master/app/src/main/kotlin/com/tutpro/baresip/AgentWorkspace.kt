package com.tutpro.baresip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Local desk status only: never changes PBX DND, queue membership or SIP registration. */
enum class AgentAvailability(val label: String) { AVAILABLE("Available"), BUSY("Busy"), OFF_DUTY("Off duty") }

@Composable
fun AgentWorkspaceStatus() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(AgentAvailability.OFF_DUTY) }
    var identity by remember { mutableStateOf("") }
    LaunchedEffect(context) {
        while (true) {
            allowed = InstitutionAccess.agent(context)
            identity = if (allowed) OfflineAuthorisation.identity(context) else ""
            if (!allowed) status = AgentAvailability.OFF_DUTY
            delay(250)
        }
    }
    if (allowed) {
        Text("Agent: $identity")
        Text("Local desk status: ${status.label}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AgentAvailability.entries.forEach { option ->
                FilterChip(selected = status == option, onClick = {
                    if (InstitutionAccess.agent(context)) status = option
                }, label = { Text(option.label) })
            }
        }
        Text("Status applies only on this phone and resets when the workspace locks. It does not change PBX queues or stop incoming calls.",
            style = MaterialTheme.typography.bodySmall)
    } else Text("Agent workspace locked. Unlock to view your institutional identity and set local desk status.")
}
