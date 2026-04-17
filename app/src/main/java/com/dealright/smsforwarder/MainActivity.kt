package com.dealright.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled via UI state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val needed = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            requestPermissions.launch(needed)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ForwarderScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwarderScreen(vm: MainViewModel = viewModel()) {
    val webhookUrl by vm.webhookUrl.collectAsState()
    val jsonTemplate by vm.jsonTemplate.collectAsState()
    val contentType by vm.contentType.collectAsState()
    val customHeaders by vm.customHeaders.collectAsState()
    val logs by vm.logs.collectAsState()
    var showVarRef by remember { mutableStateOf(false) }
    var showHeaders by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "sms-forward-to-webhook",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Webhook URL input
        OutlinedTextField(
            value = webhookUrl,
            onValueChange = { vm.setWebhookUrl(it) },
            label = { Text("Webhook URL") },
            placeholder = { Text("https://your-webhook-url.com/endpoint") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Content-Type selector
        Text(
            text = "Content-Type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MainViewModel.CONTENT_TYPES.forEachIndexed { index, ct ->
                SegmentedButton(
                    selected = contentType == ct,
                    onClick = { vm.setContentType(ct) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = MainViewModel.CONTENT_TYPES.size,
                    ),
                ) {
                    Text(
                        text = ct.removePrefix("application/"),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom headers (collapsible)
        TextButton(onClick = { showHeaders = !showHeaders }) {
            Text(
                if (showHeaders) "Hide custom headers" else "Add custom headers",
                fontSize = 12.sp,
            )
        }
        AnimatedVisibility(visible = showHeaders) {
            OutlinedTextField(
                value = customHeaders,
                onValueChange = { vm.setCustomHeaders(it) },
                label = { Text("Custom Headers (one per line)") },
                placeholder = { Text("Authorization: Bearer token123\nX-Custom: value") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                singleLine = false,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // JSON template editor
        OutlinedTextField(
            value = jsonTemplate,
            onValueChange = { vm.setJsonTemplate(it) },
            label = { Text("Body Template") },
            placeholder = { Text("""{"text": "{{sender}}: {{body}}"}""") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            singleLine = false,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
        )

        // Variable ref toggle + clear buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { showVarRef = !showVarRef }) {
                Text(
                    if (showVarRef) "Hide variables" else "Show variables",
                    fontSize = 12.sp,
                )
            }
            Row {
                TextButton(onClick = { vm.setWebhookUrl("") }) {
                    Text("Clear URL", fontSize = 12.sp)
                }
                TextButton(onClick = { vm.setJsonTemplate(MainViewModel.DEFAULT_TEMPLATE) }) {
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }

        // Collapsible variable reference
        AnimatedVisibility(visible = showVarRef) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Available {{variables}}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SmsData.VARIABLE_DOCS.forEach { (name, desc) ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            Text(
                                text = "{{$name}}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(170.dp),
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.sendTestMessage() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Send Test")
            }
            OutlinedButton(
                onClick = { vm.clearLogs() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear Log")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Log header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Forwarding Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${logs.size} entries)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable log
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No messages forwarded yet.\nIncoming SMS will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs, key = { "${it.timestamp}-${it.sender}" }) { entry ->
                    LogCard(entry)
                }
            }
        }
    }
}

@Composable
fun JsonBlock(label: String, json: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = json,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(4.dp)
                )
                .padding(8.dp),
        )
    }
}

@Composable
fun LogCard(entry: LogEntry) {
    val statusColor = if (entry.success) Color(0xFF4CAF50) else Color(0xFFF44336)
    val statusText = when {
        entry.error != null -> "ERROR"
        entry.success -> "${entry.responseCode} OK"
        else -> "${entry.responseCode} FAIL"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: timestamp + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor,
                    modifier = Modifier
                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "From: ${entry.sender}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // SMS body
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .padding(8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Request headers
            if (entry.requestHeaders.isNotBlank()) {
                JsonBlock(
                    label = "REQUEST HEADERS",
                    json = entry.requestHeaders,
                    color = Color(0xFFCE93D8),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Request body
            if (entry.requestJson.isNotBlank()) {
                JsonBlock(
                    label = "REQUEST BODY",
                    json = entry.requestJson,
                    color = Color(0xFF81D4FA),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Response headers
            if (entry.responseHeaders != null) {
                JsonBlock(
                    label = "RESPONSE HEADERS",
                    json = entry.responseHeaders,
                    color = Color(0xFFA5D6A7),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Response body or error
            if (entry.error != null) {
                JsonBlock(label = "ERROR", json = entry.error, color = Color(0xFFF44336))
            } else if (entry.responseBody != null) {
                JsonBlock(
                    label = "RESPONSE BODY ${entry.responseCode}",
                    json = entry.responseBody,
                    color = if (entry.success) Color(0xFF4CAF50) else Color(0xFFF44336),
                )
            }
        }
    }
}
