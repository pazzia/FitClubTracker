package com.fitclub.tracker.data.model

/** Мышечные зоны, которые умеет подсвечивать модель тела. */
enum class Muscle(val title: String) {
    CHEST("грудные"),
    LATS("широчайшие"),
    TRAPS("трапеции"),
    LOWER_BACK("разгибатели спины"),
    DELTS("дельты"),
    BICEPS("бицепс"),
    TRICEPS("трицепс"),
    FOREARMS("предплечья"),
    QUADS("квадрицепсы"),
    HAMS("задняя поверхность бедра"),
    GLUTES("ягодичные"),
    CALVES("икры"),
    ABS("пресс"),
    MIDDLE_BACK("середина спины"),
    ADDUCTORS("приводящие мышцы бедра")
}

enum class BodyView { FRONT, BACK }

enum class Sex(val title: String) { MALE("Мужской"), FEMALE("Женский") }

/** Группы мышц — разделы справочника. */
enum class MuscleGroup(val title: String, val regions: List<Muscle>, val view: BodyView) {
    CHEST("Грудь", listOf(Muscle.CHEST), BodyView.FRONT),
    BACK("Спина", listOf(Muscle.LATS, Muscle.TRAPS, Muscle.LOWER_BACK), BodyView.BACK),
    SHOULDERS("Плечи", listOf(Muscle.DELTS), BodyView.FRONT),
    BICEPS("Бицепс", listOf(Muscle.BICEPS), BodyView.FRONT),
    TRICEPS("Трицепс", listOf(Muscle.TRICEPS), BodyView.BACK),
    FOREARMS("Предплечья", listOf(Muscle.FOREARMS), BodyView.FRONT),
    QUADS("Квадрицепсы", listOf(Muscle.QUADS), BodyView.FRONT),
    HAMSTRINGS("Задняя поверхность бедра", listOf(Muscle.HAMS), BodyView.BACK),
    GLUTES("Ягодицы", listOf(Muscle.GLUTES), BodyView.BACK),
    CALVES("Икры", listOf(Muscle.CALVES), BodyView.BACK),
    CORE("Пресс и кор", listOf(Muscle.ABS), BodyView.FRONT);

    /** Тип движения для оценки баланса программы. */
    val pattern: MovePattern
        get() = when (this) {
            CHEST, SHOULDERS, TRICEPS -> MovePattern.PUSH
            BACK, BICEPS, FOREARMS -> MovePattern.PULL
            QUADS, HAMSTRINGS, GLUTES, CALVES -> MovePattern.LEGS
            CORE -> MovePattern.CORE
        }
}

enum class MovePattern { PUSH, PULL, LEGS, CORE }

enum class ExerciseType(val title: String, val explanation: String) {
    BASIC("Базовое", "Многосуставное: работают несколько суставов и крупных мышц. Основа силы и массы."),
    ISOLATION("Изоляция", "Односуставное: точечная проработка одной мышцы, обычно после базовых.")
}

object Levels {
    fun title(level: Int) = when (level) { 1 -> "Новичок"; 2 -> "Продвинутый"; else -> "Профи" }
    fun explanation(level: Int) = when (level) {
        1 -> "Простая техника, можно выполнять с первых тренировок."
        2 -> "Нужна поставленная техника и базовая сила. Лучше осваивать с тренером."
        else -> "Сложная координация и высокие нагрузки. Для опытных атлетов."
    }
}

enum class RepRange(val label: String) {
    R1_3("1–3"), R3_5("3–5"), R6_8("6–8"), R8_12("8–12"),
    R12_15("12–15"), R15_20("15–20"), R20_PLUS("20+"), TIME("30–60 с")
}

val REST_OPTIONS = listOf(30, 45, 60, 90, 120, 150, 180, 240)

fun restLabel(sec: Int): String = when {
    sec < 60 -> "$sec с"
    sec % 60 == 0 -> "${sec / 60} мин"
    else -> "${sec / 60},${(sec % 60) * 10 / 60} мин"
}

/** Цель тренировок и методические ориентиры для оценки программы. */
enum class Goal(
    val title: String,
    val idealReps: Set<RepRange>,
    val okReps: Set<RepRange>,
    val restRange: IntRange,
    val restOk: IntRange,
    val compoundShare: ClosedFloatingPointRange<Float>,
    val setsPerSession: IntRange,
    val freqPerWeek: IntRange,
    val defaultReps: RepRange,
    val defaultRest: Int,
    val repsTip: String,
    val restTip: String,
    val compoundTip: String
) {
    STRENGTH(
        "Сила", setOf(RepRange.R1_3, RepRange.R3_5), setOf(RepRange.R6_8),
        120..300, 90..300, 0.7f..1f, 10..20, 3..4, RepRange.R3_5, 180,
        "Работайте в 1–5 повторениях с весом 80–90% от 1ПМ — это главный стимул для роста силы.",
        "Отдыхайте 2–5 минут между подходами в базовых упражнениях, чтобы восстанавливать силу.",
        "Сделайте основой базовые упражнения (присед, жимы, тяги) — не меньше 70% подходов."
    ),
    MASS(
        "Масса", setOf(RepRange.R6_8, RepRange.R8_12), setOf(RepRange.R3_5, RepRange.R12_15, RepRange.TIME),
        60..120, 45..180, 0.4f..0.75f, 14..24, 3..5, RepRange.R8_12, 90,
        "Основной объём — 6–12 повторений с весом 65–80% от 1ПМ, последние 1–3 повтора близко к отказу.",
        "Отдых 60–120 секунд: хватает на качественный подход и сохраняет плотность тренировки.",
        "Начинайте каждый день с 1–2 базовых упражнений, изоляцию оставьте на конец."
    ),
    ENDURANCE(
        "Выносливость", setOf(RepRange.R15_20, RepRange.R20_PLUS, RepRange.TIME), setOf(RepRange.R12_15),
        0..60, 0..90, 0.3f..1f, 12..24, 3..5, RepRange.R15_20, 45,
        "Используйте 15–25 повторений с весом 40–60% от 1ПМ или работу на время 30–60 с.",
        "Сократите отдых до 30–60 секунд или объедините упражнения в круг без пауз.",
        "Добавьте многосуставные движения — они лучше развивают общую выносливость."
    ),
    FAT_LOSS(
        "Жиросжигание", setOf(RepRange.R8_12, RepRange.R12_15, RepRange.R15_20),
        setOf(RepRange.R6_8, RepRange.R20_PLUS, RepRange.TIME),
        0..60, 0..90, 0.6f..1f, 14..26, 3..5, RepRange.R12_15, 45,
        "Держите 10–15 повторений с умеренным весом: так сохраняются мышцы и растёт расход энергии.",
        "Отдых 30–60 секунд, используйте суперсеты или круговой формат.",
        "Больше многосуставных упражнений: они задействуют больше мышц и тратят больше энергии."
    ),
    GENERAL(
        "Общая форма", setOf(RepRange.R8_12, RepRange.R12_15),
        setOf(RepRange.R6_8, RepRange.R15_20, RepRange.TIME),
        45..120, 30..180, 0.5f..0.9f, 10..22, 2..4, RepRange.R8_12, 60,
        "Для общей формы подойдут 8–15 повторений с умеренным весом.",
        "Отдых 60–90 секунд между подходами.",
        "Добавьте базовое упражнение на каждую крупную группу мышц."
    )
}

