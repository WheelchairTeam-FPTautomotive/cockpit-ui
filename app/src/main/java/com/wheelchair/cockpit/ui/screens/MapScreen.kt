package com.wheelchair.cockpit.ui.screens

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wheelchair.cockpit.R
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitTypography

/**
 * Premium automotive-grade top-down navigation map screen.
 *
 * Theme-aware 2D Canvas map with a layered neon route, SVG ego vehicle with
 * dynamic lighting, stroked street labels, and consistent frosted-glass /
 * soft-shadow floating overlays. No real mapping SDK is used.
 */
@Composable
fun MapScreen(
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    primaryColor: Color,
    surfaceColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    // MODIFIED: glance map; disable control taps while driving
    isDrivingRestricted: Boolean = false,
    onLockedInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = mapPalette(displayTheme)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp)
            .background(palette.background)
            .then(if (isDrivingRestricted) Modifier.alpha(0.85f) else Modifier)
    ) {
        // Map canvas is clipped to this Box so it cannot draw under the sidebar.
        MapLayer(displayTheme, palette)

        MapOverlays(
            appLanguage = appLanguage,
            displayTheme = displayTheme,
            primaryColor = primaryColor,
            textMain = textMain,
            textSecondary = textSecondary,
            isDrivingRestricted = isDrivingRestricted,
            onLockedInteraction = onLockedInteraction
        )
    }
}

@Composable
private fun MapLayer(
    displayTheme: DisplayTheme,
    palette: MapPalette
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }

        val carX = maxWidth * 0.50f
        val carY = maxHeight * 0.82f
        val carSize = maxWidth * 0.10f

        MapCanvas(
            width = w,
            height = h,
            palette = palette,
            modifier = Modifier.fillMaxSize()
        )

        CarMarkerOverlay(
            carX = carX,
            carY = carY,
            carSize = carSize,
            rotation = routeAngleAt(w, h, with(density) { carX.toPx() }, with(density) { carY.toPx() }),
            theme = displayTheme,
            palette = palette
        )
    }
}

@Composable
private fun MapCanvas(
    width: Float,
    height: Float,
    palette: MapPalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        drawMapBackground(width, height, palette)
        drawWaterBody(width, height, palette)
        drawCityBlocks(width, height, palette)
        drawRoads(width, height, palette)
        drawRoute(width, height, palette)
        drawStreetLabels(width, height, palette)
        drawSpeedLimitBadge(width, height, palette)
    }
}

private fun DrawScope.drawMapBackground(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(palette.backgroundCenter, palette.background),
            center = Offset(width * 0.5f, height * 0.5f),
            radius = kotlin.math.max(width, height)
        ),
        size = size
    )
}

private fun DrawScope.drawWaterBody(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    val path = Path().apply {
        moveTo(width * 0.75f, height * -0.05f)
        lineTo(width * 1.05f, height * -0.05f)
        lineTo(width * 1.05f, height * 1.05f)
        lineTo(width * 0.65f, height * 1.05f)
        quadraticBezierTo(
            width * 0.80f, height * 0.70f,
            width * 0.72f, height * 0.45f
        )
        quadraticBezierTo(
            width * 0.65f, height * 0.20f,
            width * 0.75f, height * -0.05f
        )
        close()
    }
    drawPath(path = path, color = palette.water)
}

/**
 * Fills the land cells between roads with block polygons and parks.
 * Edges are inset by half the surrounding road width so the blocks sit
 * flush against the roads without gaps or overlaps.
 */
