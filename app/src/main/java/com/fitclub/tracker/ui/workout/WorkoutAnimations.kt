package com.fitclub.tracker.ui.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitclub.tracker.ui.components.GlowProgress
import com.fitclub.tracker.ui.components.LineIcon
import com.fitclub.tracker.ui.components.PackIcons
import com.fitclub.tracker.ui.components.PillShape
import com.fitclub.tracker.ui.components.RoundIconButton
import com.fitclub.tracker.ui.components.rememberReducedMotion
import com.fitclub.tracker.ui.components.glow
import com.fitclub.tracker.ui.theme.Condensed
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.Sans
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Timings of the workout start and complete animations. They come from the design pack's
 * declarative config (assets/animations/fitclub_animations.json): the JSON says what happens and
 * when, the composables below say how it is drawn.
 */
data class StartTiming(val stepMs: Int = 900, val goAtMs: Int = 2700, val finishAtMs: Int = 3300) {
    /** Reduce Motion keeps the countdown working with a 700 ms step (ANIMATION_SPEC.md). */
    fun reduced(): StartTiming = StartTiming(700, 2100, 2500)
}

data class CompleteTiming(
    val ringsAtMs: Int = 300, val titleAtMs: Int = 600, val statsAtMs: Int = 1050,
    val streakAtMs: Int = 1450, val finishAtMs: Int = 2200
)

data class AnimationTimings(val start: StartTiming = StartTiming(), val complete: CompleteTiming = CompleteTiming())

object AnimationPack {
    private const val ASSET = "animations/fitclub_animations.json"
    @Volatile private var cached: AnimationTimings? = null

    fun timings(context: Context): AnimationTimings = cached ?: runCatching {
        parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }.getOrDefault(AnimationTimings()).also { cached = it }

    /** Reads the moments the screens depend on; anything missing keeps the pack's defaults. */
    fun parse(json: String): AnimationTimings {
        val root = JSONObject(json).getJSONObject("animations")
        val defaults = AnimationTimings()
        val start = root.optJSONObject("workout_start")?.optJSONArray("timeline")?.let { timeline ->
            var two = -1; var go = -1; var finish = -1
            for (i in 0 until timeline.length()) {
                val step = timeline.getJSONObject(i)
                val at = step.optInt("atMs", -1)
                if (step.optString("event") == "WORKOUT_START_ANIMATION_FINISHED") finish = at
                val actions = step.optJSONArray("actions") ?: continue
                for (k in 0 until actions.length()) {
                    val a = actions.getJSONObject(k)
                    if (a.optString("target") == "countdown_number" && a.optString("property") == "text") {
                        when (a.optString("value")) { "2" -> two = at; "GO" -> go = at }
                    }
                }
            }
            StartTiming(
                stepMs = two.takeIf { it > 0 } ?: defaults.start.stepMs,
                goAtMs = go.takeIf { it > 0 } ?: defaults.start.goAtMs,
                finishAtMs = finish.takeIf { it > 0 } ?: defaults.start.finishAtMs
            )
        } ?: defaults.start
        val complete = root.optJSONObject("workout_complete")?.optJSONArray("timeline")?.let { timeline ->
            val at = mutableMapOf<String, Int>()
            for (i in 0 until timeline.length()) {
                val step = timeline.getJSONObject(i)
                val time = step.optInt("atMs", -1)
                if (step.optString("event") == "WORKOUT_COMPLETE_ANIMATION_FINISHED") at["finish"] = time
                val actions = step.optJSONArray("actions") ?: continue
                for (k in 0 until actions.length()) at.putIfAbsent(actions.getJSONObject(k).optString("target"), time)
            }
            CompleteTiming(
                ringsAtMs = at["success_rings"] ?: defaults.complete.ringsAtMs,
                titleAtMs = at["complete_title"] ?: defaults.complete.titleAtMs,
                statsAtMs = at["stats"] ?: defaults.complete.statsAtMs,
                streakAtMs = at["streak_card"] ?: defaults.complete.streakAtMs,
                finishAtMs = at["finish"] ?: defaults.complete.finishAtMs
            )
        } ?: defaults.complete
        return AnimationTimings(start, complete)
    }
}

enum class Haptic { LIGHT, MEDIUM, STRONG, SUCCESS }

