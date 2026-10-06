@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.settings

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.zvpn.ui.theme.AccentPresets
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.*
import com.zvpn.ui.platform.draggableHorizontalScroll

private val DisabledAlpha = 0.38f

@Composable
private fun SettingsRow(
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
    val descColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (!description.isNullOrEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = descColor)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        description = summary,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        description = summary,
        enabled = enabled,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun SettingsEditRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    numeric: Boolean = false,
    placeholder: String? = null,
    onValueChanged: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = when {
        isPassword && value.isNotEmpty() -> "••••••"
        value.isNotEmpty() -> value
        else -> placeholder
    }

    SettingsRow(
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
    )

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        var reveal by remember { mutableStateOf(false) }
        val save = {
            showDialog = false
            onValueChanged(text.trim())
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    visualTransformation = if (isPassword && !reveal) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    trailingIcon = if (isPassword) {
                        {
                            Icon(
                                painterResource(
                                    if (reveal) Res.drawable.ic_visibility_off
                                    else Res.drawable.ic_visibility
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { reveal = !reveal },
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = save) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
fun <T> SettingsListRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
        ?: options.firstOrNull()?.second.orEmpty()

    SettingsRow(
        title = title,
        description = selectedLabel,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == selected,
                                    onClick = {
                                        showDialog = false
                                        onSelected(value)
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == selected,
                                onClick = {
                                    showDialog = false
                                    onSelected(value)
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label.ifBlank { stringResource(Res.string.option_none) },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
fun SettingsInfoRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsMenuRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    badgeColor: androidx.compose.ui.graphics.Color? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    val defaultBadge = badgeColor ?: MaterialTheme.colorScheme.primary
    val defaultTint = iconTint ?: defaultBadge
    SettingsRow(
        title = title,
        description = subtitle,
        enabled = true,
        onClick = onClick,
        leading = leadingIcon?.let {
            {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(defaultBadge.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = defaultTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!trailingText.isNullOrBlank()) {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), content = content)
        }
    }
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun SettingsPageScaffold(
    title: String,
    subtitle: String? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
    titleIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        com.zvpn.ui.components.PageHeader(
            title = title,
            subtitle = subtitle,
            titleIcon = titleIcon,
        )
        Spacer(Modifier.height(12.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun AccentColorRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().draggableHorizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccentPresets.forEachIndexed { i, preset ->
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(preset.light)
                        .border(
                            width = if (selected == i) 3.dp else 1.dp,
                            color = if (selected == i) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(i) },
                )
            }
        }
    }
}
