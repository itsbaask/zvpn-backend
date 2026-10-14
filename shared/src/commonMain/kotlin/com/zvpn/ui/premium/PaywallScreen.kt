@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.zvpn.ui.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.BillingConfig
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.ic_bolt
import com.zvpn.shared.resources.ic_close
import com.zvpn.ui.theme.ZGradients
import com.zvpn.ui.theme.ZHotPink
import com.zvpn.ui.theme.ZLime
import com.zvpn.ui.theme.ZViolet
import org.jetbrains.compose.resources.painterResource

data class PlanOption(
    val productId: String,
    val title: String,
    val price: String,
    val perDay: String,
    val badge: String = "",
    val isPopular: Boolean = false,
    val savePct: String = "",
)

/**
 * PaywallScreen — redesigned to match ZVPN's design language exactly.
 *
 * Uses ONLY MaterialTheme tokens (no hardcoded dark/light colours).
 * The hero uses ZGradients (Violet → HotPink → Lime) — same as the
 * connection animation — so it feels native to the app.
 */
@Composable
fun PaywallScreen(
    onPlanSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onWatchAd: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    val plans = remember {
        listOf(
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
                savePct = "Save 58%",
            ),
            PlanOption(
                productId = BillingConfig.PRODUCT_PREMIUM_PLUS_YEARLY,
                title = "Ultra All-Access",
                price = BillingConfig.PRICE_PLUS_YEARLY_DISPLAY,
                perDay = BillingConfig.PRICE_PLUS_YEARLY_PER_DAY,
                badge = "Ultimate",
                savePct = "Save 58%",
            ),
        )
    }

    var selectedPlan by remember { mutableStateOf(plans[1].productId) }

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {

        // ── Decorative hero gradient blob (top) ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .drawBehind {
                    // Radial glow — same violet/pink as app orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZViolet.copy(alpha = 0.35f),
                                ZHotPink.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.75f,
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width / 2f, 0f),
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Close",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Icon — same gradient orb as connect button ────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ZViolet, ZHotPink),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_bolt),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text = "ZVPN Premium",
                style = typo.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = cs.onBackground,
            )

            Spacer(Modifier.height(6.dp))

            // Gradient accent line under title
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(ZViolet, ZHotPink, ZLime))
                    )
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Unlock 10,000+ servers, zero ads, unlimited bandwidth & multi-hop chains",
                style = typo.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Feature list ──────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = cs.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    val features = listOf(
                        Triple(ZViolet, "⚡", "10,000+ verified high-speed servers"),
                        Triple(ZHotPink, "🛡️", "All protocols: VLESS, Hysteria 2, WireGuard…"),
                        Triple(ZLime, "🚫", "Zero ads — completely ad-free experience"),
                        Triple(ZViolet, "⛓️", "Multi-Hop & Custom DNS anti-tracker"),
                        Triple(ZHotPink, "💬", "Priority support & instant activation"),
                    )
                    features.forEachIndexed { i, (accent, emoji, text) ->
                        FeatureRow(emoji = emoji, text = text, accent = accent)
                        if (i < features.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = cs.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Plan cards ────────────────────────────────────────────────────
            plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = selectedPlan == plan.productId,
                    onClick = { selectedPlan = plan.productId },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(16.dp))

            // ── CTA ───────────────────────────────────────────────────────────
            val selectedPlanData = plans.firstOrNull { it.productId == selectedPlan }
            Button(
                onClick = { onPlanSelected(selectedPlan) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = cs.onSurface.copy(alpha = 0.12f),
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(ZViolet, ZHotPink)),
                            shape = MaterialTheme.shapes.large,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = if (selectedPlanData != null)
                                "Continue — ${selectedPlanData.price}"
                            else "Continue",
                            style = typo.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                }
            }

            // ── Watch ad option ───────────────────────────────────────────────
            if (onWatchAd != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onWatchAd,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = cs.primary,
                    ),
                ) {
                    Text(
                        "🎬  Watch ad for 30 min free access",
                        style = typo.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onDismiss) {
                Text(
                    "Maybe later",
                    style = typo.labelLarge,
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Cancel anytime in Google Play. Subscription renews automatically.",
                style = typo.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Plan Card ────────────────────────────────────────────────────────────────

@Composable
private fun PlanCard(
    plan: PlanOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    val borderColor by animateColorAsState(
        targetValue = if (selected) ZViolet else cs.outlineVariant,
        animationSpec = tween(200),
        label = "plan-border",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
        animationSpec = tween(200),
        label = "plan-bg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.01f else 1f,
        animationSpec = tween(180),
        label = "plan-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.large,
            color = bgColor,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Radio dot
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected)
                                Brush.linearGradient(listOf(ZViolet, ZHotPink))
                            else
                                Brush.linearGradient(listOf(cs.outlineVariant, cs.outlineVariant))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = typo.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                    )
                    Text(
                        text = plan.perDay,
                        style = typo.labelSmall,
                        color = if (selected) cs.onPrimaryContainer.copy(0.7f) else cs.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = plan.price,
                        style = typo.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                    )
                    if (plan.savePct.isNotEmpty()) {
                        Text(
                            text = plan.savePct,
                            style = typo.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZLime,
                        )
                    }
                }
            }
        }

        // Badge chip
        if (plan.badge.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp)
                    .offset(y = (-10).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (plan.isPopular)
                            Brush.linearGradient(listOf(ZViolet, ZHotPink))
                        else
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer,
                                )
                            )
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = plan.badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (plan.isPopular) Color.White
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ─── Feature Row ─────────────────────────────────────────────────────────────

@Composable
private fun FeatureRow(emoji: String, text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Accent dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = "$emoji  $text",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