private fun DrawScope.drawCityBlocks(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    val standardWidth = height * 0.007f
    val majorWidth = height * 0.014f

    val minorVertX = width * 0.24f
    val avenueX = width * 0.50f
    val bridgeX = width * 0.78f

    val minorY1 = height * 0.18f
    val leLoiY = height * 0.40f
    val minorY3 = height * 0.62f
    val minorY4 = height * 0.84f

    // Park between the minor vertical, the major avenue, and two minor streets.
    drawBlock(
        left = minorVertX + standardWidth / 2f,
        top = minorY1 + standardWidth / 2f,
        right = avenueX - majorWidth / 2f,
        bottom = leLoiY - standardWidth / 2f,
        color = palette.park
    )

    // Small park in the lower-left cell.
    drawBlock(
        left = width * 0.05f,
        top = minorY3 + standardWidth / 2f,
        right = minorVertX - standardWidth / 2f,
        bottom = minorY4 - standardWidth / 2f,
        color = palette.park
    )

    // Gray city blocks for the remaining realistic blocks.
    listOf(
        // Between avenue and bridge, above Lê Lợi.
        Rect(
            left = avenueX + majorWidth / 2f,
            top = leLoiY + standardWidth / 2f,
            right = bridgeX - majorWidth / 2f,
            bottom = minorY3 - standardWidth / 2f
        ),
        // Between avenue and bridge, below Lê Lợi.
        Rect(
            left = avenueX + majorWidth / 2f,
            top = minorY3 + standardWidth / 2f,
            right = bridgeX - majorWidth / 2f,
            bottom = minorY4 - standardWidth / 2f
        ),
        // Between minor vertical and avenue, below Lê Lợi.
        Rect(
            left = minorVertX + standardWidth / 2f,
            top = leLoiY + standardWidth / 2f,
            right = avenueX - majorWidth / 2f,
            bottom = minorY3 - standardWidth / 2f
        ),
        // Wide lower-left block.
        Rect(
            left = width * 0.05f,
            top = minorY4 + standardWidth / 2f,
            right = avenueX - majorWidth / 2f,
            bottom = height * 0.97f
        )
    ).forEach { block ->
        drawRect(
            color = palette.block,
            topLeft = Offset(block.left, block.top),
            size = Size(block.width, block.height)
        )
    }
}

private fun DrawScope.drawBlock(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color
) {
    if (right <= left || bottom <= top) return
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top)
    )
}

private enum class RoadType {
    MAJOR,
    STANDARD,
    BRIDGE
}

private data class Road(
    val type: RoadType,
    val start: Offset,
    val end: Offset
)

