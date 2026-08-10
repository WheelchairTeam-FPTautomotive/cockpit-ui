package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.DisplayTheme

@Composable
fun TripEfficiencyModule(
    theme: DisplayTheme,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        theme = theme,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip & Efficiency",
                        style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title,
                        color = textMain
                    )
                    Icon(
                        imageVector = Icons.Rounded.ElectricCar,
                        contentDescription = "Efficiency",
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Content grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left stat
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EvStation, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Since Charge", style = com.wheelchair.cockpit.ui.theme.CockpitTypography.label, color = textSecondary)
                        }
                        Text("18.4 kWh/100km", style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title, color = textMain)
                        Text("124 km driven", style = com.wheelchair.cockpit.ui.theme.CockpitTypography.body, color = textSecondary)
                    }
                    
                    // Right stat
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Current Trip", style = com.wheelchair.cockpit.ui.theme.CockpitTypography.label, color = textSecondary)
                        }
                        Text("15.2 kWh/100km", style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title, color = textMain)
                        Text("32 km driven", style = com.wheelchair.cockpit.ui.theme.CockpitTypography.body, color = textSecondary)
                    }
                }
            }
        }
    )
}
