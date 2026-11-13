package com.tutpro.baresip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Number entry only. Call initiation continues through the existing SIP path. */
@Composable
internal fun DiallerKeypad(enabled: Boolean, onKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    OutlinedButton(onClick = { onKey(digit) }, enabled = enabled,
                        modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(digit, fontSize = 22.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { onKey("Backspace") }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Backspace") }
            OutlinedButton(onClick = { onKey("Clear number") }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear number") }
        }
    }
}