private fun DrawScope.drawRoads(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    val standardWidth = height * 0.007f
    val majorWidth = height * 0.014f
    val bridgeWidth = height * 0.014f
    val bridgeOutline = height * 0.003f

    val avenueX = width * 0.50f
    val leLoiY = height * 0.40f
    val bridgeX = width * 0.78f
    val minorVertX = width * 0.24f

    val roads = buildList {
        // Major vertical artery.
        add(Road(RoadType.MAJOR, Offset(avenueX, -height * 0.05f), Offset(avenueX, height * 1.05f)))

        // Standard / minor cross streets.
        add(Road(RoadType.STANDARD, Offset(-width * 0.05f, leLoiY), Offset(bridgeX, leLoiY)))
        listOf(0.18f, 0.62f, 0.84f).forEach { yRatio ->
            add(Road(RoadType.STANDARD, Offset(-width * 0.05f, height * yRatio), Offset(bridgeX, height * yRatio)))
        }

        // Minor vertical cross street.
        add(Road(RoadType.STANDARD, Offset(minorVertX, -height * 0.05f), Offset(minorVertX, height * 1.05f)))

        // Bridge / highway on the right.
        add(Road(RoadType.BRIDGE, Offset(bridgeX, height * 0.12f), Offset(bridgeX, height * 0.70f)))
    }

    // Draw bridge outlines first so they sit behind the road fill.
    roads.filter { it.type == RoadType.BRIDGE }.forEach { road ->
        drawLine(
            color = palette.bridgeOutline,
            start = road.start,
            end = road.end,
            strokeWidth = bridgeWidth + bridgeOutline * 2f,
            cap = StrokeCap.Round
        )
    }

    roads.forEach { road ->
        val (color, strokeWidth) = when (road.type) {
            RoadType.MAJOR -> palette.majorRoad to majorWidth
            RoadType.STANDARD -> palette.minorRoad to standardWidth
            RoadType.BRIDGE -> palette.majorRoad to bridgeWidth
        }
        drawLine(
            color = color,
            start = road.start,
            end = road.end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRoute(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    val routePath = routePath(width, height)
    drawNeonRoute(routePath, height, palette)
}

private fun routeNodes(width: Float, height: Float): List<Offset> = listOf(
    Offset(width * 0.50f, height * 1.05f),
    Offset(width * 0.50f, height * 0.40f),
    Offset(width * 0.78f, height * 0.40f),
    Offset(width * 0.78f, height * -0.05f)
)

private fun routePath(width: Float, height: Float): Path {
    val nodes = routeNodes(width, height)
    return Path().apply {
        moveTo(nodes.first().x, nodes.first().y)
        nodes.drop(1).forEach { node ->
            lineTo(node.x, node.y)
        }
    }
}

/**
 * Returns the rotation (in degrees) that aligns an up-pointing marker
 * with the route direction at the supplied screen position.
 */
private fun routeAngleAt(width: Float, height: Float, x: Float, y: Float): Float {
    val nodes = routeNodes(width, height)
    val tolerance = kotlin.math.max(width, height) * 0.03f
    for (i in 0 until nodes.lastIndex) {
        val a = nodes[i]
        val b = nodes[i + 1]
        if (pointOnSegment(a, b, x, y, tolerance)) {
            val dx = b.x - a.x
            val dy = b.y - a.y
            return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        }
    }
    return 0f
}

private fun pointOnSegment(a: Offset, b: Offset, x: Float, y: Float, tolerance: Float): Boolean {
    val minX = kotlin.math.min(a.x, b.x) - tolerance
    val maxX = kotlin.math.max(a.x, b.x) + tolerance
    val minY = kotlin.math.min(a.y, b.y) - tolerance
    val maxY = kotlin.math.max(a.y, b.y) + tolerance
    return x in minX..maxX && y in minY..maxY
}

private fun DrawScope.drawNeonRoute(
    path: Path,
    height: Float,
    palette: MapPalette
) {
    val androidPath = path.asAndroidPath()

    // Soft, blurred glow / shadow base.
    val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = height * 0.045f
        color = palette.routeGlow.toArgb()
        strokeCap = Paint.Cap.BUTT
        maskFilter = BlurMaskFilter(height * 0.032f, BlurMaskFilter.Blur.NORMAL)
    }

    // Bright electric-blue top line with hard corners.
    val topPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = height * 0.010f
        color = palette.route.toArgb()
        strokeCap = Paint.Cap.BUTT
    }

    drawContext.canvas.nativeCanvas.drawPath(androidPath, glowPaint)
    drawContext.canvas.nativeCanvas.drawPath(androidPath, topPaint)
}

private fun DrawScope.drawStreetLabels(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    val density = drawContext.density
    val textSizePx = with(density) { 16.sp.toPx() }
    val strokeWidthPx = with(density) { 2.dp.toPx() }

    val paint = Paint().apply {
        isAntiAlias = true
        textSize = textSizePx
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    fun drawLabel(text: String, x: Float, y: Float, rotation: Float) {
        val metrics = paint.fontMetrics
        val offsetY = -(metrics.ascent + metrics.descent) / 2f

        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(x, y)
            rotate(rotation)

            paint.color = palette.labelStroke.toArgb()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidthPx
            drawText(text, 0f, offsetY, paint)

            paint.color = palette.label.toArgb()
            paint.style = Paint.Style.FILL
            drawText(text, 0f, offsetY, paint)

            restore()
        }
    }

    val majorWidth = height * 0.014f
    val standardWidth = height * 0.007f
    val bridgeWidth = height * 0.014f

    // Labels run parallel to their roads and sit just off the road surface.
    drawLabel("Đại lộ Nguyễn Huệ", width * 0.50f - majorWidth * 0.65f, height * 0.30f, 90f)
    drawLabel("Đường Lê Lợi", width * 0.37f, height * 0.40f - standardWidth * 0.75f, 0f)
    drawLabel("Cầu Thủ Thiêm", width * 0.78f + bridgeWidth * 0.65f, height * 0.42f, -90f)
}

private fun DrawScope.drawSpeedLimitBadge(
    width: Float,
    height: Float,
    palette: MapPalette
) {
    val majorWidth = height * 0.014f
    val center = Offset(width * 0.50f + majorWidth * 0.35f, height * 0.45f)
    val radius = height * 0.036f

    drawCircle(color = Color(0xFFDC2626), radius = radius, center = center)
    drawCircle(color = Color.White, radius = radius * 0.78f, center = center)

    val paint = Paint().apply {
        isAntiAlias = true
        color = Color.Black.toArgb()
        textSize = radius * 1.1f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val metrics = paint.fontMetrics
    val offsetY = -(metrics.ascent + metrics.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText("40", center.x, center.y + offsetY, paint)
}

@Composable
private fun CarMarkerOverlay(
    carX: Dp,
    carY: Dp,
    carSize: Dp,
    rotation: Float,
    theme: DisplayTheme,
    palette: MapPalette
) {
    Box(
        modifier = Modifier
            .size(carSize)
            .offset(x = carX - carSize / 2, y = carY - carSize / 2)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Soft red radial glow behind the rear (taillights).
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEF4444).copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height * 0.82f),
                    radius = size.width * 0.52f
                )
            )

            // White/yellow headlight cone in front of the vehicle.
            val conePath = Path().apply {
                moveTo(size.width * 0.36f, size.height * 0.32f)
                lineTo(size.width * 0.64f, size.height * 0.32f)
                lineTo(size.width * 1.10f, -size.height * 0.42f)
                lineTo(-size.width * 0.10f, -size.height * 0.42f)
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFBE6).copy(alpha = 0.42f),
                        Color.Transparent
                    ),
                    startY = size.height * 0.32f,
                    endY = -size.height * 0.42f
                )
            )
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(R.raw.suv)
                .decoderFactory(SvgDecoder.Factory())
                .build(),
            contentDescription = "Ego vehicle",
            colorFilter = ColorFilter.tint(palette.carBody),
            modifier = Modifier.fillMaxSize(0.85f)
        )
    }
}

