package net.koalastuff.koalashelf.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CoverMotif {
    SUN,
    STRIPES,
    RINGS,
    EYES,
    DIAGONAL,
    ARCS,
    MOON,
}

data class CoverArtPalette(
    val bg: Color,
    val fg: Color,
    val ink: Color,
    val motif: CoverMotif,
    val alt: Color,
)

private val COVER_PALETTES = listOf(
    CoverArtPalette(Color(0xFF2B4A7A), Color(0xFFF2B544), Color(0xFFF1E8DC), CoverMotif.SUN, Color(0xFF13203F)),
    CoverArtPalette(Color(0xFF11183A), Color(0xFFF2C14E), Color(0xFFF2C14E), CoverMotif.STRIPES, Color(0xFFF1E8DC)),
    CoverArtPalette(Color(0xFF8FB3C4), Color(0xFF6F98AB), Color(0xFF13203F), CoverMotif.RINGS, Color(0xFFB9D0DA)),
    CoverArtPalette(Color(0xFFAE452E), Color(0xFFF7EFE6), Color(0xFFF7EFE6), CoverMotif.EYES, Color(0xFF1E222B)),
    CoverArtPalette(Color(0xFF3E4A2E), Color(0xFFE2C25A), Color(0xFFF7EFE6), CoverMotif.RINGS, Color(0xFF3E4A2E)),
    CoverArtPalette(Color(0xFF22533A), Color(0xFF6E9A80), Color(0xFFF1E8DC), CoverMotif.DIAGONAL, Color(0xFFF1E8DC)),
    CoverArtPalette(Color(0xFFF3E3CF), Color(0xFFEF6F4F), Color(0xFF5A2A1A), CoverMotif.ARCS, Color(0xFFF7A88A)),
    CoverArtPalette(Color(0xFF4B3FB8), Color(0xFF8C7CF0), Color(0xFFF1E8DC), CoverMotif.RINGS, Color(0xFF4B3FB8)),
    CoverArtPalette(Color(0xFFDCE8F2), Color(0xFF3C6FA8), Color(0xFF1E3A5A), CoverMotif.DIAGONAL, Color(0xFF1E222B)),
    CoverArtPalette(Color(0xFF1E3A4A), Color(0xFFF1E8DC), Color(0xFFF1E8DC), CoverMotif.MOON, Color(0xFF1E3A4A)),
)

/**
 * Deterministic FNV-1a palette & geometric motif generator matching `apps/web/src/lib/cover-art.ts`.
 */
fun coverArtForSeed(seed: String): CoverArtPalette {
    var h = 0x811c9dc5u
    for (i in seed.indices) {
        h = (h xor seed[i].code.toUInt()) * 0x01000193u
    }
    val idx = (h % COVER_PALETTES.size.toUInt()).toInt()
    return COVER_PALETTES[idx]
}

/**
 * Square audiobook cover with Coil remote/local image loading and geometric + typographic fallback
 * matching `apps/web/src/lib/components/Cover.svelte`.
 */
@Composable
fun BookCover(
    title: String,
    author: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = KoalaRadius.cover,
    titleTop: Boolean = false,
    bare: Boolean = false,
    progress: Float = 0f,
    isFinished: Boolean = false,
) {
    val colors = KoalaTheme.colors
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(colors.card),
    ) {
        TypographicCoverFallback(
            title = title,
            author = author,
            titleTop = titleTop,
            bare = bare,
        )

        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isFinished) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .background(Color(0xFFD4A640)),
            )
        } else if (progress > 0.01f && progress < 0.99f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(colors.accent),
                )
            }
        }
    }
}

