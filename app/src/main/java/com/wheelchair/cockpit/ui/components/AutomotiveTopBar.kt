package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AccessibleForward
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutomotiveTopBar(
    vehicleSpeed: Float,
    isHvacOn: Boolean,
    hvacTemp: Float,
    primaryBlue: Color,
    backgroundBg: Color,
    surfaceContainer: Color,
    textMain: Color,
    outlineVariant: Color,
    isDrivingRestricted: Boolean = false,
    onHvacToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = backgroundBg,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.AccessibleForward,
                    contentDescription = null,
                    tint = primaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Wheelchair Copilot",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMain
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed & HVAC Status Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (vehicleSpeed > 80f) Color(0xFFFEE2E2) else surfaceContainer,
                        border = BorderStroke(1.dp, if (vehicleSpeed > 80f) Color(0xFFEF4444) else outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = if (vehicleSpeed > 80f) Color(0xFFEF4444) else primaryBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${"%.0f".format(vehicleSpeed)} km/h",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vehicleSpeed > 80f) Color(0xFFB91C1C) else textMain
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (isHvacOn) Color(0xFFDBEAFE) else surfaceContainer,
                        border = BorderStroke(1.dp, if (isHvacOn) primaryBlue else outlineVariant),
                        modifier = Modifier.clickable(enabled = !isDrivingRestricted) { onHvacToggle() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isHvacOn) Icons.Rounded.AcUnit else Icons.Rounded.Air,
                                contentDescription = null,
                                tint = if (isDrivingRestricted) primaryBlue.copy(alpha = 0.5f) else primaryBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHvacOn) "AC ${hvacTemp}°" else "AC OFF",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDrivingRestricted) textMain.copy(alpha = 0.5f) else textMain
                            )
                        }
                    }
                }

                if (!isDrivingRestricted) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(32.dp)
                            .background(surfaceContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = primaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
