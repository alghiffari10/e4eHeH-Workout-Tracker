package com.e4eheh.e4ehehworkout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.ui.navigation.TopLevelTab
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes

/** 4-tab Batcomputer bottom navigation bar with a centered chamfered + action button. */
@Composable
fun ArkhamBottomNavBar(
    currentRoute: String?,
    onSelectTab: (TopLevelTab) -> Unit,
    onQuickActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ArkhamColors.ObsidianPanel,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = ArkhamColors.TerminalEmerald)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabButton(
                    tab = TopLevelTab.HOME,
                    icon = ArkhamIcons.Home,
                    selected = currentRoute == TopLevelTab.HOME.route,
                    onClick = { onSelectTab(TopLevelTab.HOME) },
                    modifier = Modifier.weight(1f),
                )
                TabButton(
                    tab = TopLevelTab.WORKOUT,
                    icon = ArkhamIcons.Workout,
                    selected = currentRoute == TopLevelTab.WORKOUT.route,
                    onClick = { onSelectTab(TopLevelTab.WORKOUT) },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .offset(y = (-6).dp)
                            .size(50.dp)
                            .testTag("nav_fab_add")
                            .clickable(
                                role = Role.Button,
                                onClick = onQuickActionClick,
                            ),
                        shape = ArkhamShapes.fab,
                        color = ArkhamColors.PhosphorGreen,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, ArkhamColors.MintWhite.copy(alpha = 0.4f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = ArkhamIcons.Add,
                                contentDescription = "Quick Action",
                                tint = ArkhamColors.OnPhosphor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                TabButton(
                    tab = TopLevelTab.PROGRESS,
                    icon = ArkhamIcons.Progress,
                    selected = currentRoute == TopLevelTab.PROGRESS.route,
                    onClick = { onSelectTab(TopLevelTab.PROGRESS) },
                    modifier = Modifier.weight(1f),
                )
                TabButton(
                    tab = TopLevelTab.PROFILE,
                    icon = ArkhamIcons.Profile,
                    selected = currentRoute == TopLevelTab.PROFILE.route,
                    onClick = { onSelectTab(TopLevelTab.PROFILE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    tab: TopLevelTab,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) ArkhamColors.PhosphorGreen else ArkhamColors.MintMuted,
        animationSpec = tween(durationMillis = ArkhamMotion.FAST_MS),
        label = "navTabTint",
    )

    Column(
        modifier = modifier
            .testTag(tab.testTag)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}
