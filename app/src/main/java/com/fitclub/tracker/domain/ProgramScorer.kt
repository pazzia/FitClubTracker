package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.ExerciseType
import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.model.MovePattern
import com.fitclub.tracker.data.model.restLabel
import com.fitclub.tracker.data.repo.ProgramDraft
import kotlin.math.max
import kotlin.math.roundToInt

data class Criterion(val title: String, val value: Float)

data class Recommendation(val title: String, val text: String, val severity: Float)

data class FitResult(
    val percent: Int,
    val criteria: List<Criterion>,
    val recommendations: List<Recommendation>
) {
    val verdict: String
        get() = when {
            percent >= 80 -> "Отлично подходит"
            percent >= 60 -> "Подходит с оговорками"
            else -> "Слабо соответствует цели"
        }
}

/**
 * Оценивает, насколько программа подходит под выбранную цель.
 * Веса: повторения 30%, отдых 20%, доля базовых 15%, баланс 15%, объём 10%, частота 10%.
 */
object ProgramScorer {
    private val FREE_WEIGHTS = setOf("Штанга", "Гантели", "EZ-гриф")
    private val PATTERN_OVERRIDES = mapOf(
        "reardelt" to MovePattern.PULL, "facepull" to MovePattern.PULL,
        "deadlift" to MovePattern.LEGS, "hyper" to MovePattern.CORE
    )

    fun patternOf(e: ExerciseEntity): MovePattern = PATTERN_OVERRIDES[e.id] ?: e.group.pattern