@Composable
private fun TypographicCoverFallback(
    title: String,
    author: String,
    titleTop: Boolean,
    bare: Boolean = false,
) {
    val art = remember(title, author) { coverArtForSeed("$title|$author") }
    val typography = KoalaTheme.typography

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(art.bg),
    ) {
        // Geometric SVG motif layer (`Cover.svelte` viewBox 0..100)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (art.motif) {
                CoverMotif.SUN -> {
                    drawCircle(
                        color = art.fg,
                        radius = w * 0.12f,
                        center = Offset(w * 0.66f, h * 0.34f),
                    )
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, h * 0.74f)
                        quadraticTo(w * 0.30f, h * 0.58f, w * 0.55f, h * 0.70f)
                        quadraticTo(w * 0.80f, h * 0.82f, w, h * 0.66f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path = path, color = art.alt)
                }
                CoverMotif.STRIPES -> {
                    drawCircle(
                        color = art.alt,
                        radius = w * 0.06f,
                        center = Offset(w * 0.24f, h * 0.26f),
                    )
                    drawLine(
                        color = art.fg,
                        start = Offset(0f, h * 0.58f),
                        end = Offset(w, h * 0.58f),
                        strokeWidth = h * 0.07f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(w * 0.04f, w * 0.03f),
                            0f,
                        ),
                    )
                }
                CoverMotif.RINGS -> {
                    val center = Offset(w * 0.50f, h * 0.50f)
                    for (i in 0 until 11) {
                        drawCircle(
                            color = art.fg,
                            radius = w * (0.04f + i * 0.07f),
                            center = center,
                            style = Stroke(width = w * 0.03f),
                        )
                    }
                }
                CoverMotif.EYES -> {
                    val cy = h * 0.50f
                    drawCircle(color = art.fg, radius = w * 0.11f, center = Offset(w * 0.36f, cy))
                    drawCircle(color = art.fg, radius = w * 0.11f, center = Offset(w * 0.64f, cy))
                    drawCircle(color = art.alt, radius = w * 0.045f, center = Offset(w * 0.38f, cy))
                    drawCircle(color = art.alt, radius = w * 0.045f, center = Offset(w * 0.66f, cy))
                }
                CoverMotif.DIAGONAL -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w, 0f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path = path, color = art.fg)
                    drawCircle(
                        color = art.alt,
                        radius = w * 0.06f,
                        center = Offset(w * 0.28f, h * 0.32f),
                    )
                }
                CoverMotif.ARCS -> {
                    val center = Offset(w * 0.50f, h * 1.04f)
                    drawCircle(color = art.alt, radius = w * 0.46f, center = center)
                    drawCircle(color = art.fg, radius = w * 0.30f, center = center)
                }
                CoverMotif.MOON -> {
                    drawCircle(color = art.fg, radius = w * 0.13f, center = Offset(w * 0.68f, h * 0.34f))
                    drawCircle(color = art.bg, radius = w * 0.12f, center = Offset(w * 0.62f, h * 0.30f))
                }
            }
        }

        // Thumbnails (`@container (max-width: 96px)` in Cover.svelte): motif alone identifies the placeholder
        if (!bare && maxWidth > 96.dp) {
            val w = maxWidth.value.coerceAtLeast(96f)
            val pad = (w * 0.065f).dp
            val authorFontSize = (w * 0.055f).coerceIn(8f, 13f).sp
            val titleFontSize = (w * 0.115f).coerceIn(12f, 24f).sp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
            ) {
                if (author.isNotBlank()) {
                    Text(
                        text = author.uppercase(),
                        color = art.ink,
                        style = typography.label.copy(fontSize = authorFontSize),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                if (!titleTop) {
                    Text(
                        text = title,
                        color = art.ink,
                        style = typography.size18.copy(
                            fontSize = titleFontSize,
                            lineHeight = (titleFontSize.value * 1.02f).sp,
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }
    }
}

/**
 * Circular author/narrator portrait with deterministic FNV-1a palette and 2-letter initials fallback
 * matching `apps/web/src/lib/components/Portrait.svelte`.
 */
@Composable
fun AuthorPortrait(
    name: String,
    imageUrl: String? = null,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
) {
    val art = remember(name) { coverArtForSeed(name) }
    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) "?"
        else if (parts.size == 1) parts[0].take(2).uppercase()
        else "${parts.first().first()}${parts.last().first()}".uppercase()
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(art.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = art.ink,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Bold,
        )

        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

data class MosaicCoverItem(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
)

/**
 * 2x2 cover collage for playlists & collections matching `apps/web/src/lib/components/CoverMosaic.svelte`.
 */
@Composable
fun CoverMosaic(
    items: List<MosaicCoverItem>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = KoalaRadius.card,
) {
    val colors = KoalaTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            items.size >= 4 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (i in 0..1) {
                            val it = items[i]
                            BookCover(
                                title = it.title,
                                author = it.author,
                                coverUrl = it.coverUrl,
                                bare = true,
                                cornerRadius = 0.dp,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (i in 2..3) {
                            val it = items[i]
                            BookCover(
                                title = it.title,
                                author = it.author,
                                coverUrl = it.coverUrl,
                                bare = true,
                                cornerRadius = 0.dp,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            items.isNotEmpty() -> {
                val first = items.first()
                BookCover(
                    title = first.title,
                    author = first.author,
                    coverUrl = first.coverUrl,
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Text(
                    text = "♪",
                    color = colors.muted,
                    fontSize = 28.sp,
                )
            }
        }
    }
}

/**
 * Spinning vinyl disc with radial grooves, chapter rim ticks, stationary light sheen,
 * cover label, spindle hole, and gold record finish (`apps/web/src/lib/components/Vinyl.svelte`).
 */
@Composable
fun VinylDisc(
    title: String,
    author: String = "",
    coverUrl: String? = null,
    playing: Boolean = false,
    marks: List<Float> = emptyList(),
    labelRatio: Float = 0.40f,
    speed: Float = 1.0f,
    gold: Boolean = false,
    shineTrigger: Int = 0,
    extraRotationDeg: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val spinEnabled = KoalaTheme.spinEnabled
    val reducedMotion = KoalaTheme.reducedMotion

    var rotationDeg by remember { mutableFloatStateOf(0f) }
    var currentMotorSpeed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing, speed, spinEnabled) {
        if (!spinEnabled) return@LaunchedEffect
        var lastMs = withFrameMillis { it }
        while (isActive) {
            val nowMs = withFrameMillis { it }
            val dtMs = (nowMs - lastMs).coerceIn(0L, 64L).toFloat()
            lastMs = nowMs

            val targetRate = if (playing) speed.coerceIn(0.5f, 3.5f) else 0f
            val rampStep = dtMs / 900f
            currentMotorSpeed = if (currentMotorSpeed < targetRate) {
                min(targetRate, currentMotorSpeed + rampStep * max(1f, targetRate))
            } else {
                max(targetRate, currentMotorSpeed - rampStep * max(1f, currentMotorSpeed))
            }

            if (currentMotorSpeed > 0.001f) {
                val degPerMs = 360f / KoalaMotion.SPIN_MS.toFloat()
                rotationDeg = (rotationDeg + dtMs * degPerMs * currentMotorSpeed) % 360f
            } else if (!playing) {
                break
            }
        }
    }

    val shineProgress = remember { Animatable(0f) }
    LaunchedEffect(shineTrigger) {
        if (shineTrigger > 0 && !reducedMotion) {
            shineProgress.snapTo(0f)
            shineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            )
        }
    }

    val grooveA = if (gold) Color(0xFF6E5216) else colors.vinylA
    val grooveB = if (gold) Color(0xFFA8832C) else colors.vinylB
    val tickColor = if (gold) Color(0xB3FFF4D2) else Color(0x8CFFFFFF)

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(elevation = 14.dp, shape = CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val discSize = maxWidth
        val labelSize = discSize * labelRatio.coerceIn(0.28f, 0.52f)
        val spindleSize = discSize * 0.055f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = grooveA, radius = radius, center = center)

            val ringCount = 22
            val minGrooveR = radius * (labelRatio + 0.04f)
            val grooveSpan = radius * 0.96f - minGrooveR
            for (i in 0 until ringCount) {
                val r = minGrooveR + (grooveSpan * i / ringCount.toFloat())
                drawCircle(
                    color = if (i % 2 == 0) grooveB else grooveA,
                    radius = r,
                    center = center,
                    style = Stroke(width = (grooveSpan / ringCount) * 0.55f),
                )
            }

            rotate(degrees = rotationDeg + extraRotationDeg, pivot = center) {
                val innerTickR = radius * 0.91f
                val outerTickR = radius * 0.97f
                marks.forEach { fraction ->
                    val angleRad = (fraction * 360f - 90f) * (PI.toFloat() / 180f)
                    val start = Offset(
                        x = center.x + cos(angleRad) * innerTickR,
                        y = center.y + sin(angleRad) * innerTickR,
                    )
                    val end = Offset(
                        x = center.x + cos(angleRad) * outerTickR,
                        y = center.y + sin(angleRad) * outerTickR,
                    )
                    drawLine(
                        color = tickColor,
                        start = start,
                        end = end,
                        strokeWidth = max(2f, radius * 0.015f),
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Stationary light sheen over the spinning grooves
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = -55f,
                sweepAngle = 42f,
                useCenter = true,
                topLeft = Offset.Zero,
                size = size,
            )
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = 125f,
                sweepAngle = 42f,
                useCenter = true,
                topLeft = Offset.Zero,
                size = size,
            )

            if (shineProgress.value > 0f && shineProgress.value < 1f) {
                val xOffset = size.width * (1f - shineProgress.value * 1.3f)
                drawLine(
                    color = Color(0xCCFFF0C8),
                    start = Offset(xOffset, 0f),
                    end = Offset(xOffset + size.width * 0.35f, size.height),
                    strokeWidth = radius * 0.25f,
                )
            }
        }

        // Rotating center label using graphicsLayer lambda so composition is not invalidated on every frame
        Box(
            modifier = Modifier
                .size(labelSize)
                .graphicsLayer {
                    rotationZ = rotationDeg + extraRotationDeg
                }
                .clip(CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
        ) {
            BookCover(
                title = title,
                author = author,
                coverUrl = coverUrl,
                cornerRadius = 0.dp,
                titleTop = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Center spindle hole
        Box(
            modifier = Modifier
                .size(spindleSize)
                .clip(CircleShape)
                .background(colors.bg)
                .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
        )
    }
}

/**
 * Interactive Turntable deck with platter, spinning VinylDisc, tonearm that tracks book progress,
 * and circular touch DJ scratching (`apps/web/src/lib/components/Turntable.svelte`).
 */
@Composable
fun TurntableDeck(
    title: String,
    author: String = "",
    coverUrl: String? = null,
    playing: Boolean = false,
    marks: List<Float> = emptyList(),
    speed: Float = 1.0f,
    gold: Boolean = false,
    shineTrigger: Int = 0,
    progress: Float = 0f,
    interactive: Boolean = false,
    onScratchStart: () -> Unit = {},
    onScratchDeltaSeconds: (Float) -> Unit = {},
    onScratchEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography
    val haptics = LocalHapticFeedback.current

    var isScratching by remember { mutableStateOf(false) }
    var scratchRotateDeg by remember { mutableFloatStateOf(0f) }
    var totalScratchSeconds by remember { mutableFloatStateOf(0f) }
    var platterSize by remember { mutableStateOf(IntSize.Zero) }

    val targetArmAngle = if (playing || isScratching) {
        KoalaMotion.TONEARM_BASE_DEG + progress.coerceIn(0f, 1f) * KoalaMotion.TONEARM_RANGE_DEG
    } else {
        4f
    }
    val armAngle by animateFloatAsState(
        targetValue = targetArmAngle,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "tonearmAngle",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(KoalaRadius.cardLg))
            .background(colors.platter),
    ) {
        // Platter area (left 6%, top 9%, width 82%)
        Box(
            modifier = Modifier
                .fillMaxSize(0.82f)
                .align(Alignment.CenterStart)
                .offset(x = 12.dp, y = 4.dp)
                .onSizeChanged { platterSize = it }
                .then(
                    if (interactive) {
                        Modifier.pointerInput(Unit) {
                            var lastAngle = 0f
                            var hapticAccum = 0f
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val cx = platterSize.width / 2f
                                    val cy = platterSize.height / 2f
                                    lastAngle = atan2(offset.y - cy, offset.x - cx)
                                    hapticAccum = 0f
                                    totalScratchSeconds = 0f
                                    scratchRotateDeg = 0f
                                    isScratching = true
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScratchStart()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val cx = platterSize.width / 2f
                                    val cy = platterSize.height / 2f
                                    val curAngle = atan2(change.position.y - cy, change.position.x - cx)
                                    var delta = curAngle - lastAngle
                                    while (delta > PI.toFloat()) delta -= 2f * PI.toFloat()
                                    while (delta < -PI.toFloat()) delta += 2f * PI.toFloat()
                                    lastAngle = curAngle

                                    val deltaDeg = delta * (180f / PI.toFloat())
                                    scratchRotateDeg += deltaDeg
                                    hapticAccum += deltaDeg

                                    // 360 degrees = 30 seconds of audio
                                    val dtSeconds = deltaDeg * (30f / 360f)
                                    totalScratchSeconds += dtSeconds
                                    onScratchDeltaSeconds(dtSeconds)

                                    if (abs(hapticAccum) >= 12f) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        hapticAccum = 0f
                                    }
                                },
                                onDragEnd = {
                                    isScratching = false
                                    scratchRotateDeg = 0f
                                    totalScratchSeconds = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScratchEnd()
                                },
                                onDragCancel = {
                                    isScratching = false
                                    scratchRotateDeg = 0f
                                    totalScratchSeconds = 0f
                                    onScratchEnd()
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            VinylDisc(
                title = title,
                author = author,
                coverUrl = coverUrl,
                playing = playing && !isScratching,
                marks = marks,
                speed = speed,
                gold = gold,
                shineTrigger = shineTrigger,
                extraRotationDeg = scratchRotateDeg,
                modifier = Modifier.fillMaxSize(),
            )

            if (isScratching) {
                Surface(
                    shape = RoundedCornerShape(KoalaRadius.pill),
                    color = Color.Black.copy(alpha = 0.85f),
                    shadowElevation = 6.dp,
                ) {
                    val sign = if (totalScratchSeconds >= 0f) "+" else ""
                    Text(
                        text = "$sign${"%.1f".format(totalScratchSeconds)}s",
                        color = colors.accent,
                        style = typography.size14.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // Tonearm overlay (Pivot at 93%, 5%)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pivot = Offset(w * 0.91f, h * 0.08f)
            val armLength = h * 0.44f

            drawCircle(
                color = colors.ctrl,
                radius = w * 0.048f,
                center = pivot,
            )

            rotate(degrees = armAngle, pivot = pivot) {
                drawLine(
                    color = colors.muted,
                    start = pivot,
                    end = Offset(pivot.x, pivot.y + armLength - w * 0.04f),
                    strokeWidth = w * 0.015f,
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = colors.accent,
                    topLeft = Offset(pivot.x - w * 0.024f, pivot.y + armLength - w * 0.06f),
                    size = Size(w * 0.048f, h * 0.075f),
                    cornerRadius = CornerRadius(w * 0.01f, w * 0.01f),
                )
            }

            drawCircle(
                color = colors.bg,
                radius = w * 0.018f,
                center = pivot,
            )
        }
    }
}

data class CrateRecordUi(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val isFinished: Boolean = false,
)

/**
 * Interactive Record Shop Crate (`apps/web/src/lib/components/RecordCrate.svelte`)
 * Adapted for touch gestures: swipe horizontally to thumb through every volume in the series,
 * tap the front record to open that book, or tap the crate title to open the series page.
 */
@Composable
fun RecordCrate(
    name: String,
    records: List<CrateRecordUi>,
    note: String,
    onRecordClick: (String) -> Unit,
    onSeriesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography
    val haptics = LocalHapticFeedback.current

    var currentIndex by remember(records.size) { mutableIntStateOf(0) }
    var isOpen by remember { mutableStateOf(false) }
    val count = records.size
    val currentRecord = records.getOrNull(currentIndex.coerceIn(0, max(0, count - 1)))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s4),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.08f)
                .pointerInput(count) {
                    if (count < 2) return@pointerInput
                    var dragAccum = 0f
                    val stepPx = 28.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragAccum = 0f
                            isOpen = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccum += dragAmount
                            if (abs(dragAccum) >= stepPx) {
                                val steps = (dragAccum / stepPx).toInt()
                                dragAccum -= steps * stepPx
                                val next = (currentIndex - steps).coerceIn(0, count - 1)
                                if (next != currentIndex) {
                                    currentIndex = next
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {},
                    )
                }
                .clickable {
                    if (currentRecord != null) {
                        onRecordClick(currentRecord.id)
                    } else {
                        onSeriesClick()
                    }
                },
        ) {
            // Visible records in the crate (from back to front so front record draws on top of behind ones)
            val maxBehind = if (isOpen) 5 else 3
            val startIdx = min(count - 1, currentIndex + maxBehind)
            for (i in startIdx downTo currentIndex) {
                val rec = records[i]
                val d = i - currentIndex
                val tiltDeg = (((i * 7919) % 5) - 2) * 0.7f
                val yOffsetDp = (-d * 10 - (if (isOpen && d == 0) 8 else 0)).dp
                val scale = (1f - d * 0.045f).coerceAtLeast(0.78f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .offset(y = yOffsetDp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = tiltDeg
                        },
                ) {
                    BookCover(
                        title = rec.title,
                        author = rec.author,
                        coverUrl = rec.coverUrl,
                        isFinished = rec.isFinished,
                        titleTop = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (d > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = (d * 0.14f).coerceAtMost(0.55f))),
                        )
                    }
                }
            }

            // Crate Front Wall
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.34f)
                    .align(Alignment.BottomCenter)
                    .clip(
                        RoundedCornerShape(
                            topStart = 6.dp,
                            topEnd = 6.dp,
                            bottomStart = KoalaRadius.card,
                            bottomEnd = KoalaRadius.card,
                        ),
                    )
                    .background(colors.ctrl)
                    .border(
                        width = 1.dp,
                        color = colors.ink.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(
                            topStart = 6.dp,
                            topEnd = 6.dp,
                            bottomStart = KoalaRadius.card,
                            bottomEnd = KoalaRadius.card,
                        ),
                    ),
            ) {
                // Cutout hand grip
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.30f)
                        .fillMaxHeight(0.18f)
                        .align(Alignment.TopCenter)
                        .offset(y = 10.dp)
                        .clip(RoundedCornerShape(KoalaRadius.pill))
                        .background(Color.Black.copy(alpha = 0.45f)),
                )

                // Scrub indicator when crate has multiple books
                if (count > 1) {
                    val fraction = currentIndex.toFloat() / (count - 1).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .offset(y = (-8).dp)
                            .clip(RoundedCornerShape(KoalaRadius.pill))
                            .background(Color.White.copy(alpha = 0.16f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.2f)
                                .offset(x = (fraction * 80f).dp)
                                .clip(RoundedCornerShape(KoalaRadius.pill))
                                .background(colors.accent),
                        )
                    }
                }
            }
        }

        Text(
            text = name,
            style = typography.size14.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { onSeriesClick() },
        )

        val subtitle = if (count > 1 && currentRecord != null && (isOpen || currentIndex > 0)) {
            "${currentIndex + 1}/$count · ${currentRecord.title}"
        } else {
            note
        }
        Text(
            text = subtitle,
            style = typography.size13,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 3D-Flip Record Sleeve (`apps/web/src/lib/components/LinerNotesSleeve.svelte`)
 * Flips between the front album sleeve (with protruding vinyl disc) and back Liner Notes.
 */
@Composable
fun LinerNotesSleeve(
    title: String,
    author: String,
    narrator: String,
    coverUrl: String?,
    catalogCode: String,
    stereoLabel: String,
    linerNotesLabel: String,
    flipFrontLabel: String,
    synopsis: String,
    playing: Boolean = false,
    gold: Boolean = false,
    marks: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography
    val density = LocalDensity.current.density

    var flipped by remember { mutableStateOf(false) }
    val rotationY by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "sleeveFlipY",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s8),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.22f)
                .graphicsLayer {
                    this.rotationY = rotationY
                    cameraDistance = 14f * density
                }
                .clickable { flipped = !flipped },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (rotationY <= 90f) {
                // Front side: Cover sleeve + protruding vinyl disc on the right
                Box(modifier = Modifier.fillMaxSize()) {
                    VinylDisc(
                        title = title,
                        author = author,
                        coverUrl = coverUrl,
                        playing = playing,
                        gold = gold,
                        marks = marks,
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .align(Alignment.CenterEnd)
                            .offset(x = (-6).dp),
                    )
                    BookCover(
                        title = title,
                        author = author,
                        coverUrl = coverUrl,
                        isFinished = gold,
                        cornerRadius = KoalaRadius.cover,
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .shadow(16.dp, RoundedCornerShape(KoalaRadius.cover)),
                    )
                }
            } else {
                // Back side: Vintage Liner Notes Sleeve (mirrored 180 deg so text is right-side up)
                Surface(
                    shape = RoundedCornerShape(KoalaRadius.card),
                    color = colors.card,
                    tonalElevation = 4.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.rotationY = 180f }
                        .border(1.dp, colors.line, RoundedCornerShape(KoalaRadius.card)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(KoalaSpacing.s16),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "KOALASHELF RECORDS · $catalogCode",
                                style = typography.label,
                                color = colors.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stereoLabel,
                                style = typography.size12,
                                color = colors.muted,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            style = typography.size18,
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (narrator.isNotBlank()) "$author · $narrator" else author,
                            style = typography.size13,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = colors.line, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = synopsis.ifBlank { "—" },
                                style = typography.size13,
                                color = colors.ink.copy(alpha = 0.88f),
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(KoalaRadius.pill),
            color = colors.ctrl,
            contentColor = colors.ctrlInk,
            modifier = Modifier.clickable { flipped = !flipped },
        ) {
            Text(
                text = if (flipped) flipFrontLabel else linerNotesLabel,
                style = typography.size12.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Shelf book tile with protruding `.sleeve-disc` above the cover, Done / Series-stack pill,
 * and continuous `.ledge` shelf bar beneath the cover (`apps/web/src/lib/components/BookTile.svelte`).
 */
@Composable
fun BookShelfTile(
    title: String,
    subtitle: String,
    coverAuthor: String = subtitle,
    coverUrl: String?,
    progress: Float = 0f,
    isFinished: Boolean = false,
    doneLabel: String = "",
    seriesCountLabel: String? = null,
    inPlayer: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    topStartOverlay: (@Composable () -> Unit)? = null,
    topEndOverlay: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography

    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Protruding vinyl disc above the sleeve (`.sleeve-disc` in BookTile.svelte)
            VinylDisc(
                title = title,
                author = coverAuthor,
                coverUrl = coverUrl,
                playing = isPlaying,
                gold = isFinished,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .align(Alignment.TopCenter)
                    .offset(y = if (inPlayer) (-18).dp else (-8).dp),
            )

            // Stacked record shadow when representing a collapsed series
            if (seriesCountLabel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .aspectRatio(1f)
                        .offset(x = 5.dp, y = (-5).dp)
                        .clip(RoundedCornerShape(KoalaRadius.cover))
                        .background(colors.card.copy(alpha = 0.7f))
                        .border(1.dp, colors.line, RoundedCornerShape(KoalaRadius.cover)),
                )
            }

            // Front square cover sleeve
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(8.dp, RoundedCornerShape(KoalaRadius.cover)),
            ) {
                BookCover(
                    title = title,
                    author = coverAuthor,
                    coverUrl = coverUrl,
                    progress = progress,
                    isFinished = isFinished,
                    cornerRadius = KoalaRadius.cover,
                    modifier = Modifier.fillMaxSize(),
                )

                if (topStartOverlay != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        topStartOverlay()
                    }
                }

                if (topEndOverlay != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        topEndOverlay()
                    }
                } else if (seriesCountLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(KoalaRadius.pill),
                        color = colors.ctrl,
                        contentColor = colors.ctrlInk,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = seriesCountLabel,
                            style = typography.size12.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else if (isFinished && doneLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(KoalaRadius.pill),
                        color = colors.accent,
                        contentColor = colors.onAccent,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = doneLabel,
                            style = typography.size12.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                if (onPlayClick != null) {
                    Surface(
                        shape = CircleShape,
                        color = colors.ctrl.copy(alpha = 0.92f),
                        contentColor = colors.ctrlInk,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(34.dp),
                        onClick = onPlayClick,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width * 0.18f, size.height * 0.1f)
                                    lineTo(size.width * 0.92f, size.height * 0.5f)
                                    lineTo(size.width * 0.18f, size.height * 0.9f)
                                    close()
                                }
                                drawPath(path = path, color = colors.ctrlInk)
                            }
                        }
                    }
                }
            }
        }

        // Continuous shelf ledge bar (`.ledge` in BookTile.svelte)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(
                    RoundedCornerShape(
                        bottomStart = 3.dp,
                        bottomEnd = 3.dp,
                    ),
                )
                .background(colors.ledge),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = typography.size14.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = typography.size12,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Horizontal Book Crate Shelf with dark `.ledge` bar overlapping the bottom of the covers
 * (`apps/web/src/lib/components/Crate.svelte`).
 */
