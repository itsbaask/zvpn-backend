@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zvpn.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zvpn.domain.model.NavBarStyle
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.ic_bolt
import com.zvpn.ui.onboarding.TourTargets
import com.zvpn.ui.onboarding.tourTarget
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ZedNavBar(
    style: NavBarStyle,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    onOpenPremium: (() -> Unit)? = null,
) {
    when (style) {
        NavBarStyle.FloatingPill -> FloatingPillBar(current, onSelect, compact = false, onOpenPremium = onOpenPremium)
        NavBarStyle.CompactDock -> FloatingPillBar(current, onSelect, compact = true, onOpenPremium = onOpenPremium)
        NavBarStyle.ExpressivePill -> ExpressivePillBar(current, onSelect, onOpenPremium = onOpenPremium)
        NavBarStyle.FullBar -> MaterialBar(current, onSelect, underline = false, onOpenPremium = onOpenPremium)
        NavBarStyle.Underline -> MaterialBar(current, onSelect, underline = true, onOpenPremium = onOpenPremium)
        NavBarStyle.Minimal -> MinimalBar(current, onSelect, onOpenPremium = onOpenPremium)
    }
}

@Composable
private fun FloatingPillBar(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    compact: Boolean,
    onOpenPremium: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        ) {
            TopDestination.entries.forEach { dest ->
                NavPill(
                    destination = dest,
                    selected = current == dest,
                    compact = compact,
                    onClick = { onSelect(dest) },
                    modifier = Modifier.tourTarget(TourTargets.nav(dest.name)),
                )
            }

            if (onOpenPremium != null) {
                GenericNavPill(
                    iconRes = Res.drawable.ic_bolt,
                    label = "Premium",
                    selected = false,
                    compact = compact,
                    onClick = onOpenPremium,
                    isPremium = true,
                )
            }
        }
    }
}

@Composable
private fun ExpressivePillBar(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    onOpenPremium: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopDestination.entries.forEach { dest ->
                    val selected = current == dest
                    val bg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        label = "exp-bg",
                    )
                    val fg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "exp-fg",
                    )
                    Surface(
                        onClick = { onSelect(dest) },
                        shape = CircleShape,
                        color = bg,
                        contentColor = fg,
                        modifier = Modifier.tourTarget(TourTargets.nav(dest.name)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(dest.icon),
                                contentDescription = stringResource(dest.labelRes),
                                modifier = Modifier.size(22.dp),
                            )

                            AnimatedContent(selected, label = "exp-label") { show ->
                                if (show) {
                                    Row {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(0.dp))
                                }
                            }
                        }
                    }
                }

                if (onOpenPremium != null) {
                    IconButton(onClick = onOpenPremium) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_bolt),
                            contentDescription = "Premium",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialBar(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    underline: Boolean,
    onOpenPremium: (() -> Unit)? = null,
) {
    NavigationBar {
        TopDestination.entries.forEach { dest ->
            val selected = current == dest
            NavigationBarItem(
                modifier = Modifier.tourTarget(TourTargets.nav(dest.name)),
                selected = selected,
                onClick = { onSelect(dest) },
                icon = {
                    if (underline) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .width(24.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                            )
                            Spacer(Modifier.height(6.dp))
                            Icon(painterResource(dest.icon), contentDescription = null)
                        }
                    } else {
                        Icon(painterResource(dest.icon), contentDescription = null)
                    }
                },
                label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                colors = if (underline) {
                    androidx.compose.material3.NavigationBarItemDefaults.colors(
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                } else {
                    androidx.compose.material3.NavigationBarItemDefaults.colors()
                },
            )
        }

        if (onOpenPremium != null) {
            NavigationBarItem(
                selected = false,
                onClick = onOpenPremium,
                icon = {
                    Icon(
                        painterResource(Res.drawable.ic_bolt),
                        contentDescription = "Premium",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        "Premium",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun MinimalBar(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    onOpenPremium: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopDestination.entries.forEach { dest ->
            val selected = current == dest
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "min-tint",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.tourTarget(TourTargets.nav(dest.name)),
            ) {
                IconButton(onClick = { onSelect(dest) }) {
                    Icon(
                        painterResource(dest.icon),
                        contentDescription = stringResource(dest.labelRes),
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                        ),
                )
            }
        }

        if (onOpenPremium != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onOpenPremium) {
                    Icon(
                        painterResource(Res.drawable.ic_bolt),
                        contentDescription = "Premium",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(Modifier.size(5.dp))
            }
        }
    }
}

@Composable
private fun NavPill(
    destination: TopDestination,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconSize by animateDpAsState(
        targetValue = when {
            compact -> if (selected) 21.dp else 19.dp
            selected -> 25.dp
            else -> 22.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "nav-icon-size",
    )
    val icon = @Composable {
        Icon(
            painter = painterResource(destination.icon),
            contentDescription = stringResource(destination.labelRes),
            modifier = Modifier.size(iconSize),
        )
    }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (scaleIn(initialScale = 0.85f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.85f) + fadeOut())
        },
        label = "nav-pill",
        modifier = modifier,
    ) { isSelected ->
        val sizeMod = if (compact) Modifier.size(38.dp) else Modifier
        if (isSelected) {
            FilledIconButton(onClick = onClick, modifier = sizeMod) { icon() }
        } else {
            IconButton(onClick = onClick, modifier = sizeMod) { icon() }
        }
    }
}

@Composable
private fun GenericNavPill(
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
) {
    val iconSize by animateDpAsState(
        targetValue = when {
            compact -> if (selected) 21.dp else 19.dp
            selected -> 25.dp
            else -> 22.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "generic-icon-size",
    )
    val icon = @Composable {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (isPremium && !selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (scaleIn(initialScale = 0.85f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.85f) + fadeOut())
        },
        label = "generic-pill",
        modifier = modifier,
    ) { isSelected ->
        val sizeMod = if (compact) Modifier.size(38.dp) else Modifier
        if (isSelected) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isPremium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isPremium) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    icon()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        } else {
            IconButton(onClick = onClick, modifier = sizeMod) { icon() }
        }
    }
}
