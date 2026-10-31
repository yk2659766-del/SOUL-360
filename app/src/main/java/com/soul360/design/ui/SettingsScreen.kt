package com.soul360.design.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.soul360.design.data.AppSettings

@Composable
fun SettingsScreen(settings: AppSettings, onClose: () -> Unit) {
    var url by remember { mutableStateOf(settings.gatewayUrl) }
    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(settings.hasToken) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI gateway", style = MaterialTheme.typography.titleMedium)
            Text("Optional. Needed only for AI render and Make 3D. Photo editing, cutouts and the 3D Studio work without it. " +
                "Deploy the gateway in backend/ first. Never enter OpenAI or Meshy keys here — they stay on the gateway.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Gateway address (https://…)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Button(onClick = {
                note = try { settings.saveGatewayUrl(url); url = settings.gatewayUrl; "Gateway address saved." }
                catch (e: Exception) { e.message }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save address") }

            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(),
                label = { Text(if (hasToken) "App token (saved — enter to replace)" else "App token") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    note = try { settings.saveToken(token); token = ""; hasToken = true; "App token saved (encrypted on this device)." }
                    catch (e: Exception) { e.message }
                }, modifier = Modifier.weight(1f)) { Text("Save token") }
                OutlinedButton(onClick = { settings.removeToken(); hasToken = false; note = "App token removed." },
                    enabled = hasToken, modifier = Modifier.weight(1f)) { Text("Remove token") }
            }
            note?.let { Text(it, color = Palette.Orange) }
            Text(
                if (settings.gatewayUrl.isNotEmpty() && hasToken) "AI features: ready" else "AI features: not configured",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