@Composable
fun BookCrateShelf(
    title: String,
    note: String = "",
    records: List<CrateRecordUi>,
    onRecordClick: (String) -> Unit,
    onHeaderClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography

    // Matches `Crate.svelte`: `.crate` has `border-radius: var(--r-card-lg); overflow: hidden;`,
    // `.row` has `grid-auto-columns: 132px; gap: 0; padding-top: 26px;`, and `.ledge` is `position: absolute; bottom: 0`.
    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(KoalaRadius.cardLg)),
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(records.size, key = { records[it].id }) { idx ->
                val rec = records[idx]
                Box(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onRecordClick(rec.id) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // `.sleeve-disc` peeking from top of cover
                    VinylDisc(
                        title = rec.title,
                        author = rec.author,
                        coverUrl = rec.coverUrl,
                        gold = rec.isFinished,
                        modifier = Modifier
                            .size(110.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-12).dp),
                    )
                    BookCover(
                        title = rec.title,
                        author = rec.author,
                        coverUrl = rec.coverUrl,
                        isFinished = rec.isFinished,
                        titleTop = true,
                        cornerRadius = 0.dp,
                        modifier = Modifier.size(132.dp),
                    )
                }
            }
        }

        // `.ledge` overlapping the bottom of the covers inside `.crate`
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.ctrl)
                .then(
                    if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = typography.size13.copy(fontWeight = FontWeight.Bold),
                color = colors.ctrlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = note,
                    style = typography.size13,
                    color = colors.ctrlInk.copy(alpha = 0.75f),
                )
            }
        }
    }
}
