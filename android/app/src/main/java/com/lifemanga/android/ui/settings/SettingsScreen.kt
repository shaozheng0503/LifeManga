package com.lifemanga.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifemanga.android.data.BubbleMode
import com.lifemanga.android.data.EndpointType
import com.lifemanga.android.data.MangaStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    var draftKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EndpointCard(
                currentType = state.settings.endpointType,
                azureEndpoint = state.settings.azureEndpoint,
                azureDeployment = state.settings.azureDeployment,
                azureApiVersion = state.settings.azureApiVersion,
                onTypeChange = vm::setEndpointType,
                onEndpointChange = vm::setAzureEndpoint,
                onDeploymentChange = vm::setAzureDeployment,
                onApiVersionChange = vm::setAzureApiVersion,
            )

            ApiKeyCard(
                masked = state.apiKeyMasked,
                hasKey = state.hasApiKey,
                draftKey = draftKey,
                keyVisible = keyVisible,
                isAzure = state.settings.endpointType == EndpointType.AZURE,
                onDraftChange = { draftKey = it },
                onToggleVisibility = { keyVisible = !keyVisible },
                onSave = {
                    if (draftKey.isNotBlank()) {
                        vm.setApiKey(draftKey)
                        draftKey = ""
                    }
                },
                onClear = { vm.clearApiKey() },
            )

            StyleCard(current = state.settings.style, onSelect = vm::setStyle)
            ColorCard(isColor = state.settings.isColor, onChange = vm::setIsColor)
            BubbleCard(current = state.settings.bubbleMode, onSelect = vm::setBubbleMode)

            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointCard(
    currentType: EndpointType,
    azureEndpoint: String,
    azureDeployment: String,
    azureApiVersion: String,
    onTypeChange: (EndpointType) -> Unit,
    onEndpointChange: (String) -> Unit,
    onDeploymentChange: (String) -> Unit,
    onApiVersionChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("接口类型", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow {
                EndpointType.entries.forEachIndexed { idx, type ->
                    SegmentedButton(
                        selected = type == currentType,
                        onClick = { onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(idx, EndpointType.entries.size),
                    ) { Text(type.displayName) }
                }
            }
            if (currentType == EndpointType.AZURE) {
                OutlinedTextField(
                    value = azureEndpoint,
                    onValueChange = onEndpointChange,
                    label = { Text("Endpoint") },
                    placeholder = { Text("https://<resource>.cognitiveservices.azure.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = azureDeployment,
                    onValueChange = onDeploymentChange,
                    label = { Text("Deployment 名称") },
                    placeholder = { Text("gpt-image-2-…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = azureApiVersion,
                    onValueChange = onApiVersionChange,
                    label = { Text("API Version") },
                    placeholder = { Text("2024-02-01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Azure 模式下会调用 {endpoint}/openai/deployments/{deployment}/images/edits?api-version={ver}，并把 Key 同时塞到 Authorization 和 api-key header。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "OpenAI 直连：调用 https://api.openai.com/v1/images/edits ，需要 sk- 开头的 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    masked: String,
    hasKey: Boolean,
    draftKey: String,
    keyVisible: Boolean,
    isAzure: Boolean,
    onDraftChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isAzure) "Azure API Key" else "OpenAI API Key",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (hasKey) "已保存：$masked" else "尚未设置（必填）",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasKey) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            OutlinedTextField(
                value = draftKey,
                onValueChange = onDraftChange,
                label = { Text(if (isAzure) "Azure subscription key" else "sk-…") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "隐藏" else "显示",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, enabled = draftKey.isNotBlank()) { Text("保存") }
                if (hasKey) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
            }
            Text(
                text = "Key 用 EncryptedSharedPreferences 加密存设备本地，不上传不备份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StyleCard(current: MangaStyle, onSelect: (MangaStyle) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("默认漫画风格", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            MangaStyle.entries.forEach { style ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = style == current,
                        onClick = { onSelect(style) },
                        label = { Text(style.displayName) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        style.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorCard(isColor: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("彩色 / 黑白", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isColor) "彩色（cel-shading）" else "纯黑白漫画线稿",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isColor, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun BubbleCard(current: BubbleMode, onSelect: (BubbleMode) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("气泡文字", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            BubbleMode.entries.forEach { mode ->
                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                    FilterChip(
                        selected = mode == current,
                        onClick = { onSelect(mode) },
                        label = { Text(mode.displayName) },
                    )
                }
            }
        }
    }
}
