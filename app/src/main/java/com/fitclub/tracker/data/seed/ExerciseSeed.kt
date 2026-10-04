package com.fitclub.tracker.data.seed

import com.fitclub.tracker.data.db.Alternative
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.ExerciseType
import com.fitclub.tracker.data.model.Muscle.*
import com.fitclub.tracker.data.model.MuscleGroup

/** Старые IDs сохраняются для программ и истории; импорт обновляет справочник без дублей этих IDs. */
object ExerciseSeed {
    val all: List<ExerciseEntity> by lazy {
        val legacyIds = legacy.map { it.id }.toSet()
        legacy + FreeExerciseCatalog.all.filterNot { it.id in legacyIds }
    }
    private val legacy: List<ExerciseEntity> = listOf(
        ExerciseEntity(
            id = "bench", name = "Жим штанги лёжа", group = MuscleGroup.CHEST,
            primary = listOf(CHEST), secondary = listOf(TRICEPS, DELTS),
            type = ExerciseType.BASIC, level = 1, equipment = "Штанга",
            technique = "Лягте на скамью, сведите лопатки и упритесь стопами в пол. Опустите гриф к нижней части груди, локти под углом около 45°, и выжмите вверх.",
            impact = "Главное упражнение для силы и массы груди. Заметно нагружает трицепс и передние дельты.",
            alternatives = listOf(Alternative("Жим гантелей лёжа", "гантели: больше амплитуда, безопаснее без страхующего"), Alternative("Жим в тренажёре сидя", "тренажёр: стабильная траектория для новичков"), Alternative("Отжимания от пола", "свой вес: дома или в разминке"))
        ),
        ExerciseEntity(
            id = "dbbench", name = "Жим гантелей лёжа", group = MuscleGroup.CHEST,
            primary = listOf(CHEST), secondary = listOf(TRICEPS, DELTS),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Гантели над грудью, ладони вперёд. Опускайте их по дуге до уровня груди с лёгким растяжением и выжимайте, сводя вверху.",
            impact = "Развивает грудь с большей амплитудой, чем штанга, и выравнивает силу левой и правой стороны.",
            alternatives = listOf(Alternative("Жим штанги лёжа", "штанга: больше рабочий вес"), Alternative("Отжимания от пола", "свой вес: без оборудования"))
        ),
        ExerciseEntity(
            id = "incline", name = "Жим гантелей на наклонной", group = MuscleGroup.CHEST,
            primary = listOf(CHEST), secondary = listOf(DELTS, TRICEPS),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Скамья под углом 30–45°. Опускайте гантели к верху груди и выжимайте, не разводя локти шире плеч.",
            impact = "Смещает акцент на верхнюю (ключичную) часть груди и передние дельты.",
            alternatives = listOf(Alternative("Жим штанги на наклонной", "штанга: больше вес при той же траектории"), Alternative("Жим в тренажёре сидя", "тренажёр: проще освоить"))
        ),
        ExerciseEntity(
            id = "flyes", name = "Разведение гантелей лёжа", group = MuscleGroup.CHEST,
            primary = listOf(CHEST), secondary = listOf(DELTS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Гантели",
            technique = "Руки слегка согнуты в локтях и зафиксированы. Разводите гантели в стороны до растяжения груди и сводите по той же дуге.",
            impact = "Изолированно растягивает и нагружает грудные без участия трицепса. Хорошо в конце тренировки.",
            alternatives = listOf(Alternative("Сведение рук в кроссовере", "блок: постоянное натяжение по всей амплитуде"), Alternative("Сведения в тренажёре «бабочка»", "тренажёр: безопаснее для плеч"))
        ),
        ExerciseEntity(
            id = "cross", name = "Сведение рук в кроссовере", group = MuscleGroup.CHEST,
            primary = listOf(CHEST), secondary = listOf(DELTS),
            type = ExerciseType.ISOLATION, level = 2, equipment = "Блок",
            technique = "Шаг вперёд, корпус слегка наклонён. Сводите рукояти перед собой по дуге и задерживайтесь на секунду в пиковом сокращении.",
            impact = "Даёт постоянное натяжение и пиковое сокращение груди. Высота блоков смещает акцент.",
            alternatives = listOf(Alternative("Разведение гантелей лёжа", "гантели: если блок занят"), Alternative("Отжимания от пола", "свой вес: вне зала"))
        ),
        ExerciseEntity(
            id = "dips", name = "Отжимания на брусьях", group = MuscleGroup.CHEST,
            primary = listOf(CHEST, TRICEPS), secondary = listOf(DELTS),
            type = ExerciseType.BASIC, level = 2, equipment = "Свой вес",
            technique = "Наклоните корпус вперёд, локти слегка в стороны. Опускайтесь до угла 90° в локтях и выжимайте себя вверх.",
            impact = "Мощно нагружает низ груди и трицепс. Требует здоровых плеч и хорошей мобильности.",
            alternatives = listOf(Alternative("Отжимания в гравитроне", "тренажёр: противовес облегчает движение"), Alternative("Жим гантелей лёжа", "гантели: меньше нагрузка на плечевой сустав"))
        ),
        ExerciseEntity(
            id = "pushup", name = "Отжимания от пола", group = MuscleGroup.CHEST,
            primary = listOf(CHEST), secondary = listOf(TRICEPS, DELTS, ABS),
            type = ExerciseType.BASIC, level = 1, equipment = "Свой вес",
            technique = "Упор лёжа, тело прямое от макушки до пяток. Опуститесь, почти касаясь пола грудью, и выжмитесь вверх.",
            impact = "Базовое упражнение со своим весом для груди и трицепса, заодно тренирует кор.",
            alternatives = listOf(Alternative("Жим гантелей лёжа", "гантели: когда отжимания стали лёгкими"), Alternative("Отжимания на брусьях", "свой вес: более тяжёлый вариант"))
        ),
        ExerciseEntity(
            id = "pullup", name = "Подтягивания", group = MuscleGroup.BACK,
            primary = listOf(LATS), secondary = listOf(BICEPS, FOREARMS, TRAPS),
            type = ExerciseType.BASIC, level = 2, equipment = "Турник",
            technique = "Хват чуть шире плеч. Тянитесь грудью к перекладине, сводя лопатки, и опускайтесь до полного выпрямления рук.",
            impact = "Лучшее упражнение со своим весом для ширины спины. Хорошо развивает бицепс и хват.",
            alternatives = listOf(Alternative("Тяга верхнего блока", "блок: можно точно подобрать вес"), Alternative("Подтягивания в гравитроне", "тренажёр: противовес для новичков"))
        ),
        ExerciseEntity(
            id = "latpull", name = "Тяга верхнего блока", group = MuscleGroup.BACK,
            primary = listOf(LATS), secondary = listOf(BICEPS, DELTS),
            type = ExerciseType.BASIC, level = 1, equipment = "Блок",
            technique = "Сидя, бёдра зафиксированы. Тяните рукоять к верху груди, опуская локти вниз и назад, затем плавно отпускайте.",
            impact = "Развивает ширину спины. Удобна для тех, кто пока не подтягивается.",
            alternatives = listOf(Alternative("Подтягивания", "свой вес: следующий шаг по сложности"), Alternative("Тяга гантели одной рукой", "гантели: если блок занят"))
        ),
        ExerciseEntity(
            id = "bbrow", name = "Тяга штанги в наклоне", group = MuscleGroup.BACK,
            primary = listOf(LATS, TRAPS), secondary = listOf(BICEPS, LOWER_BACK, DELTS),
            type = ExerciseType.BASIC, level = 2, equipment = "Штанга",
            technique = "Наклон корпуса около 45°, спина прямая. Тяните гриф к низу живота, сводя лопатки, и опускайте под контролем.",
            impact = "Строит толщину спины: широчайшие, трапеции, ромбовидные. Нагружает поясницу, требует техники.",
            alternatives = listOf(Alternative("Тяга гантели одной рукой", "гантели: меньше нагрузка на поясницу"), Alternative("Тяга горизонтального блока", "блок: спина с опорой"))
        ),
        ExerciseEntity(
            id = "dbrow", name = "Тяга гантели одной рукой", group = MuscleGroup.BACK,
            primary = listOf(LATS), secondary = listOf(BICEPS, DELTS),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Колено и рука на скамье, спина параллельна полу. Тяните гантель к поясу, ведя локоть вдоль корпуса.",
            impact = "Прорабатывает каждую сторону спины отдельно, помогает убрать дисбаланс.",
            alternatives = listOf(Alternative("Тяга штанги в наклоне", "штанга: больше вес"), Alternative("Тяга горизонтального блока", "блок: постоянное натяжение"))
        ),
        ExerciseEntity(
            id = "cablerow", name = "Тяга горизонтального блока", group = MuscleGroup.BACK,
            primary = listOf(LATS, TRAPS), secondary = listOf(BICEPS),
            type = ExerciseType.BASIC, level = 1, equipment = "Блок",
            technique = "Сидя, спина прямая, ноги слегка согнуты. Тяните рукоять к животу, сводя лопатки, без раскачки корпусом.",
            impact = "Развивает середину спины и осанку. Безопаснее для поясницы, чем тяги в наклоне.",
            alternatives = listOf(Alternative("Тяга гантели одной рукой", "гантели: вариант без тренажёра"), Alternative("Тяга штанги в наклоне", "штанга: для опытных"))
        ),
        ExerciseEntity(
            id = "deadlift", name = "Становая тяга", group = MuscleGroup.BACK,
            primary = listOf(LOWER_BACK, GLUTES, HAMS), secondary = listOf(TRAPS, QUADS, FOREARMS, LATS),
            type = ExerciseType.BASIC, level = 3, equipment = "Штанга",
            technique = "Гриф над серединой стопы, спина нейтральная. Поднимайте штангу, разгибая ноги и таз одновременно, гриф скользит вдоль ног.",
            impact = "Самое тяжёлое базовое движение: развивает всю заднюю цепь и общую силу. Требует отточенной техники.",
            alternatives = listOf(Alternative("Румынская тяга", "штанга: меньше вес, акцент на бицепс бедра"), Alternative("Гиперэкстензия", "тренажёр: для укрепления поясницы"))
        ),
        ExerciseEntity(
            id = "hyper", name = "Гиперэкстензия", group = MuscleGroup.BACK,
            primary = listOf(LOWER_BACK), secondary = listOf(GLUTES, HAMS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Валики под тазом, руки на груди. Наклонитесь вниз с прямой спиной и поднимитесь до прямой линии тела, не переразгибаясь.",
            impact = "Укрепляет разгибатели спины и ягодицы, хорошо подходит для профилактики болей в пояснице.",
            alternatives = listOf(Alternative("Румынская тяга с гантелями", "гантели: вариант без тренажёра"), Alternative("Становая тяга", "штанга: для продвинутых"))
        ),
        ExerciseEntity(
            id = "ohp", name = "Армейский жим стоя", group = MuscleGroup.SHOULDERS,
            primary = listOf(DELTS), secondary = listOf(TRICEPS, TRAPS, ABS),
            type = ExerciseType.BASIC, level = 2, equipment = "Штанга",
            technique = "Гриф на передних дельтах, ягодицы и пресс напряжены. Выжмите штангу над головой и опустите обратно к груди.",
            impact = "Главное базовое упражнение на плечи: сила жима и стабилизация корпуса.",
            alternatives = listOf(Alternative("Жим гантелей сидя", "гантели: меньше нагрузка на поясницу"), Alternative("Жим в тренажёре на плечи", "тренажёр: для новичков"))
        ),
        ExerciseEntity(
            id = "dbpress", name = "Жим гантелей сидя", group = MuscleGroup.SHOULDERS,
            primary = listOf(DELTS), secondary = listOf(TRICEPS),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Спинка скамьи вертикально. Выжимайте гантели от уровня ушей вверх, сводя их над головой.",
            impact = "Нагружает передние и средние дельты. Спина с опорой — проще держать технику.",
            alternatives = listOf(Alternative("Армейский жим стоя", "штанга: больше вес"), Alternative("Жим в тренажёре на плечи", "тренажёр: стабильная траектория"))
        ),
        ExerciseEntity(
            id = "lateral", name = "Махи гантелями в стороны", group = MuscleGroup.SHOULDERS,
            primary = listOf(DELTS), secondary = listOf(TRAPS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Гантели",
            technique = "Лёгкий наклон вперёд, локти чуть согнуты. Поднимайте гантели через стороны до уровня плеч, ведя движение локтями.",
            impact = "Изолированно развивает средние дельты и визуально расширяет плечи.",
            alternatives = listOf(Alternative("Отведение руки в кроссовере", "блок: нагрузка даже в нижней точке"), Alternative("Махи в тренажёре", "тренажёр: стабильная траектория"))
        ),
        ExerciseEntity(
            id = "reardelt", name = "Разведения в наклоне", group = MuscleGroup.SHOULDERS,
            primary = listOf(DELTS), secondary = listOf(TRAPS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Гантели",
            technique = "Наклон почти параллельно полу. Разводите гантели в стороны, сводя лопатки, без рывков.",
            impact = "Прорабатывает задние дельты, улучшает осанку и баланс плечевого пояса.",
            alternatives = listOf(Alternative("Тяга каната к лицу", "блок: задние дельты и ротаторы"), Alternative("Обратная «бабочка»", "тренажёр"))
        ),
        ExerciseEntity(
            id = "facepull", name = "Тяга каната к лицу", group = MuscleGroup.SHOULDERS,
            primary = listOf(DELTS), secondary = listOf(TRAPS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Блок",
            technique = "Блок на уровне лица. Тяните канат к переносице, разводя концы и выводя локти вверх и в стороны.",
            impact = "Укрепляет задние дельты и вращательную манжету плеча, полезна для здоровья плеч.",
            alternatives = listOf(Alternative("Разведения в наклоне", "гантели"), Alternative("Тяга резинки к лицу", "эспандер: для дома"))
        ),
        ExerciseEntity(
            id = "bbcurl", name = "Подъём штанги на бицепс", group = MuscleGroup.BICEPS,
            primary = listOf(BICEPS), secondary = listOf(FOREARMS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Штанга",
            technique = "Стоя, локти прижаты к корпусу. Сгибайте руки, поднимая гриф к плечам без раскачки, и медленно опускайте.",
            impact = "Классика для массы бицепса: позволяет работать с бо́льшим весом, чем гантели.",
            alternatives = listOf(Alternative("Подъём гантелей с супинацией", "гантели: полная супинация кисти"), Alternative("Сгибания на нижнем блоке", "блок: постоянное натяжение"))
        ),
        ExerciseEntity(
            id = "dbcurl", name = "Подъём гантелей с супинацией", group = MuscleGroup.BICEPS,
            primary = listOf(BICEPS), secondary = listOf(FOREARMS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Гантели",
            technique = "Начните с ладонями к корпусу и разворачивайте кисть наружу по ходу подъёма. Локти неподвижны.",
            impact = "Нагружает бицепс и в сгибании, и в супинации — две его основные функции.",
            alternatives = listOf(Alternative("Подъём штанги на бицепс", "штанга: больше вес"), Alternative("Молотковые сгибания", "гантели: акцент на плечевую мышцу"))
        ),
        ExerciseEntity(
            id = "hammer", name = "Молотковые сгибания", group = MuscleGroup.BICEPS,
            primary = listOf(BICEPS, FOREARMS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Гантели",
            technique = "Хват нейтральный — ладони смотрят друг на друга. Поднимайте гантели, не отводя локти от корпуса.",
            impact = "Развивает плечевую мышцу и предплечье, делает руку визуально толще.",
            alternatives = listOf(Alternative("Сгибания с канатом на блоке", "блок"), Alternative("Обратные сгибания", "штанга: больше нагрузка на предплечья"))
        ),
        ExerciseEntity(
            id = "preacher", name = "Сгибания на скамье Скотта", group = MuscleGroup.BICEPS,
            primary = listOf(BICEPS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 2, equipment = "EZ-гриф",
            technique = "Плечо лежит на подушке. Опускайте гриф почти до полного разгибания и поднимайте, не отрывая локти.",
            impact = "Исключает читинг и нагружает бицепс в растянутом положении.",
            alternatives = listOf(Alternative("Сгибания в тренажёре", "тренажёр"), Alternative("Концентрированный подъём", "гантель: сидя, локоть на бедре"))
        ),
        ExerciseEntity(
            id = "closegrip", name = "Жим узким хватом", group = MuscleGroup.TRICEPS,
            primary = listOf(TRICEPS), secondary = listOf(CHEST, DELTS),
            type = ExerciseType.BASIC, level = 2, equipment = "Штанга",
            technique = "Хват на ширине плеч, локти вдоль корпуса. Опускайте гриф к низу груди и выжимайте за счёт трицепса.",
            impact = "Базовое упражнение для массы трицепса, позволяет работать с большими весами.",
            alternatives = listOf(Alternative("Отжимания на брусьях", "свой вес: вертикальный корпус"), Alternative("Узкие отжимания от пола", "свой вес: вне зала"))
        ),
        ExerciseEntity(
            id = "skull", name = "Французский жим лёжа", group = MuscleGroup.TRICEPS,
            primary = listOf(TRICEPS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 2, equipment = "EZ-гриф",
            technique = "Лёжа, гриф над лбом, плечи неподвижны. Сгибайте локти, опуская гриф ко лбу, и разгибайте.",
            impact = "Сильно растягивает и нагружает длинную головку трицепса.",
            alternatives = listOf(Alternative("Разгибание гантели из-за головы", "гантель"), Alternative("Разгибания на блоке", "блок: мягче для локтей"))
        ),
        ExerciseEntity(
            id = "pushdown", name = "Разгибания на блоке", group = MuscleGroup.TRICEPS,
            primary = listOf(TRICEPS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Блок",
            technique = "Локти прижаты к бокам. Разгибайте руки вниз до конца, задержитесь и медленно верните рукоять.",
            impact = "Простая изоляция трицепса, щадящая к локтям. Подходит новичкам.",
            alternatives = listOf(Alternative("Французский жим лёжа", "штанга: больше растяжение"), Alternative("Обратные отжимания от скамьи", "свой вес"))
        ),
        ExerciseEntity(
            id = "overhead", name = "Разгибание гантели из-за головы", group = MuscleGroup.TRICEPS,
            primary = listOf(TRICEPS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Гантели",
            technique = "Сидя, гантель двумя руками над головой. Опускайте её за голову, держа локти вверх, и разгибайте.",
            impact = "Акцент на длинную головку трицепса в растянутом положении.",
            alternatives = listOf(Alternative("Разгибания с канатом из-за головы", "блок"), Alternative("Французский жим лёжа", "штанга"))
        ),
        ExerciseEntity(
            id = "wristcurl", name = "Сгибания запястий", group = MuscleGroup.FOREARMS,
            primary = listOf(FOREARMS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Штанга",
            technique = "Предплечья на скамье, кисти свисают. Опускайте гриф к пальцам и сгибайте запястья вверх.",
            impact = "Развивает сгибатели предплечья и силу хвата.",
            alternatives = listOf(Alternative("Сгибания запястий с гантелями", "гантели"), Alternative("Кистевой эспандер", "эспандер: дома"))
        ),
        ExerciseEntity(
            id = "farmer", name = "Прогулка фермера", group = MuscleGroup.FOREARMS,
            primary = listOf(FOREARMS, TRAPS), secondary = listOf(ABS, GLUTES),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Возьмите тяжёлые гантели, плечи назад, корпус прямой. Идите короткими шагами 20–40 метров.",
            impact = "Функциональное упражнение: хват, трапеции и стабилизация корпуса одновременно.",
            alternatives = listOf(Alternative("Удержание штанги", "штанга: статика на время"), Alternative("Вис на турнике", "свой вес"))
        ),
        ExerciseEntity(
            id = "revcurl", name = "Обратные сгибания", group = MuscleGroup.FOREARMS,
            primary = listOf(FOREARMS), secondary = listOf(BICEPS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Штанга",
            technique = "Хват сверху, локти у корпуса. Поднимайте гриф к плечам, не сгибая запястья.",
            impact = "Нагружает разгибатели предплечья и плечелучевую мышцу, выравнивает силу рук.",
            alternatives = listOf(Alternative("Молотковые сгибания", "гантели"), Alternative("Обратные сгибания на блоке", "блок"))
        ),
        ExerciseEntity(
            id = "squat", name = "Приседания со штангой", group = MuscleGroup.QUADS,
            primary = listOf(QUADS, GLUTES), secondary = listOf(HAMS, LOWER_BACK, ABS),
            type = ExerciseType.BASIC, level = 2, equipment = "Штанга",
            technique = "Гриф на трапециях, стопы на ширине плеч. Садитесь, отводя таз назад, до параллели бедра с полом и вставайте, сохраняя спину нейтральной.",
            impact = "Король упражнений для ног: развивает квадрицепсы, ягодицы и общую силу.",
            alternatives = listOf(Alternative("Жим ногами", "тренажёр: без осевой нагрузки на позвоночник"), Alternative("Гоблет-присед", "гантель: чтобы освоить технику"))
        ),
        ExerciseEntity(
            id = "legpress", name = "Жим ногами", group = MuscleGroup.QUADS,
            primary = listOf(QUADS), secondary = listOf(GLUTES, HAMS),
            type = ExerciseType.BASIC, level = 1, equipment = "Тренажёр",
            technique = "Спина прижата к спинке. Опускайте платформу до угла 90° в коленях и выжимайте, не выпрямляя колени до замка.",
            impact = "Позволяет безопасно работать с большим весом на ноги без нагрузки на спину.",
            alternatives = listOf(Alternative("Приседания со штангой", "штанга: больше работы для кора"), Alternative("Гоблет-присед", "гантель"))
        ),
        ExerciseEntity(
            id = "goblet", name = "Гоблет-присед", group = MuscleGroup.QUADS,
            primary = listOf(QUADS), secondary = listOf(GLUTES, ABS),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Гантель вертикально у груди, локти вниз. Приседайте глубоко между коленями, держа корпус вертикально.",
            impact = "Отличный вход в приседания: учит правильной механике и нагружает квадрицепсы.",
            alternatives = listOf(Alternative("Приседания со штангой", "штанга: следующий шаг"), Alternative("Жим ногами", "тренажёр"))
        ),
        ExerciseEntity(
            id = "lunge", name = "Выпады с гантелями", group = MuscleGroup.QUADS,
            primary = listOf(QUADS, GLUTES), secondary = listOf(HAMS),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Шагните вперёд и опуститесь, пока заднее колено почти не коснётся пола. Оттолкнитесь передней ногой.",
            impact = "Тренирует каждую ногу отдельно, а также баланс и стабилизаторы.",
            alternatives = listOf(Alternative("Болгарские выпады", "гантели: сложнее и глубже"), Alternative("Выпады в Смите", "тренажёр: проще баланс"))
        ),
        ExerciseEntity(
            id = "legext", name = "Разгибания ног", group = MuscleGroup.QUADS,
            primary = listOf(QUADS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Валик над голеностопом. Разгибайте ноги до конца, задержитесь на секунду и медленно опустите.",
            impact = "Изолированно прорабатывает квадрицепсы. Полезно как добивка или разминка колен.",
            alternatives = listOf(Alternative("Сисси-присед", "свой вес"), Alternative("Гоблет-присед", "гантель"))
        ),
        ExerciseEntity(
            id = "frontsquat", name = "Фронтальные приседания", group = MuscleGroup.QUADS,
            primary = listOf(QUADS), secondary = listOf(GLUTES, ABS),
            type = ExerciseType.BASIC, level = 3, equipment = "Штанга",
            technique = "Гриф на передних дельтах, локти высоко. Приседайте с вертикальным корпусом как можно глубже.",
            impact = "Сильнее смещает нагрузку на квадрицепсы и кор. Требует мобильности запястий и голеностопа.",
            alternatives = listOf(Alternative("Гоблет-присед", "гантель: похожая механика"), Alternative("Приседания в Хакке", "тренажёр"))
        ),
        ExerciseEntity(
            id = "rdl", name = "Румынская тяга", group = MuscleGroup.HAMSTRINGS,
            primary = listOf(HAMS), secondary = listOf(GLUTES, LOWER_BACK),
            type = ExerciseType.BASIC, level = 2, equipment = "Штанга",
            technique = "Колени слегка согнуты. Отводите таз назад, опуская гриф вдоль ног до середины голени, и поднимайтесь, разгибая таз.",
            impact = "Лучшее упражнение для бицепса бедра в растяжении, также развивает ягодицы.",
            alternatives = listOf(Alternative("Румынская тяга с гантелями", "гантели: легче освоить"), Alternative("Сгибания ног лёжа", "тренажёр: без нагрузки на спину"))
        ),
        ExerciseEntity(
            id = "dbrdl", name = "Румынская тяга с гантелями", group = MuscleGroup.HAMSTRINGS,
            primary = listOf(HAMS), secondary = listOf(GLUTES, LOWER_BACK),
            type = ExerciseType.BASIC, level = 1, equipment = "Гантели",
            technique = "Гантели вдоль бёдер. Наклоняйтесь с прямой спиной, отводя таз назад, до растяжения задней поверхности бедра.",
            impact = "Мягкий вариант румынской тяги для освоения движения тазом.",
            alternatives = listOf(Alternative("Румынская тяга", "штанга: больше вес"), Alternative("Гиперэкстензия", "тренажёр"))
        ),
        ExerciseEntity(
            id = "legcurl", name = "Сгибания ног лёжа", group = MuscleGroup.HAMSTRINGS,
            primary = listOf(HAMS), secondary = listOf(CALVES),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Валик над пятками, таз прижат к скамье. Сгибайте ноги к ягодицам и медленно опускайте.",
            impact = "Изолированно нагружает бицепс бедра в укороченном положении.",
            alternatives = listOf(Alternative("Сгибания ног сидя", "тренажёр: чуть больше растяжение"), Alternative("Нордические сгибания", "свой вес: для продвинутых"))
        ),
        ExerciseEntity(
            id = "nordic", name = "Нордические сгибания", group = MuscleGroup.HAMSTRINGS,
            primary = listOf(HAMS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 3, equipment = "Свой вес",
            technique = "Стоя на коленях, пятки зафиксированы. Медленно опускайте корпус вперёд, сопротивляясь бицепсом бедра.",
            impact = "Очень интенсивная эксцентрическая нагрузка, снижает риск травм задней поверхности бедра.",
            alternatives = listOf(Alternative("Сгибания ног лёжа", "тренажёр: легче"), Alternative("Румынская тяга с гантелями", "гантели"))
        ),
        ExerciseEntity(
            id = "hipthrust", name = "Ягодичный мост со штангой", group = MuscleGroup.GLUTES,
            primary = listOf(GLUTES), secondary = listOf(HAMS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Штанга",
            technique = "Лопатки на скамье, гриф на тазу. Поднимайте таз до прямой линии от плеч до коленей, сжимая ягодицы вверху.",
            impact = "Максимально нагружает ягодичные в сокращённом положении.",
            alternatives = listOf(Alternative("Ягодичный мост в тренажёре", "тренажёр: удобнее установка"), Alternative("Ягодичный мост с гантелью", "гантель: дома или новичкам"))
        ),
        ExerciseEntity(
            id = "bulgarian", name = "Болгарские выпады", group = MuscleGroup.GLUTES,
            primary = listOf(GLUTES, QUADS), secondary = listOf(HAMS),
            type = ExerciseType.BASIC, level = 2, equipment = "Гантели",
            technique = "Задняя нога на скамье. Опускайтесь вертикально вниз с лёгким наклоном корпуса вперёд — так акцент уходит на ягодицы.",
            impact = "Одно из самых эффективных упражнений на ягодицы и квадрицепсы, развивает баланс.",
            alternatives = listOf(Alternative("Выпады с гантелями", "гантели: проще"), Alternative("Зашагивания на платформу", "гантели"))
        ),
        ExerciseEntity(
            id = "abduction", name = "Отведение бедра в тренажёре", group = MuscleGroup.GLUTES,
            primary = listOf(GLUTES), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Сидя, спина прижата. Разводите ноги в стороны, задержитесь и медленно сведите.",
            impact = "Прорабатывает среднюю ягодичную мышцу, отвечающую за стабильность таза.",
            alternatives = listOf(Alternative("Отведение с резинкой", "эспандер: дома"), Alternative("Отведение ноги назад в кроссовере", "блок"))
        ),
        ExerciseEntity(
            id = "kickback", name = "Отведение ноги назад в кроссовере", group = MuscleGroup.GLUTES,
            primary = listOf(GLUTES), secondary = listOf(HAMS),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Блок",
            technique = "Манжета на щиколотке, корпус слегка наклонён. Отводите прямую ногу назад, не прогибая поясницу.",
            impact = "Изолированно нагружает большую ягодичную мышцу, хорошо как добивка.",
            alternatives = listOf(Alternative("Махи ногой назад в тренажёре", "тренажёр"), Alternative("Ягодичный мост со штангой", "штанга: больше вес"))
        ),
        ExerciseEntity(
            id = "standcalf", name = "Подъёмы на носки стоя", group = MuscleGroup.CALVES,
            primary = listOf(CALVES), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Носки на краю платформы. Опускайте пятки максимально вниз и поднимайтесь на носки как можно выше.",
            impact = "Развивает икроножную мышцу при прямых коленях.",
            alternatives = listOf(Alternative("Подъёмы на носки с гантелями", "гантели"), Alternative("Подъёмы на носки в жиме ногами", "тренажёр"))
        ),
        ExerciseEntity(
            id = "seatcalf", name = "Подъёмы на носки сидя", group = MuscleGroup.CALVES,
            primary = listOf(CALVES), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Валик на бёдрах, колени согнуты. Поднимайтесь на носки и медленно опускайте пятки вниз.",
            impact = "Акцент на камбаловидную мышцу, которая лежит под икроножной.",
            alternatives = listOf(Alternative("Подъёмы на носки стоя", "тренажёр"), Alternative("Подъёмы на носки сидя с гантелью", "гантель"))
        ),
        ExerciseEntity(
            id = "presscalf", name = "Подъёмы на носки в жиме ногами", group = MuscleGroup.CALVES,
            primary = listOf(CALVES), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Тренажёр",
            technique = "Носки на нижнем краю платформы, колени прямые. Толкайте платформу носками, затем отпускайте до растяжения.",
            impact = "Удобная альтернатива подъёмам стоя без нагрузки на спину.",
            alternatives = listOf(Alternative("Подъёмы на носки стоя", "тренажёр"), Alternative("Прыжки на скакалке", "свой вес: заодно кардио"))
        ),
        ExerciseEntity(
            id = "plank", name = "Планка", group = MuscleGroup.CORE,
            primary = listOf(ABS), secondary = listOf(DELTS, GLUTES),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Свой вес",
            technique = "Упор на предплечья, тело прямое, ягодицы и пресс напряжены. Удерживайте положение, не проваливая поясницу.",
            impact = "Развивает статическую выносливость мышц кора и стабилизацию позвоночника.",
            alternatives = listOf(Alternative("Боковая планка", "свой вес: акцент на косые"), Alternative("Ролик для пресса", "ролик: сложнее"))
        ),
        ExerciseEntity(
            id = "crunch", name = "Скручивания", group = MuscleGroup.CORE,
            primary = listOf(ABS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 1, equipment = "Свой вес",
            technique = "Лёжа, колени согнуты. Поднимайте плечи от пола, скругляя грудной отдел, без рывков за шею.",
            impact = "Базовая изоляция прямой мышцы живота, подходит новичкам.",
            alternatives = listOf(Alternative("Скручивания на блоке", "блок: можно добавлять вес"), Alternative("Подъёмы ног в висе", "турник: сложнее"))
        ),
        ExerciseEntity(
            id = "hangraise", name = "Подъёмы ног в висе", group = MuscleGroup.CORE,
            primary = listOf(ABS), secondary = listOf(FOREARMS),
            type = ExerciseType.ISOLATION, level = 3, equipment = "Турник",
            technique = "В висе на перекладине поднимайте прямые или согнутые ноги, подкручивая таз вверх, без раскачки.",
            impact = "Мощная нагрузка на нижнюю часть пресса и сгибатели бедра, развивает хват.",
            alternatives = listOf(Alternative("Подъёмы ног в упоре на брусьях", "тренажёр: проще"), Alternative("Скручивания", "свой вес: легче"))
        ),
        ExerciseEntity(
            id = "cablecrunch", name = "Скручивания на блоке", group = MuscleGroup.CORE,
            primary = listOf(ABS), secondary = listOf(),
            type = ExerciseType.ISOLATION, level = 2, equipment = "Блок",
            technique = "Стоя на коленях, канат у головы. Скручивайтесь вниз, приближая локти к бёдрам. Таз неподвижен.",
            impact = "Позволяет прогрессировать весом, как в любом силовом упражнении.",
            alternatives = listOf(Alternative("Скручивания", "свой вес"), Alternative("Ролик для пресса", "ролик"))
        ),
        ExerciseEntity(
            id = "wheel", name = "Ролик для пресса", group = MuscleGroup.CORE,
            primary = listOf(ABS), secondary = listOf(LATS, DELTS),
            type = ExerciseType.ISOLATION, level = 2, equipment = "Ролик",
            technique = "С колен выкатывайте ролик вперёд, держа пресс напряжённым и не прогибая поясницу, затем вернитесь.",
            impact = "Сильная нагрузка на кор против прогиба, подключает широчайшие и плечи.",
            alternatives = listOf(Alternative("Планка", "свой вес: легче"), Alternative("Подъёмы ног в висе", "турник"))
        )
    )
}
