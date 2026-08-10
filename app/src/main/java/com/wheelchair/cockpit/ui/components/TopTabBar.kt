package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.DashboardTab
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitColors

/**
 * Horizontal tab bar for the Dashboard screen sub-sections.
 */
@Composable
fun TopTabBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    theme: DisplayTheme,
    modifier: Modifier = Modifier
) {
    val textMain = CockpitColors.getTextMain(theme)
    val textSecondary = CockpitColors.getTextSecondary(theme)
    val primary = CockpitColors.getPrimaryBlue(theme)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardTab.entries.forEach { tab ->
            TopTabItem(
                tab = tab,
                label = tabLabel(tab),
                selected = selectedTab == tab,
                primaryColor = primary,
                textMain = textMain,
                textSecondary = textSecondary,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun TopTabItem(
    tab: DashboardTab,
    label: String,
    selected: Boolean,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(end = 20.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.body.copy(
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Medium
            ),
            color = if (selected) textMain else textSecondary
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(24.dp)
                    .height(3.dp)
                    .background(primaryColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun tabLabel(tab: DashboardTab): String = when (tab) {
    DashboardTab.CONTROL -> "Control"
    DashboardTab.CLIMATE -> "Climate"
    DashboardTab.NAVIGATION -> "Navigation"
    DashboardTab.AUDIO -> "Audio"
}
