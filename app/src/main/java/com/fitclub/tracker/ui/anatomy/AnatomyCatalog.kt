package com.fitclub.tracker.ui.anatomy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class AtlasMuscle(val id: String, val name: String, val latin: String, val region: String,
    val fitness: Boolean, val role: String, val kind: String, val layer: String?, val origin: String?,
    val insertion: String?, val action: String?, val fitnessReason: String, val assets: List<String>,
    val sourceUrls: List<String>, val relatedIds: List<String>, val contentStatus: String) {
    val searchText = normalize("$name $latin $region ${if (id == "TA2:2464") "бицепс" else ""}")
}
internal fun normalize(s: String) = s.lowercase(Locale.ROOT).replace('ё', 'е').trim()
object AnatomyCatalog {
    val roles = linkedMapOf("movement" to "Движение", "stabilization" to "Стабилизация", "grip" to "Хват", "support" to "Дыхание и поддержка", "outside" to "Общий атлас")
    fun load(context: Context): List<AtlasMuscle> = context.assets.open("anatomy/catalog.json").bufferedReader().use { parse(it.readText()) }
    fun parse(json: String): List<AtlasMuscle> {
        val array = JSONArray(json)
        fun JSONObject.nullable(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
        return List(array.length()) { i ->
            val m = array.getJSONObject(i)
            val assets = m.getJSONArray("assets")
            val sources = m.getJSONArray("sources")
            val relations = m.getJSONArray("relations")
            AtlasMuscle(m.getString("id"), m.getString("name_ru"), m.getString("name_la"), m.getString("region_ru"),
                m.getInt("fitness") == 1, m.getString("fitness_role"), m.getString("entity_type"), m.nullable("layer_ru"),
                m.nullable("origin_ru"), m.nullable("insertion_ru"), m.nullable("action_ru"), m.getString("fitness_reason_ru"),
                List(assets.length()) { assets.getJSONObject(it).getString("asset_id") }.distinct(),
                List(sources.length()) { sources.getJSONObject(it).getString("url") }.distinct(),
                List(relations.length()) { val r = relations.getJSONObject(it); if (r.getString("parent_id") == m.getString("id")) r.getString("child_id") else r.getString("parent_id") }.distinct(),
                m.getString("content_status"))
        }
    }
    fun filter(all: List<AtlasMuscle>, query: String, fitnessOnly: Boolean, role: String?, region: String?): List<AtlasMuscle> {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.filter { m -> (!fitnessOnly || m.fitness) && (role == null || m.role == role) &&
            (region == null || m.region == region) && terms.all { it in m.searchText } }
    }
}