    fun score(program: ProgramDraft, catalog: Map<String, ExerciseEntity>): FitResult {
        val g = program.goal
        var total = 0; var rep = 0f; var rest = 0f; var compound = 0; var free = 0
        var push = 0; var pull = 0; var legs = 0; var daysUsed = 0
        val badReps = mutableListOf<String>()
        val badRest = mutableListOf<String>()

        program.days.forEach { day ->
            var daySets = 0
            day.items.forEach { item ->
                val e = catalog[item.exerciseId] ?: return@forEach
                val n = item.sets
                total += n; daySets += n
                val rv = when (item.reps) { in g.idealReps -> 1f; in g.okReps -> 0.5f; else -> 0f }
                rep += rv * n
                if (rv == 0f) badReps += "${e.name} (${item.reps.label})"
                val sv = when (item.restSec) { in g.restRange -> 1f; in g.restOk -> 0.5f; else -> 0f }
                rest += sv * n
                if (sv == 0f) badRest += "${e.name} (${restLabel(item.restSec)})"
                if (e.type == ExerciseType.BASIC) compound += n
                if (e.equipment in FREE_WEIGHTS) free += n
                when (patternOf(e)) {
                    MovePattern.PUSH -> push += n
                    MovePattern.PULL -> pull += n
                    MovePattern.LEGS -> legs += n
                    MovePattern.CORE -> Unit
                }
            }
            if (daySets > 0) daysUsed++
        }

        if (total == 0) {
            return FitResult(0, emptyList(), listOf(Recommendation("Программа пуста", "Добавьте упражнения, чтобы увидеть оценку соответствия цели.", 0f)))
        }

        val repS = rep / total
        val restS = rest / total
        val share = compound.toFloat() / total
        val compS = if (share in g.compoundShare) 1f else {
            val dist = if (share < g.compoundShare.start) g.compoundShare.start - share else share - g.compoundShare.endInclusive
            max(0f, 1f - dist / 0.4f)
        }
        val coverage = listOf(push, pull, legs).count { it > 0 } / 3f
        val ratio = if (push > 0 && pull > 0) push.toFloat() / pull else 0f
        val balS = coverage * 0.7f + when { ratio in 0.67f..1.5f -> 0.3f; ratio > 0f -> 0.15f; else -> 0f }
        val avg = total.toFloat() / max(1, daysUsed)
        val volS = if (avg >= g.setsPerSession.first && avg <= g.setsPerSession.last) 1f else {
            val dist = if (avg < g.setsPerSession.first) g.setsPerSession.first - avg else avg - g.setsPerSession.last
            max(0f, 1f - dist / 10f)
        }
        val fd = when {
            program.freqPerWeek < g.freqPerWeek.first -> g.freqPerWeek.first - program.freqPerWeek
            program.freqPerWeek > g.freqPerWeek.last -> program.freqPerWeek - g.freqPerWeek.last
            else -> 0
        }
        val frS = when (fd) { 0 -> 1f; 1 -> 0.5f; else -> 0f }

        val totalScore = 0.3f * repS + 0.2f * restS + 0.15f * compS + 0.15f * balS + 0.1f * volS + 0.1f * frS

        fun list3(items: List<String>): String {
            val d = items.distinct()
            return d.take(3).joinToString(", ") + if (d.size > 3) " и ещё ${d.size - 3}" else ""
        }

        val recs = mutableListOf<Recommendation>()
        if (repS < 0.85f) recs += Recommendation(
            "Повторения не под цель «${g.title}»",
            g.repsTip + if (badReps.isNotEmpty()) " Вне диапазона: ${list3(badReps)}." else "", repS
        )
        if (restS < 0.85f) recs += Recommendation(
            "Отдых между подходами",
            g.restTip + if (badRest.isNotEmpty()) " Сейчас: ${list3(badRest)}." else "", restS
        )
        if ((g == Goal.ENDURANCE || g == Goal.FAT_LOSS) &&
            free.toFloat() / total > 0.8f && repS < 0.5f
        ) recs += Recommendation(
            "Тип отягощения",
            "Почти вся программа — тяжёлые свободные веса. Для этой цели добавьте тренажёры, блоки и упражнения со своим весом в круговом формате: так проще держать темп и много повторений без потери техники.",
            repS + 0.01f
        )
        if (compS < 0.85f) recs += if (share < g.compoundShare.start) Recommendation("Мало базовых упражнений", g.compoundTip, compS)
        else Recommendation("Мало изоляции", "Добавьте изоляцию на отстающие группы (дельты, руки, икры) — для массы нужен объём на каждую мышцу.", compS)
        if (balS < 0.85f) {
            val parts = mutableListOf<String>()
            if (legs == 0) parts += "Нет упражнений на ноги — добавьте присед, жим ногами или выпады."
            if (pull == 0) parts += "Нет тяговых упражнений — добавьте подтягивания или тяги."
            if (push == 0) parts += "Нет жимовых упражнений — добавьте жимы на грудь и плечи."
            if (ratio > 1.5f) parts += "Жимовых подходов в ${"%.1f".format(ratio).replace('.', ',')} раза больше, чем тяговых. Стремитесь к соотношению около 1:1, чтобы беречь плечи и осанку."
            if (ratio > 0f && ratio < 0.67f) parts += "Тяговых подходов заметно больше жимовых — добавьте жимы для баланса."
            recs += Recommendation("Баланс мышечных групп", parts.joinToString(" "), balS)
        }
        if (volS < 0.85f) recs += Recommendation(
            "Объём за тренировку",
            "В среднем ${avg.roundToInt()} подходов за тренировку. Для цели «${g.title}» оптимально ${g.setsPerSession.first}–${g.setsPerSession.last}.",
            volS
        )
        if (frS < 0.85f) recs += Recommendation(
            "Частота тренировок",
            "Сейчас ${program.freqPerWeek} трен. в неделю. Для цели «${g.title}» рекомендуется ${g.freqPerWeek.first}–${g.freqPerWeek.last}.",
            frS
        )
        recs.sortBy { it.severity }
        if (recs.isEmpty()) recs += Recommendation(
            "Программа соответствует цели",
            "Параметры сбалансированы. Следите за прогрессией нагрузки: добавляйте вес или повторения каждые 1–2 недели.",
            1f
        )

        return FitResult(
            percent = (totalScore * 100).roundToInt(),
            criteria = listOf(
                Criterion("Повторения", repS), Criterion("Отдых", restS),
                Criterion("Базовые и изоляция", compS), Criterion("Баланс жим / тяга / ноги", balS),
                Criterion("Объём за тренировку", volS), Criterion("Частота", frS)
            ),
            recommendations = recs
        )
    }
}
