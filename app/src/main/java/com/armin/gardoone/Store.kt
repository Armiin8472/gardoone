package com.armin.gardoone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Wheel(
    var id: Long,
    var title: String,
    var tag: String,
    var items: MutableList<String>,
    var styleIndex: Int = 0,
    var textSize: Int = 16,   // progress 0..24 -> 12..36 sp
    var spinTime: Int = 4,    // progress 0..9 -> 2..11 sec
    var fair: Boolean = true,
    var spins: Int = 0,
    var lastUsed: Long = 0L
)

object Store {

    private const val FILE = "gardoone"
    private const val KEY = "wheels"

    fun load(ctx: Context): MutableList<Wheel> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaultWheels()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val items = mutableListOf<String>()
                val ia = o.getJSONArray("items")
                for (j in 0 until ia.length()) items.add(ia.getString(j))
                Wheel(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    tag = o.optString("tag", ""),
                    items = items,
                    styleIndex = o.optInt("style", 0),
                    textSize = o.optInt("textSize", 16),
                    spinTime = o.optInt("spinTime", 4),
                    fair = o.optBoolean("fair", true),
                    spins = o.optInt("spins", 0),
                    lastUsed = o.optLong("lastUsed", 0L)
                )
            }.toMutableList()
        } catch (_: Exception) {
            defaultWheels()
        }
    }

    fun save(ctx: Context, wheels: List<Wheel>) {
        val arr = JSONArray()
        wheels.forEach { w ->
            val o = JSONObject()
            o.put("id", w.id)
            o.put("title", w.title)
            o.put("tag", w.tag)
            val ia = JSONArray()
            w.items.forEach { ia.put(it) }
            o.put("items", ia)
            o.put("style", w.styleIndex)
            o.put("textSize", w.textSize)
            o.put("spinTime", w.spinTime)
            o.put("fair", w.fair)
            o.put("spins", w.spins)
            o.put("lastUsed", w.lastUsed)
            arr.put(o)
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun nextId(wheels: List<Wheel>): Long =
        (wheels.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun defaultWheels(): MutableList<Wheel> = mutableListOf(
        Wheel(1, "کشورها", "نمونه", mutableListOf("ایران", "عراق", "ترکیه", "چین", "برزیل", "فرانسه", "آلمان", "ژاپن")),
        Wheel(2, "غذاهای امروز", "غذا", mutableListOf("پیتزا", "برگر", "کباب", "ماکارونی", "سوشی", "ساندویچ")),
        Wheel(3, "بازیکن شماره", "فوتبال", mutableListOf("یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده", "یازده"))
    )
}
