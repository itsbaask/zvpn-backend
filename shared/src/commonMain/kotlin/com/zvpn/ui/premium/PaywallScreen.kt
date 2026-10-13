@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zvpn.domain.billing.BillingConfig
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.ic_bolt
import com.zvpn.shared.resources.ic_close
import org.jetbrains.compose.resources.painterResource

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
    val plans = remember {
        listOf(
            PlanOption(
                productId = BillingConfig.PRODUCT_PREMIUM_WEEKLY,
                title = "Weekly Access",
                price = BillingConfig.PRICE_WEEKLY_DISPLAY,
                perDay = BillingConfig.PRICE_WEEKLY_PER_DAY,
            ),
            PlanOption(
                productId = BillingConfig.PRODUCT_PREMIUM_MONTHLY,
                title = "Monthly Plan",
                price = BillingConfig.PRICE_MONTHLY_DISPLAY,
                perDay = BillingConfig.PRICE_MONTHLY_PER_DAY,
                badge = "Most Popular",
                isPopular = true,
            ),
            PlanOption(
                productId = BillingConfig.PRODUCT_PREMIUM_YEARLY,
                title = "Yearly Pass",
                price = BillingConfig.PRICE_YEARLY_DISPLAY,
                perDay = BillingConfig.PRICE_YEARLY_PER_DAY,
                badge = "Best Value",
            ),
            PlanOption(
                productId = BillingConfig.PRODUCT_PREMIUM_PLUS_YEARLY,
                title = "Ultra All-Access Yearly",
                price = BillingConfig.PRICE_PLUS_YEARLY_DISPLAY,
                perDay = BillingConfig.PRICE_PLUS_YEARLY_PER_DAY,
                badge = "Ultimate Access",
            ),
        )
    }

    var selectedPlan by remember { mutableStateOf(plans[1].productId) }  // monthly default

    val colorScheme = MaterialTheme.colorScheme

    val bgGradient = remember(colorScheme) {
        Brush.verticalGradient(
            listOf(
                colorScheme.surfaceContainerLowest,
                colorScheme.surfaceContainer,
                colorScheme.background,
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top Dismiss Bar ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Close",
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Header Icon & Title ──────────────────────────────────────────────
            Surface(
                shape = CircleShape,
                color = colorScheme.primaryContainer,
                tonalElevation = 6.dp,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_bolt),
                        contentDescription = "Premium",
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "ZVPN Premium ⚡",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onBackground,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Unlock all 7,500+ servers, zero ads, unlimited high speed & multi-hop chains",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Feature Highlights ───────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val features = listOf(
                        "⚡  7,500+ High-Speed Verified Global Servers",
                        "🛡️  All Protocols: VLESS, Hysteria 2, WireGuard, OpenVPN",
                        "🚫  100% Zero Ads & Unlimited Bandwidth",
                        "⛓️  Multi-Hop Chains & Custom DNS Anti-Tracker",
                        "💬  24/7 Priority Support & Immediate Activation",
                    )
                    features.forEach { feature ->
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Plan Options Cards ───────────────────────────────────────────────
            plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = selectedPlan == plan.productId,
                    onClick = { selectedPlan = plan.productId },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))

            // ── CTA Primary Purchase Button ──────────────────────────────────────
            Button(
                onClick = { onPlanSelected(selectedPlan) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    val selectedPrice = plans.firstOrNull { it.productId == selectedPlan }?.price.orEmpty()
                    Text(
                        text = "Continue — $selectedPrice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                    )
                }
            }

            // ── Optional Ad Watch Button ─────────────────────────────────────────
            if (onWatchAd != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onWatchAd,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "Watch an ad for 30 min free access 🎬",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onDismiss) {
                Text(
                    text = "Maybe later",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Cancel anytime in Google Play. Secure instant subscription.",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    val borderColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.outlineVariant,
        label = "plan-border",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primaryContainer.copy(alpha = 0.35f) else colorScheme.surfaceContainerLow,
        label = "plan-bg",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
            tonalElevation = if (selected) 4.dp else 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorScheme.primary,
                        unselectedColor = colorScheme.onSurfaceVariant,
                    ),
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = plan.perDay,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = plan.price,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.primary,
                    )
                    if (plan.title.contains("Yearly") || plan.badge.contains("Value")) {
                        Text(
                            text = "Save 58%",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ── Badge Chip ───────────────────────────────────────────────────────────
        if (plan.badge.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset(y = (-10).dp),
                shape = RoundedCornerShape(8.dp),
                color = if (selected) colorScheme.primary else colorScheme.secondaryContainer,
                contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = plan.badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