/** Haptic mapping of the pack: tick / click / heavy click / success waveform. */
fun haptic(context: Context, kind: Haptic) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) return
        val effect = when {
            kind == Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 28, 45, 45), -1)
            Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(when (kind) {
                Haptic.LIGHT -> VibrationEffect.EFFECT_TICK
                Haptic.MEDIUM -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_HEAVY_CLICK
            })
            else -> VibrationEffect.createOneShot(when (kind) { Haptic.LIGHT -> 12L; Haptic.MEDIUM -> 24L; else -> 45L }, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}

/** Short start click and two-note success tone; both follow the «sounds» switch. */
object WorkoutSounds {
    fun start() = tone(android.media.ToneGenerator.TONE_PROP_ACK, 140)
    fun complete() = tone(android.media.ToneGenerator.TONE_PROP_PROMPT, 260)
    private fun tone(type: Int, length: Int) {
        runCatching {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
            tone.startTone(type, length)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, length + 200L)
        }
    }
}

private fun easeOutCubic(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).pow(3)
private fun easeOutBack(x: Float): Float { val c = 1.9f; val t = x.coerceIn(0f, 1f) - 1f; return 1f + (c + 1f) * t * t * t + c * t * t }
private fun progressOf(t: Long, from: Int, duration: Int) = ((t - from).toFloat() / duration).coerceIn(0f, 1f)

/** Frame-driven clock in milliseconds since the first frame; stops once [until] is reached. */
@Composable
private fun rememberAnimationClock(key: Any, until: Long, onTick: (Long) -> Unit = {}): Long {
    var t by remember(key) { mutableLongStateOf(0L) }
    val tick by rememberUpdatedState(onTick)
    LaunchedEffect(key) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it } - start
            t = now
            tick(now)
            if (now >= until) break
        }
    }
    return t
}

/**
 * Workout start: 3 → 2 → 1 → GO around a lime ring with 48 ticks, then the first exercise.
 * Tapping anywhere starts at once (SKIP_COUNTDOWN).
 */
