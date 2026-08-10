package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wheelchair.cockpit.R
import com.wheelchair.cockpit.model.DisplayTheme

/**
 * Top-down vehicle visualization rendered from an SVG asset.
 *
 * The SVG is tinted to the theme color and four lock indicators are overlaid
 * near the doors. Tapping a lock toggles that door's state.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Speed

@Composable
fun VehicleVisualization(
    frontLeftLocked: Boolean,
    frontRightLocked: Boolean,
    rearLeftLocked: Boolean,
    rearRightLocked: Boolean,
    vehicleSpeed: Float,
    vi: Boolean,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    accentGreen: Color,
    theme: DisplayTheme,
    onFrontLeftToggle: () -> Unit,
    onFrontRightToggle: () -> Unit,
    onRearLeftToggle: () -> Unit,
    onRearRightToggle: () -> Unit,
    flPsi: Int = 36,
    frPsi: Int = 36,
    rlPsi: Int = 36,
    rrPsi: Int = 35,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxSize(),
        theme = theme,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: SUV + Locks + PSI
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(R.raw.suv)
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = "Vehicle",
                        colorFilter = ColorFilter.tint(textMain.copy(alpha = 0.9f)),
                        modifier = Modifier.fillMaxSize()
                    )

                    val lockHalf = 8.dp
                    // Locks sit directly on the doors, slightly inside the body line.
                    // Locks sit just outside the door line so they don't overlap the body.
                    DoorLock(
                        locked = frontLeftLocked,
                        primaryColor = primaryColor,
                        textSecondary = textSecondary,
                        onClick = onFrontLeftToggle,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = maxWidth * 0.32f - lockHalf, y = maxHeight * 0.33f - lockHalf)
                    )
                    DoorLock(
                        locked = frontRightLocked,
                        primaryColor = primaryColor,
                        textSecondary = textSecondary,
                        onClick = onFrontRightToggle,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = maxWidth * 0.68f - lockHalf, y = maxHeight * 0.33f - lockHalf)
                    )
                    DoorLock(
                        locked = rearLeftLocked,
                        primaryColor = primaryColor,
                        textSecondary = textSecondary,
                        onClick = onRearLeftToggle,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = maxWidth * 0.32f - lockHalf, y = maxHeight * 0.56f - lockHalf)
                    )
                    DoorLock(
                        locked = rearRightLocked,
                        primaryColor = primaryColor,
                        textSecondary = textSecondary,
                        onClick = onRearRightToggle,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = maxWidth * 0.68f - lockHalf, y = maxHeight * 0.56f - lockHalf)
                    )

                    // Tire Pressures sit just outside each wheel (8dp gap).
                    val psiBlockWidth = 44.dp
                    val psiBlockHalf = 22.dp
                    val psiGap = 8.dp
                    TirePressure(
                        psi = flPsi,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = maxWidth * 0.34f - psiBlockWidth - psiGap,
                                y = maxHeight * 0.28f - psiBlockHalf
                            )
                    )
                    TirePressure(
                        psi = frPsi,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = maxWidth * 0.66f + psiGap,
                                y = maxHeight * 0.28f - psiBlockHalf
                            )
                    )
                    TirePressure(
                        psi = rlPsi,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = maxWidth * 0.34f - psiBlockWidth - psiGap,
                                y = maxHeight * 0.68f - psiBlockHalf
                            )
                    )
                    TirePressure(
                        psi = rrPsi,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = maxWidth * 0.66f + psiGap,
                                y = maxHeight * 0.68f - psiBlockHalf
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Middle: PRND
                PrndSelector(
                    currentGear = "P",
                    textMain = textMain,
                    textSecondary = textSecondary,
                    primaryColor = primaryColor,
                    outlineVariant = outlineVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Bottom: Speed & Battery
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "%.0f km/h".format(vehicleSpeed),
                            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title.copy(fontSize = 22.sp),
                            color = textMain
                        )
                    }
                    
                    // Divider
                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(outlineVariant.copy(alpha = 0.5f)))
                    
                    // Battery
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Rounded.BatteryFull,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "78%",
                            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title.copy(fontSize = 22.sp),
                            color = textMain
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun PrndSelector(
    currentGear: String,
    textMain: Color,
    textSecondary: Color,
    primaryColor: Color,
    outlineVariant: Color
) {
    val gears = listOf("P", "R", "N", "D")
    Row(
        modifier = Modifier
            .background(outlineVariant.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        gears.forEach { gear ->
            val isSelected = gear == currentGear
            Text(
                text = gear,
                style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title.copy(fontSize = 20.sp),
                color = if (isSelected) primaryColor else textSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TirePressure(
    psi: Int,
    textMain: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    val isLow = psi < 34
    val color = if (isLow) Color(0xFFEF4444) else textMain
    Column(
        modifier = modifier.size(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = "$psi",
            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.body.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = "PSI",
            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.label,
            color = textSecondary
        )
    }
}

@Composable
private fun DoorLock(
    locked: Boolean,
    primaryColor: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (locked) primaryColor else textSecondary.copy(alpha = 0.4f)
    Icon(
        imageVector = Icons.Rounded.Lock,
        contentDescription = if (locked) "Locked" else "Unlocked",
        tint = tint,
        modifier = modifier
            .size(16.dp)
            .clickable(onClick = onClick)
    )
}


