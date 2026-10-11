package com.fitclub.tracker.ui.anatomy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One exercise from the app catalog linked to a muscle. */
data class AtlasExercise(val exerciseId: String, val main: Boolean, val hint: String?)

/** A named piece of a muscle card: a part of the muscle or a member of a complex. */
data class AtlasName(val name: String, val latin: String)

/** One row of an origin/insertion schematic. */
data class AtlasAttachment(val part: String, val origin: String, val insertion: String)

data class AtlasMuscle(
    val id: String,
    val groupId: String,
    val name: String,
    val latin: String,
    val where: String?,
    val does: String?,
    val fitness: String?,
    val details: String?,
    val trainingNote: String?,
    val exercises: List<AtlasExercise>,
    val role: String? = null,
    val aliases: List<String> = emptyList(),
    val parts: List<AtlasName> = emptyList(),
    val members: List<AtlasName> = emptyList(),
    /** Present when the reviewed card replaced its illustration with an origin/insertion schematic. */
    val schematic: List<AtlasAttachment> = emptyList()
) {
    val hasIllustration get() = schematic.isEmpty()
    val image get() = "atlas/$id.webp"
    val thumbnail get() = "atlas/${id}_thumb.webp"
    internal val searchText = normalize("$name $latin ${aliases.joinToString(" ")}")
}

data class AtlasGroup(val id: String, val title: String, val muscles: List<AtlasMuscle>)

data class MuscleAtlas(val version: String, val groups: List<AtlasGroup>) {
    val muscles: List<AtlasMuscle> get() = groups.flatMap { it.muscles }
    fun group(id: String) = groups.firstOrNull { it.id == id }
    fun muscle(id: String) = muscles.firstOrNull { it.id == id }

    /** Russian or Latin search; every word must match, «ё» equals «е». */
    fun search(query: String): List<AtlasMuscle> {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return muscles.filter { m -> terms.all { it in m.searchText } }
    }

    /** Previous and next muscle in the same group, for paging between cards. */
    fun neighbours(id: String): Pair<AtlasMuscle?, AtlasMuscle?> {
        val list = muscle(id)?.let { group(it.groupId)?.muscles }.orEmpty()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null to null
        return list.getOrNull(i - 1) to list.getOrNull(i + 1)
    }

    companion object {
        const val PATH = "atlas/atlas.json"

        fun load(context: Context): MuscleAtlas =
            context.assets.open(PATH).bufferedReader().use { parse(it.readText()) }

        fun parse(json: String): MuscleAtlas {
            val root = JSONObject(json)
            fun JSONObject.text(key: String) = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
            val groups = root.getJSONArray("groups")
            return MuscleAtlas(root.optString("version"), List(groups.length()) { gi ->
                val g = groups.getJSONObject(gi)
                val muscles = g.getJSONArray("muscles")
                AtlasGroup(g.getString("id"), g.getString("title"), List(muscles.length()) { mi ->
                    val m = muscles.getJSONObject(mi)
                    val ex = m.optJSONArray("exercises") ?: JSONArray()
                    fun names(key: String) = m.optJSONArray(key)?.let { a ->
                        List(a.length()) { a.getJSONObject(it).let { o -> AtlasName(o.getString("name"), o.optString("latin")) } }
                    }.orEmpty()
                    val aliases = m.optJSONArray("aliases")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
                    val schematic = m.optJSONArray("schematic")?.let { a ->
                        List(a.length()) { a.getJSONObject(it).let { o -> AtlasAttachment(o.getString("part"), o.getString("origin"), o.getString("insertion")) } }
                    }.orEmpty()
                    AtlasMuscle(
                        m.getString("id"), g.getString("id"), m.getString("name"), m.getString("latin"),
                        m.text("where"), m.text("does"), m.text("fitness"), m.text("details"), m.text("trainingNote"),
                        List(ex.length()) { ei ->
                            val e = ex.getJSONObject(ei)
                            AtlasExercise(e.getString("id"), e.optString("role") == "main", e.text("hint"))
                        },
                        role = m.text("role"), aliases = aliases, parts = names("parts"), members = names("members"), schematic = schematic
                    )
                })
            })
        }
    }
}

internal fun normalize(s: String) = s.lowercase(Locale.ROOT).replace('ё', 'е').trim()

/** «1 мышца», «3 мышцы», «12 мышц». */
fun musclesWord(n: Int) = plural(n, "мышца", "мышцы", "мышц")
fun exercisesWord(n: Int) = plural(n, "упражнение", "упражнения", "упражнений")

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

/** Short filter label from a hint like «Верх · ключичная часть» → «Верх». */
internal fun hintLabel(hint: String) = hint.substringBefore(" · ").trim()

/** Filter chips are shown only when every linked exercise names the part of the muscle it targets. */
internal fun partFilters(exercises: List<AtlasExercise>): List<String> {
    if (exercises.isEmpty() || exercises.any { it.hint == null }) return emptyList()
    val labels = exercises.map { hintLabel(it.hint!!) }.distinct()
    return if (labels.size in 2..4) labels else emptyList()
}
