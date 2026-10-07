@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.BillingConfig

data class PlanOption(
    val productId: String,
    val title: String,
    val price: String,
    val perDay: String,
    val badge: String = "",
    val isPopular: Boolean = false,
)

@Composable
fun PaywallScreen(
    onPlanSelected: (String) -> Unit,   // productId
    onDismiss: () -> Unit,
    onWatchAd: (() -> Unit)? = null,    // null = no ad option
    isLoading: Boolean = false,
) {
    val plans = listOf(
        PlanOption(
            productId = BillingConfig.PRODUCT_PREMIUM_WEEKLY,
            title = "Weekly",
            price = BillingConfig.PRICE_WEEKLY_DISPLAY,
            perDay = BillingConfig.PRICE_WEEKLY_PER_DAY,
        ),
        PlanOption(
            productId = BillingConfig.PRODUCT_PREMIUM_MONTHLY,
            title = "Monthly",
            price = BillingConfig.PRICE_MONTHLY_DISPLAY,
            perDay = BillingConfig.PRICE_MONTHLY_PER_DAY,
            badge = "Most Popular",
            isPopular = true,
        ),
        PlanOption(
            productId = BillingConfig.PRODUCT_PREMIUM_YEARLY,
            title = "Yearly",
            price = BillingConfig.PRICE_YEARLY_DISPLAY,
            perDay = BillingConfig.PRICE_YEARLY_PER_DAY,
            badge = "Best Value",
        ),
    )

    var selectedPlan by remember { mutableStateOf(plans[1].productId) }  // monthly default

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460),
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Spacer(Modifier.height(32.dp))
        Text("⭐", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Go Premium",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Unlock all servers, zero ads, unlimited speed",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // ── Features ─────────────────────────────────────────────────────────
        val features = listOf(
            "✅  1,000+ servers in 50+ countries",
            "✅  All protocols: VLESS, Trojan, OpenVPN…",
            "✅  No ads, no interruptions",
            "✅  Unlimited bandwidth",
            "✅  Auto-select fastest server",
            "✅  Priority support",
        )
        features.forEach { feature ->
            Text(
                text = feature,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Plan Cards ───────────────────────────────────────────────────────
        plans.forEach { plan ->
            PlanCard(
                plan = plan,
                selected = selectedPlan == plan.productId,
                onClick = { selectedPlan = plan.productId },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(24.dp))

        // ── CTA Button ───────────────────────────────────────────────────────
        Button(
            onClick = { onPlanSelected(selectedPlan) },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C63FF),
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    "Start Premium — ${plans.first { it.productId == selectedPlan }.price}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        // ── Watch Ad option ──────────────────────────────────────────────────
        if (onWatchAd != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onWatchAd) {
                Text(
                    "Or watch an ad for 30 min free access 🎬",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text(
                "Maybe later",
                color = Color.White.copy(alpha = 0.4f),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Cancel anytime. Billed via Google Play. No commitments.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(
    plan: PlanOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.15f),
        label = "plan-border",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF6C63FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        label = "plan-bg",
    )

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Radio
                RadioButton(selected = selected, onClick = onClick)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = plan.perDay,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = plan.price,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    if (plan.title == "Yearly") {
                        Text(
                            text = "Save 50%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        // Badge (Most Popular / Best Value)
        if (plan.badge.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .offset(y = (-10).dp),
                shape = RoundedCornerShape(8.dp),
                color = if (plan.isPopular) Color(0xFF6C63FF) else Color(0xFFFF9800),
            ) {
                Text(
                    text = plan.badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
