package com.soul360.design.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.soul360.design.data.AiProvider
import com.soul360.design.data.AppSettings

@Composable
fun SettingsScreen(settings: AppSettings, onClose: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }   // bump to refresh after saves
    var provider by remember { mutableStateOf(settings.provider) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Text("AI Render", style = MaterialTheme.typography.titleLarge)
                Text("Choose which AI turns your design into a photoreal render. You use your own API key — " +
                    "it is encrypted on this phone and only sent to that provider.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
                Row(Modifier.padding(top = 6.dp).clip(CircleShape).background(Palette.Sand).padding(3.dp)) {
                    AiProvider.entries.forEach { p ->
                        val on = p == provider
                        Text(p.label,
                            Modifier.weight(1f).clip(CircleShape).background(if (on) Palette.Orange else Color.Transparent)
                                .clickable { provider = p; settings.provider = p }
                                .padding(vertical = 10.dp),
                            color = if (on) Color.White else Palette.Ink,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Text("Default for new renders. You can switch on the render screen too.",
                    style = MaterialTheme.typography.labelSmall, color = Palette.Taupe)
            }

            ProviderKeyCard(settings, AiProvider.OPENAI, version,
                help = "Get a key at platform.openai.com → API keys. Billing must be enabled.",
                link = "https://platform.openai.com/api-keys",
                onNote = { note = it; version++ })
            ProviderKeyCard(settings, AiProvider.GEMINI, version,
                help = "Get a key at aistudio.google.com → Get API key. Image models may need billing enabled.",
                link = "https://aistudio.google.com/apikey",
                onNote = { note = it; version++ })

            note?.let { Text(it, color = Palette.OrangeDeep, style = MaterialTheme.typography.bodyMedium) }

            GatewayCard(settings) { note = it }

            Text("Keep your API keys private. Don't share this phone's app data or screenshots of the key.",
                style = MaterialTheme.typography.labelSmall, color = Palette.Taupe)
            TextButton(onClick = { openInBrowser(context, "https://github.com/yk2659766-del/SOUL-360") }) {
                Text("App source & latest version")
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ProviderKeyCard(settings: AppSettings, p: AiProvider, version: Int, help: String, link: String, onNote: (String?) -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    val hasKey = remember(version) { settings.hasApiKey(p) }
    var model by remember(version) { mutableStateOf(if (p == AiProvider.OPENAI) settings.openaiModel else settings.geminiModel) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (hasKey) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF3C8D5A))
                Text(" Connected", style = MaterialTheme.typography.labelMedium, color = Color(0xFF3C8D5A))
            }
        }
        Text(help, style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
        TextButton(onClick = { openInBrowser(context, link) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Open key page →")
        }
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(),
            label = { Text(if (hasKey) "API key (saved — paste to replace)" else "API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onNote(try { settings.saveApiKey(p, key); key = ""; "${p.label} key saved." } catch (e: Exception) { e.message })
            }, modifier = Modifier.weight(1f)) { Text("Save key") }
            OutlinedButton(onClick = { settings.removeApiKey(p); onNote("${p.label} key removed.") },
                enabled = hasKey, modifier = Modifier.weight(1f)) { Text("Remove") }
        }
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Model") },
            supportingText = {
                Text(if (p == AiProvider.OPENAI) "Default ${AppSettings.DEFAULT_OPENAI_MODEL}. Also: gpt-image-1.5"
                else "Default ${AppSettings.DEFAULT_GEMINI_MODEL}. Also: gemini-3-pro-image-preview")
            })
        TextButton(onClick = {
            if (p == AiProvider.OPENAI) settings.openaiModel = model else settings.geminiModel = model
            onNote("${p.label} model set to ${if (p == AiProvider.OPENAI) settings.openaiModel else settings.geminiModel}.")
        }) { Text("Save model") }
    }
}

@Composable
private fun GatewayCard(settings: AppSettings, onNote: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(settings.gatewayUrl) }
    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(settings.hasToken) }
    Card {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Make 3D (advanced)", style = MaterialTheme.typography.titleMedium)
                Text("Optional server for furniture image → 3D model (Meshy).", style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
            }
            Text(if (expanded) "Hide" else "Set up", color = Palette.Orange, style = MaterialTheme.typography.labelLarge)
        }
        if (expanded) {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Gateway address (https://…)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Button(onClick = {
                onNote(try { settings.saveGatewayUrl(url); url = settings.gatewayUrl; "Gateway address saved." } catch (e: Exception) { e.message })
            }, modifier = Modifier.fillMaxWidth()) { Text("Save address") }
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(),
                label = { Text(if (hasToken) "App token (saved)" else "App token") },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onNote(try { settings.saveToken(token); token = ""; hasToken = true; "App token saved." } catch (e: Exception) { e.message })
                }, modifier = Modifier.weight(1f)) { Text("Save token") }
                OutlinedButton(onClick = { settings.removeToken(); hasToken = false; onNote("App token removed.") },
                    enabled = hasToken, modifier = Modifier.weight(1f)) { Text("Remove") }
            }
        }
    }
}
