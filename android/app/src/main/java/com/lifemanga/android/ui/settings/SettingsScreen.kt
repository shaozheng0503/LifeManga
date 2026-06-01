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
import com.lifemanga.android.data.MangaStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    var draftComfyKey by remember { mutableStateOf("") }
    var comfyKeyVisible by remember { mutableStateOf(false) }

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
            ComfyUiCard(
                comfyUiUrl = state.settings.comfyUiUrl,
                onUrlChange = vm::setComfyUiUrl,
                maskedKey = state.comfyApiKeyMasked,
                hasKey = state.hasComfyApiKey,
                draftKey = draftComfyKey,
                keyVisible = comfyKeyVisible,
                onDraftChange = { draftComfyKey = it },
                onToggleVisibility = { comfyKeyVisible = !comfyKeyVisible },
                onSaveKey = {
                    if (draftComfyKey.isNotBlank()) {
                        vm.setComfyApiKey(draftComfyKey)
                        draftComfyKey = ""
                    }
                },
                onClearKey = { vm.clearComfyApiKey() },
            )

            QwenCard(
                qwenUrl = state.settings.qwenUrl,
                onUrlChange = vm::setQwenUrl,
            )

            StoryModeCard(
                storyMode = state.settings.storyMode,
                panelCount = state.settings.panelCount,
                onStoryModeChange = vm::setStoryMode,
                onPanelCountChange = vm::setPanelCount,
            )

            StyleCard(current = state.settings.style, onSelect = vm::setStyle)
            ColorCard(isColor = state.settings.isColor, onChange = vm::setIsColor)
            BubbleCard(current = state.settings.bubbleMode, onSelect = vm::setBubbleMode)

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ComfyUiCard(
    comfyUiUrl: String,
    onUrlChange: (String) -> Unit,
    maskedKey: String,
    hasKey: Boolean,
    draftKey: String,
    keyVisible: Boolean,
    onDraftChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveKey: () -> Unit,
    onClearKey: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ComfyUI 后端", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "填写你的 ComfyUI 实例地址。已预填本项目默认部署地址，一般无需修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = comfyUiUrl,
                onValueChange = onUrlChange,
                label = { Text("ComfyUI URL") },
                placeholder = { Text("https://your-comfy-instance:8188") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Text("comfy.org API Key（Wan2.5 生图必填）", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Wan2.5 / Flux 等 API 节点需要 comfy.org 账号授权。\n" +
                    "获取方式：登录 comfy.org → Account → API Keys → 创建新 Key，粘贴到这里。\n" +
                    "注意：这是 comfy.org 的账号 Key，不是 ComfyUI 服务器密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasKey) "已保存：$maskedKey" else "未设置（不填无法使用 Wan2.5/Flux API 节点）",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasKey) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            OutlinedTextField(
                value = draftKey,
                onValueChange = onDraftChange,
                label = { Text("API Key") },
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
                OutlinedButton(onClick = onSaveKey, enabled = draftKey.isNotBlank()) { Text("保存") }
                if (hasKey) {
                    TextButton(onClick = onClearKey) { Text("清除") }
                }
            }
        }
    }
}

@Composable
private fun QwenCard(
    qwenUrl: String,
    onUrlChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Qwen 后端（剧本模式）", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "仅剧本模式使用。已预填 Qwen3.5-9B vLLM 部署地址，一般无需修改。\n" +
                    "剧本模式流程：先由 Qwen 把你的故事描述拆解成多格分镜脚本，再由 ComfyUI 逐格渲染成图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = qwenUrl,
                onValueChange = onUrlChange,
                label = { Text("Qwen vLLM URL") },
                placeholder = { Text("https://your-qwen-instance:8000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryModeCard(
    storyMode: Boolean,
    panelCount: Int,
    onStoryModeChange: (Boolean) -> Unit,
    onPanelCountChange: (Int) -> Unit,
) {
    val panelOptions = listOf(4, 6, 8)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("剧本模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (storyMode) "先用 Qwen 生成剧本，再用 ComfyUI 渲染" else "直接用 ComfyUI 生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = storyMode, onCheckedChange = onStoryModeChange)
            }
            if (storyMode) {
                HorizontalDivider()
                Text("每次生成格数", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow {
                    panelOptions.forEachIndexed { idx, count ->
                        SegmentedButton(
                            selected = count == panelCount,
                            onClick = { onPanelCountChange(count) },
                            shape = SegmentedButtonDefaults.itemShape(idx, panelOptions.size),
                        ) { Text("$count 格") }
                    }
                }
            }
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