@Composable
private fun MapOverlays(
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    isDrivingRestricted: Boolean,
    onLockedInteraction: () -> Unit
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            DirectionBanner(
                instruction = if (vi) "Rẽ phải tại Đại lộ Nguyễn Huệ" else "Turn right at Đại lộ Nguyễn Huệ",
                distance = "250m",
                primaryColor = primaryColor,
                textMain = textMain,
                textSecondary = textSecondary,
                theme = displayTheme
            )
            SearchBar(
                hint = if (vi) "Tìm kiếm điểm đến..." else "Search destination...",
                textMain = textMain,
                textSecondary = textSecondary,
                theme = displayTheme,
                isDrivingRestricted = isDrivingRestricted,
                onLockedInteraction = onLockedInteraction
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            MapControls(
                textMain = textMain,
                theme = displayTheme,
                isDrivingRestricted = isDrivingRestricted,
                onLockedInteraction = onLockedInteraction
            )
        }
    }
}

@Composable
private fun DirectionBanner(
    instruction: String,
    distance: String,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    theme: DisplayTheme
) {
    MapOverlaySurface(
        theme = theme,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.width(320.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column {
                Text(
                    text = instruction,
                    style = CockpitTypography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = textMain,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "— $distance",
                    style = CockpitTypography.caption,
                    color = textSecondary
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    hint: String,
    textMain: Color,
    textSecondary: Color,
    theme: DisplayTheme,
    isDrivingRestricted: Boolean = false,
    onLockedInteraction: () -> Unit = {}
) {
    MapOverlaySurface(
        theme = theme,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .width(260.dp)
                .clickable(enabled = isDrivingRestricted, onClick = onLockedInteraction)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = hint,
                style = CockpitTypography.body,
                color = textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MapControls(
    textMain: Color,
    theme: DisplayTheme,
    isDrivingRestricted: Boolean = false,
    onLockedInteraction: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        MapControlButton(
            icon = Icons.Rounded.NearMe,
            contentDescription = "Recenter",
            textMain = textMain,
            theme = theme,
            onClick = { if (isDrivingRestricted) onLockedInteraction() }
        )
        Column {
            MapControlButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Zoom in",
                textMain = textMain,
                theme = theme,
                onClick = { if (isDrivingRestricted) onLockedInteraction() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            MapControlButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Zoom out",
                textMain = textMain,
                theme = theme,
                onClick = { if (isDrivingRestricted) onLockedInteraction() }
            )
        }
        MapControlButton(
            icon = Icons.Rounded.MyLocation,
            contentDescription = "Locate",
            textMain = textMain,
            theme = theme,
            onClick = { if (isDrivingRestricted) onLockedInteraction() }
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    textMain: Color,
    theme: DisplayTheme,
    onClick: () -> Unit = {}
) {
    MapOverlaySurface(
        theme = theme,
        shape = CircleShape,
        modifier = Modifier.size(48.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = textMain,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Map-specific overlay surface.
 *
 * Dark mode uses a dark, semi-transparent frosted-glass panel with a subtle
 * border. Light mode uses a solid white card with a soft, wide shadow.
 */
@Composable
private fun MapOverlaySurface(
    theme: DisplayTheme,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isLight = theme == DisplayTheme.LIGHT

    Surface(
        modifier = modifier,
        shape = shape,
        color = if (isLight) Color.White else Color(0xFF1B1E24).copy(alpha = 0.72f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isLight) {
                Color(0xFFD0D5DD).copy(alpha = 0.65f)
            } else {
                Color.White.copy(alpha = 0.12f)
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (isLight) 10.dp else 0.dp,
        content = content
    )
}

private data class MapPalette(
    val background: Color,
    val backgroundCenter: Color,
    val minorRoad: Color,
    val majorRoad: Color,
    val water: Color,
    val park: Color,
    val block: Color,
    val route: Color,
    val routeGlow: Color,
    val label: Color,
    val labelStroke: Color,
    val bridgeOutline: Color,
    val carBody: Color
)

private fun mapPalette(theme: DisplayTheme): MapPalette = when (theme) {
    DisplayTheme.LIGHT -> MapPalette(
        background = Color(0xFFECEFF3),
        backgroundCenter = Color(0xFFF4F5F7),
        minorRoad = Color(0xFFFFFFFF),
        majorRoad = Color(0xFFE2E6EA),
        water = Color(0xFFD6E4F0),
        park = Color(0xFFDDF0E5),
        block = Color(0xFFDEE2E8),
        route = Color(0xFF00A3FF),
        routeGlow = Color(0xFF00A3FF).copy(alpha = 0.28f),
        label = Color(0xFF1F2937),
        labelStroke = Color.White,
        bridgeOutline = Color(0xFF6B7280),
        carBody = Color(0xFF334155)
    )

    DisplayTheme.DARK,
    DisplayTheme.CENTRAL -> MapPalette(
        background = Color(0xFF0F1419),
        backgroundCenter = Color(0xFF1A233A),
        minorRoad = Color(0xFF1E2532),
        majorRoad = Color(0xFF2A3447),
        water = Color(0xFF0D1B33),
        park = Color(0xFF0F2E22),
        block = Color(0xFF131920),
        route = Color(0xFF33C1FF),
        routeGlow = Color(0xFF00A3FF).copy(alpha = 0.45f),
        label = Color(0xFFF1F5F9),
        labelStroke = Color(0xFF000000),
        bridgeOutline = Color.Black,
        carBody = Color(0xFFE2E8F0)
    )
}
