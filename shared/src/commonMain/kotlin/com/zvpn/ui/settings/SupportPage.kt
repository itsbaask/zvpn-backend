@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zvpn.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.*
import com.zvpn.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SupportPage(
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val platform = LocalPlatform.current
    val copied = stringResource(Res.string.support_copied)

    SettingsPageScaffold(
        title = stringResource(Res.string.support_title),
        subtitle = stringResource(Res.string.support_subtitle),
        contentPadding = contentPadding,
        modifier = modifier,
        titleIcon = Res.drawable.ic_favorite,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_favorite),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    stringResource(Res.string.support_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WalletCard(
                icon = Res.drawable.ic_coin_tether,
                label = stringResource(Res.string.support_usdt),
                address = "TTLShqrSAtywpsiZXwr9ZfV3aHDvGZMnNq",
                badgeColor = Color(0xFF00897E),
                onCopy = { platform.copyToClipboard(it); platform.toast(copied) },
            )
            WalletCard(
                icon = Res.drawable.ic_coin_ton,
                label = stringResource(Res.string.support_ton),
                address = "UQD6lg5A4mOHmp2-cXsG-ifOr4OS1MihyMuBF8Xvs-_9NLbj",
                badgeColor = Color(0xFF1E63D0),
                onCopy = { platform.copyToClipboard(it); platform.toast(copied) },
            )
            WalletCard(
                icon = Res.drawable.ic_coin_tron,
                label = stringResource(Res.string.support_tron),
                address = "TTLShqrSAtywpsiZXwr9ZfV3aHDvGZMnNq",
                badgeColor = Color(0xFFC0362C),
                onCopy = { platform.copyToClipboard(it); platform.toast(copied) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.support_usdt_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WalletCard(
    icon: DrawableResource,
    label: String,
    address: String,
    badgeColor: Color,
    onCopy: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().clickable { onCopy(address) },
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onCopy(address) }) {
                Icon(
                    painterResource(Res.drawable.ic_content_paste),
                    contentDescription = stringResource(Res.string.action_copy),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
