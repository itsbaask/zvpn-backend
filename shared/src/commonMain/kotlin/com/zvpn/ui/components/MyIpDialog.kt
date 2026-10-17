package com.zvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zvpn.data.net.NetworkInfo
import com.zvpn.data.net.NetworkInfoRepository
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.ic_public
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@Composable
fun MyIpDialog(
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(NetworkInfo(loading = true)) }

    val loadIpInfo: () -> Unit = {
        scope.launch {
            info = NetworkInfo(loading = true)
            val result = withContext(Dispatchers.Default) {
                runCatching { NetworkInfoRepository().fetchInfo() }.getOrDefault(NetworkInfo(error = "Failed to fetch IP info"))
            }
            info = result.copy(loading = false)
        }
    }

    LaunchedEffect(Unit) {
        com.zvpn.domain.ads.PointsManager.unlockFeature("my_ip_geolocation_inspector", com.zvpn.domain.billing.BillingConfig.FEATURE_MY_IP_INSPECTOR)
        loadIpInfo()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_public),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text("My IP Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (info.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                } else if (info.error != null) {
                    Text(
                        text = "Error: ${info.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val ip = info.ipv4?.takeIf { it.isNotBlank() } ?: info.ipv6?.takeIf { it.isNotBlank() } ?: "Unknown"
                        val country = info.country?.takeIf { it.isNotBlank() } ?: "Unknown"
                        val city = info.city?.takeIf { it.isNotBlank() } ?: "Unknown"
                        val isp = info.isp?.takeIf { it.isNotBlank() } ?: "Unknown"
                        IpDetailRow("IP Address", ip)
                        IpDetailRow("Country", "$country (${info.countryCode ?: "??"})")
                        IpDetailRow("City", city)
                        IpDetailRow("ISP / Org", isp)
                        IpDetailRow("Connection Status", if (info.underVpn) "Protected (VPN Active)" else "Direct (Unprotected)")
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { loadIpInfo() }) {
                    Text("Refresh")
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun IpDetailRow(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