@Composable
fun WorkoutCountdown(
    exerciseName: String,
    subtitle: String,
    exerciseCount: Int,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val timing = remember(reduced) { AnimationPack.timings(context).start.let { if (reduced) it.reduced() else it } }
    val finished by rememberUpdatedState(onFinished)
    val sound by rememberUpdatedState(soundOn)
    var lastStep by remember { mutableLongStateOf(-1L) }
    var done by remember { mutableLongStateOf(0L) }
    fun finishOnce() { if (done == 0L) { done = 1L; finished() } }
    BackHandler(onBack = onBack)
    val t = rememberAnimationClock("countdown", timing.finishAtMs.toLong()) { now ->
        val step = (now / timing.stepMs).coerceAtMost(3)
        if (step != lastStep && now < timing.finishAtMs) {
            lastStep = step
            haptic(context, when (step) { 0L, 1L -> Haptic.LIGHT; 2L -> Haptic.MEDIUM; else -> Haptic.STRONG })
            if (step == 3L && sound) WorkoutSounds.start()
        }
        if (now >= timing.finishAtMs) finishOnce()
    }
    val step = (t / timing.stepMs).toInt().coerceAtMost(3)
    val go = t >= timing.goAtMs
    val local = t - step * timing.stepMs
    val progress = if (go) 1f else (step + easeOutCubic(local / (timing.stepMs * .944f))) / 3f
    val pulse = if (go && !reduced) sin(PI * progressOf(t, timing.goAtMs, 600)).toFloat() else 0f
    val numberScale = if (reduced) 1f else .72f + .28f * easeOutBack(local / 320f)
    val numberAlpha = (local / if (reduced) 200f else 150f).coerceIn(0f, 1f)
    val left = (3 - step).coerceAtLeast(0)
    val lime = Fc.Lime
    val dark = !Fc.isLight

    Box(
        Modifier.fillMaxSize().background(Fc.Bg)
            .drawBehind {
                if (dark) drawRect(Brush.radialGradient(listOf(lime.copy(alpha = .12f + .22f * pulse), Color.Transparent),
                    Offset(size.width / 2, size.height * .34f), size.width * .75f))
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
                onClickLabel = "Начать сразу") { finishOnce() }
            .semantics { contentDescription = "Отсчёт перед тренировкой. Нажмите, чтобы начать сразу" }
            .testTag("workout-countdown")
            .statusBarsPadding().navigationBarsPadding()
    ) {
        if (!reduced && dark) Sparks(t, pulse)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIconButton("Назад", com.fitclub.tracker.ui.components.LineIcons.BACK, onBack)
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(PillShape).background(Fc.Surface.copy(alpha = .8f)).border(1.dp, Fc.Line, PillShape)
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("1/$exerciseCount", color = Fc.Lime, style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                        Text(" упражнений", color = Fc.Muted, style = TextStyle(fontFamily = Sans, fontSize = 13.sp))
                    }
                    GlowProgress(if (exerciseCount > 0) (.4f + .6f * progress) / exerciseCount else 0f, Modifier.fillMaxWidth(), height = 4.dp)
                }
                RoundIconButton(if (soundOn) "Выключить звук" else "Включить звук", if (soundOn) PackIcons.SOUND_ON else PackIcons.SOUND_OFF, onToggleSound)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val ring = minOf(maxWidth - 40.dp, maxHeight * .52f, 320.dp)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                    Box(Modifier.size(ring).graphicsLayer {
                        val kick = if (reduced) 0f else (if (go) .05f else .022f) * (1f - (local / 200f).coerceIn(0f, 1f))
                        scaleX = 1f + kick; scaleY = 1f + kick
                    }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) { drawCountdownRing(progress, pulse, lime, dark) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(
                                if (go) "GO" else "${3 - step}",
                                color = Fc.Lime, textAlign = TextAlign.Center,
                                style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold,
                                    fontSize = if (go) 96.sp else 112.sp, lineHeight = 116.sp,
                                    shadow = if (dark) Shadow(lime.copy(alpha = .35f + .4f * pulse), Offset.Zero, 28f + 40f * pulse) else null),
                                modifier = Modifier.graphicsLayer { scaleX = numberScale; scaleY = numberScale; alpha = numberAlpha }
                            )
                            Text(if (go) "НАЧИНАЕМ" else "ПРИГОТОВЬТЕСЬ", color = Fc.Text,
                                style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.2.sp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ПЕРВОЕ УПРАЖНЕНИЕ", color = Fc.Muted,
                            style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 2.sp))
                        Text(exerciseName, color = Fc.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp))
                        if (subtitle.isNotBlank()) Text(subtitle, color = Fc.Muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), textAlign = TextAlign.Center)
                    }
                }
            }
            Column(Modifier.padding(bottom = 28.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (go) Text("Старт!", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                    else {
                        Text("Старт через ", color = Fc.Text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                        Text("$left", color = Fc.Lime, style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp))
                        Text(" сек", color = Fc.Text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                    }
                }
                Text("Нажмите, чтобы начать сразу", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun exercisesWord(n: Int): String = com.fitclub.tracker.ui.programs.pluralRu(n, "упражнение", "упражнения", "упражнений")

private fun DrawScope.drawCountdownRing(progress: Float, pulse: Float, lime: Color, dark: Boolean) {
    val stroke = size.minDimension * .055f
    val radius = size.minDimension / 2 - stroke
    val center = Offset(size.width / 2, size.height / 2)
    val track = if (dark) Color(0xFF263039) else Color(0xFFD5D9D0)
    drawCircle(track, radius, center, style = Stroke(stroke))
    val sweep = 360f * progress
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    if (progress > .004f) {
        if (dark) drawArc(lime.copy(alpha = .22f + .25f * pulse), -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
        drawArc(lime, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    val tickOuter = radius - stroke * 1.4f
    val lit = (progress * 48).toInt()
    for (i in 0 until 48) {
        val major = i % 8 == 0
        val len = if (major) stroke * .95f else stroke * .55f
        val on = i < lit
        rotate(i * 7.5f, center) {
            drawLine(if (on) lime else Color(0xFF59636B), Offset(center.x, center.y - tickOuter), Offset(center.x, center.y - tickOuter + len),
                strokeWidth = if (major) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** A few drifting lime sparks around the ring; skipped with Reduce Motion. */
@Composable
private fun Sparks(t: Long, pulse: Float) {
    val lime = Fc.Lime
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2; val cy = size.height * .36f
        for (p in 0 until 12) {
            val a = p * 2.39996f + t * .00018f * (if (p % 2 == 0) -1 else 1)
            val r = size.width * .40f + ((t * .025f + p * 37) % 90f).dp.toPx()
            val x = cx + cos(a) * r; val y = cy + sin(a) * r * .92f
            val tw = (.25f + .55f * kotlin.math.abs(sin(t * .002f + p)) + pulse * .4f).coerceIn(0f, 1f)
            drawCircle(lime.copy(alpha = tw * .9f), (if (p % 3 == 0) 2f else 1.3f).dp.toPx(), Offset(x, y))
        }
    }
}

/**
 * Workout complete header: lime badge pops in with a check, three rings ripple out, the title
 * rises, stats count up and the streak card follows. Content stays in the semantics tree from
 * the first frame; only its drawing is animated.
 */
@Composable
fun WorkoutCompleteHeader(
    name: String?,
    subtitle: String,
    minutes: Int,
    exercises: Int,
    volume: Float,
    volumeLabel: String,
    formatVolume: (Float) -> String,
    streakWeeks: Int,
    weekDays: List<Boolean>,
    soundOn: Boolean,
    onReplay: () -> Unit = {},
    onFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val timing = remember { AnimationPack.timings(context).complete }
    val finished by rememberUpdatedState(onFinished)
    var fired by remember { mutableLongStateOf(0L) }
    var playKey by remember { mutableLongStateOf(0L) }
    val t = rememberAnimationClock(playKey, timing.finishAtMs + 400L) { now ->
        if (fired == 0L) {
            fired = 1L
            haptic(context, Haptic.SUCCESS)
            if (soundOn) WorkoutSounds.complete()
        }
        if (now >= timing.finishAtMs && fired == 1L) { fired = 2L; finished() }
    }
    val lime = Fc.Lime
    val dark = !Fc.isLight
    fun rise(at: Int, dur: Int = 420, dist: Float = 16f): Modifier = Modifier.graphicsLayer {
        val k = easeOutCubic(progressOf(t, at, dur))
        alpha = progressOf(t, at, if (reduced) 200 else 300)
        translationY = if (reduced) 0f else (1f - k) * dist * density
    }
    Column(Modifier.fillMaxWidth().testTag("complete-header"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val base = 62.dp.toPx()
                val ringK = progressOf(t, timing.ringsAtMs, 900)
                if (dark) drawCircle(Brush.radialGradient(listOf(lime.copy(alpha = .12f * ringK), Color.Transparent), c, size.minDimension / 2), size.minDimension / 2, c)
                listOf(1.32f, 1.62f, 1.94f).forEachIndexed { i, k ->
                    drawCircle(lime.copy(alpha = (.30f - i * .08f) * ringK), base * k, c, style = Stroke(1.5.dp.toPx()))
                }
                if (!reduced) listOf(0, 150, 300).forEach { d ->
                    val p = progressOf(t, timing.ringsAtMs + d, 1200)
                    if (p > 0f && p < 1f) {
                        val s = .65f + .7f * easeOutCubic(p)
                        drawCircle(lime.copy(alpha = .45f * (1f - p)), base * 1.45f * s, c, style = Stroke(2.dp.toPx()))
                    }
                }
                if (!reduced && dark && t < 1500) for (i in 0 until 14) {
                    val a = i * (2 * PI / 14).toFloat() + (i % 2) * .2f
                    val k = easeOutCubic(progressOf(t, 60 + (i % 3) * 40, 1200))
                    val dist = base * 1.1f + k * (base * 1.1f + (i % 4) * 10.dp.toPx())
                    val p0 = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist)
                    val len = (10f * (1f - k) + 3f).dp.toPx()
                    val p1 = Offset(p0.x + cos(a) * len, p0.y + sin(a) * len)
                    if (k > 0f) drawLine(lime.copy(alpha = 1f - k), p0, p1, 3.dp.toPx(), StrokeCap.Round)
                }
            }
            val badge = when {
                reduced -> 1f
                t < 520 -> .1f + .98f * easeOutCubic(t / 520f)
                else -> 1.08f - .08f * easeOutCubic(progressOf(t, 520, 220))
            }
            val glowA = .35f + .35f * sin(PI * progressOf(t, 0, 900)).toFloat()
            Box(
                Modifier.size(124.dp)
                    .graphicsLayer { scaleX = badge; scaleY = badge; alpha = progressOf(t, 0, if (reduced) 300 else 120) }
                    .glow(lime, dark, radius = 34.dp, alpha = glowA * .8f)
                    .clip(CircleShape)
                    .background(lime)
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .28f), Color.Transparent),
                            Offset(size.width * .38f, size.height * .32f), size.minDimension * .6f))
                    }
                    .semantics { contentDescription = "Тренировка завершена" }
                    .clickable(role = Role.Button, onClickLabel = "Повторить анимацию") { playKey++; fired = 0L; onReplay() },
                contentAlignment = Alignment.Center
            ) {
                val check = easeOutCubic(progressOf(t, 180, 420))
                Canvas(Modifier.size(58.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * .22f, size.height * .53f); lineTo(size.width * .42f, size.height * .72f); lineTo(size.width * .78f, size.height * .32f)
                    }
                    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                    val part = androidx.compose.ui.graphics.Path()
                    measure.getSegment(0f, measure.length * (if (reduced) 1f else check), part, true)
                    drawPath(part, Color(0xFF0A0C0D), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        Column(rise(timing.titleAtMs), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ТРЕНИРОВКА ЗАВЕРШЕНА", color = Fc.Lime,
                style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.4.sp))
            Text(if (name.isNullOrBlank()) "Отличная работа!" else "Отличная работа,\n$name", color = Fc.Text, textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
                modifier = Modifier.padding(top = 8.dp))
            if (subtitle.isNotBlank()) Text(subtitle, color = Fc.Muted, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        val countK = if (reduced) progressOf(t, timing.statsAtMs, 200) else easeOutCubic(progressOf(t, timing.statsAtMs, 700))
        Row(
            rise(timing.statsAtMs, 400, 18f).fillMaxWidth().clip(com.fitclub.tracker.ui.components.CardShape).background(Fc.Surface)
                .border(1.dp, Fc.Line, com.fitclub.tracker.ui.components.CardShape).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stats = listOf(
                Triple(PackIcons.STOPWATCH, "${(minutes * countK).toInt()} мин", "ВРЕМЯ"),
                Triple(PackIcons.DUMBBELL, "${(exercises * countK).toInt()}", exercisesWord(exercises).uppercase()),
                Triple(PackIcons.PLATES, formatVolume(volume * countK) + " " + volumeLabel, "ТОННАЖ")
            )
            stats.forEachIndexed { i, (icon, value, label) ->
                if (i > 0) Box(Modifier.width(1.dp).height(56.dp).background(Fc.Line))
                Column(Modifier.weight(1f).padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineIcon(icon, Fc.Lime, Modifier.size(22.dp))
                    Text(value, color = Fc.Text, maxLines = 1,
                        style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFeatureSettings = "tnum"))
                    Text(label, color = Fc.Muted, maxLines = 1,
                        style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.2.sp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val streakIn = progressOf(t, timing.streakAtMs, 350)
        val flip = progressOf(t, timing.streakAtMs + 250, 260)
        Row(
            Modifier.graphicsLayer {
                alpha = streakIn
                val s = if (reduced) 1f else .96f + .04f * easeOutBack(streakIn)
                scaleX = s; scaleY = s
            }.fillMaxWidth().clip(com.fitclub.tracker.ui.components.CardShape).background(Fc.Surface)
                .border(1.dp, Fc.Line, com.fitclub.tracker.ui.components.CardShape).padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Fc.LimeSoft).border(1.dp, Fc.Lime.copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) {
                val pop = if (reduced) 1f else 1f + .3f * sin(PI * flip).toFloat()
                com.fitclub.tracker.ui.components.FilledIcon(PackIcons.FLAME_FILL, Fc.Lime, Modifier.size(26.dp).graphicsLayer { scaleX = pop; scaleY = pop })
            }
            Column(Modifier.weight(1f)) {
                Text(if (streakWeeks > 1) "СЕРИЯ ПРОДЛЕНА" else "СЕРИЯ НАЧАТА", color = Fc.Muted,
                    style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shown = if (flip >= 1f || reduced) streakWeeks else (streakWeeks - 1).coerceAtLeast(0)
                    val numPop = if (reduced) 1f else 1f + .25f * sin(PI * flip).toFloat()
                    Text("$shown", color = if (flip > 0f && flip < 1f) Fc.Lime else Fc.Text,
                        style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp),
                        modifier = Modifier.graphicsLayer { scaleX = numPop; scaleY = numPop; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) })
                    Text(com.fitclub.tracker.ui.programs.pluralRu(shown, "неделя", "недели", "недель") + " подряд", color = Fc.Text,
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.semantics {
                contentDescription = "На этой неделе тренировок: ${weekDays.count { it }} из 7"
            }) {
                weekDays.forEachIndexed { i, on ->
                    val last = i == weekDays.indexOfLast { it }
                    val lit = on && (!last || flip > 0f || reduced)
                    val s = if (last && !reduced) 1f + .6f * sin(PI * flip).toFloat() else 1f
                    Box(Modifier.size(8.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(if (lit) Fc.Lime else Fc.Line))
                }
            }
        }
    }
}
