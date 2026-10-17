@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.zvpn.ui.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.BillingConfig
import com.zvpn.shared.resources.*
import com.zvpn.ui.theme.ZCyan
import com.zvpn.ui.theme.ZHotPink
import com.zvpn.ui.theme.ZLime
import com.zvpn.ui.theme.ZOnLime
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
 * PaywallScreen — redesigned to match ZVPN's expressive design language.
 *
 * Uses MaterialTheme colorScheme tokens for full Light/Dark mode support.
 * Incorporates ZVPN's signature vibrant palette (ZViolet, ZHotPink, ZLime, ZCyan)
 * and ambient radial glows matching the connection animations.
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
                title = "Weekly Plan",
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
                title = "Yearly Plan",
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
            PlanOption(
                productId = BillingConfig.PRODUCT_PACK_3000_PTS,
                title = "3,000 Points Pack",
                price = "$7.99",
                perDay = "+3,000 Pts",
                badge = "Point Pack",
            ),
            PlanOption(
                productId = BillingConfig.PRODUCT_PACK_1200_PTS,
                title = "1,200 Points Pack",
                price = "$3.99",
                perDay = "+1,200 Pts",
                badge = "Point Pack",
            ),
        )
    }

    var selectedPlan by remember { mutableStateOf(plans[1].productId) }

    // Pulsing background glow animation for hero header
    val infiniteTransition = rememberInfiniteTransition(label = "paywall-glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {

        // ── Ambient Top Hero Radial Glow ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZViolet.copy(alpha = 0.30f * glowPulse),
                                ZHotPink.copy(alpha = 0.15f * glowPulse),
                                ZCyan.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.85f,
                        ),
                        radius = size.width * 0.85f,
                        center = Offset(size.width / 2f, 0f),
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top Navigation Bar ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Restore purchases text button
                TextButton(
                    onClick = { onPlanSelected("restore") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Restore",
                        style = typo.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant,
                    )
                }

                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceContainerHigh.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Close",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Hero Icon Badge ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(16.dp, CircleShape, spotColor = ZViolet)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ZViolet, ZHotPink, ZLime)
                        )
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ZViolet.copy(alpha = 0.25f), ZHotPink.copy(alpha = 0.20f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = ZHotPink,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Premium Badge Tag ─────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cs.primaryContainer,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ZLime)
                    )
                    Text(
                        text = "ZVPN PREMIUM",
                        style = typo.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onPrimaryContainer,
                        letterSpacing = 1.2.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Main Title ───────────────────────────────────────────────────
            Text(
                text = "Unleash Ultimate VPN Power",
                style = typo.headlineMedium,
                fontWeight = FontWeight.Black,
                color = cs.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Gradient accent line
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(3.5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(ZViolet, ZHotPink, ZLime))
                    )
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "10,000+ Supercharged Servers • Ultra Low Ping • Zero Ads • Unlimited Bandwidth",
                style = typo.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(22.dp))

            // ── Feature List Panel ────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cs.surfaceContainer,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    val features = listOf(
                        FeatureItem(Res.drawable.ic_bolt, ZViolet, "10,000+ Ultra Fast Servers", "Worldwide low-ping dedicated nodes"),
                        FeatureItem(Res.drawable.ic_encrypted, ZHotPink, "All Premium Protocols", "VLESS, Hysteria 2, WireGuard & Shadowsocks"),
                        FeatureItem(Res.drawable.ic_check_circle, ZLime, "Zero Ads & Unlimited Speed", "No speed caps, no bandwidth throttling"),
                        FeatureItem(Res.drawable.ic_public, ZCyan, "Multi-Hop & Anti-Tracker", "Double encryption & ad-block DNS built-in"),
                    )
                    features.forEachIndexed { i, feature ->
                        FeatureRow(item = feature)
                        if (i < features.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = cs.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Section Label ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = "SELECT YOUR PLAN",
                    style = typo.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Plan Cards List ──────────────────────────────────────────────
            plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = selectedPlan == plan.productId,
                    onClick = { selectedPlan = plan.productId },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))

            // ── Main CTA Button ──────────────────────────────────────────────
            val selectedPlanData = plans.firstOrNull { it.productId == selectedPlan }
            val ctaText = if (selectedPlanData != null)
                "Continue — ${selectedPlanData.price}"
            else "Unlock ZVPN Premium"

            Button(
                onClick = { onPlanSelected(selectedPlan) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(12.dp, MaterialTheme.shapes.large, spotColor = ZViolet),
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
                            Brush.linearGradient(
                                colors = listOf(ZViolet, ZHotPink, ZLime)
                            ),
                            shape = MaterialTheme.shapes.large,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = ctaText,
                                style = typo.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(Res.drawable.ic_bolt),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // ── Watch Ad CTA (Optional Free Option) ──────────────────────────
            if (onWatchAd != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onWatchAd,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, ZViolet.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = cs.primary,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_bolt),
                            contentDescription = null,
                            tint = ZViolet,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Watch Ad for 30 Min Free Access",
                            style = typo.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onDismiss) {
                Text(
                    text = "Maybe Later",
                    style = typo.labelLarge,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Auto-renewal Legal Note ───────────────────────────────────────
            Text(
                text = "Subscriptions renew automatically until canceled in Google Play Store.\nCancel anytime in Play Store settings.",
                style = typo.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
            )
        }
    }
}

// ─── Feature Data Model & Item Component ─────────────────────────────────────

private data class FeatureItem(
    val iconRes: org.jetbrains.compose.resources.DrawableResource,
    val accentColor: Color,
    val title: String,
    val subtitle: String,
)

@Composable
private fun FeatureRow(item: FeatureItem) {
    val cs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(item.accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = item.accentColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = typo.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Text(
                text = item.subtitle,
                style = typo.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ─── Plan Card Component ──────────────────────────────────────────────────────

@Composable
private fun PlanCard(
    plan: PlanOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    val borderColor by animateColorAsState(
        targetValue = if (selected) ZViolet else cs.outlineVariant.copy(alpha = 0.4f),
        animationSpec = tween(220),
        label = "plan-border",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) cs.primaryContainer.copy(alpha = 0.85f) else cs.surfaceContainerLow,
        animationSpec = tween(220),
        label = "plan-bg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.015f else 1.0f,
        animationSpec = tween(200),
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
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.extraLarge,
            color = bgColor,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
            ),
            tonalElevation = if (selected) 4.dp else 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Radio indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
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
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = plan.title,
                            style = typo.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                        )
                        if (plan.savePct.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ZLime,
                            ) {
                                Text(
                                    text = plan.savePct,
                                    style = typo.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = ZOnLime,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = plan.perDay,
                        style = typo.labelMedium,
                        color = if (selected) cs.onPrimaryContainer.copy(0.75f) else cs.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = plan.price,
                        style = typo.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                    )
                }
            }
        }

        // Floating Top-Right Badge (e.g. Most Popular / Best Value)
        if (plan.badge.isNotEmpty()) {
            val badgeBrush = when {
                plan.isPopular -> Brush.linearGradient(listOf(ZViolet, ZHotPink))
                plan.badge == "Best Value" -> Brush.linearGradient(listOf(ZLime, ZLime))
                else -> Brush.linearGradient(listOf(cs.secondaryContainer, cs.secondaryContainer))
            }
            val textColor = when {
                plan.isPopular -> Color.White
                plan.badge == "Best Value" -> ZOnLime
                else -> cs.onSecondaryContainer
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset(y = (-11).dp)
                    .shadow(4.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBrush)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = plan.badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                )
            }
        }
    }
}

