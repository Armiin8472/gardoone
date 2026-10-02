package com.armin.gardoone

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LibraryActivity : AppCompatActivity() {

    private lateinit var wheels: MutableList<Wheel>
    private lateinit var adapter: WheelAdapter
    private var filter = 0 // 0 all, 1 tagged, 2 untagged
    private var shown = mutableListOf<Wheel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        wheels = Store.load(this)
        adapter = WheelAdapter()
        val lv = findViewById<ListView>(R.id.lv_wheels)
        lv.adapter = adapter

        findViewById<TextView>(R.id.btn_new).setOnClickListener { newWheel() }

        val cAll = findViewById<TextView>(R.id.chip_all)
        val cTag = findViewById<TextView>(R.id.chip_tagged)
        val cUntag = findViewById<TextView>(R.id.chip_untagged)
        val chips = listOf(cAll, cTag, cUntag)
        fun applyFilter(f: Int) {
            filter = f
            chips.forEachIndexed { i, c -> c.isSelected = i == f }
            shown.clear()
            when (f) {
                1 -> shown.addAll(wheels.filter { it.tag.isNotBlank() })
                2 -> shown.addAll(wheels.filter { it.tag.isBlank() })
                else -> shown.addAll(wheels)
            }
            adapter.notifyDataSetChanged()
            findViewById<TextView>(R.id.tv_empty).visibility =
                if (shown.isEmpty()) View.VISIBLE else View.GONE
            lv.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
        }
        cAll.setOnClickListener { applyFilter(0) }
        cTag.setOnClickListener { applyFilter(1) }
        cUntag.setOnClickListener { applyFilter(2) }
        applyFilter(0)
    }

    override fun onResume() {
        super.onResume()
        wheels = Store.load(this)
        findViewById<TextView>(R.id.chip_all).performClick()
    }

    private fun newWheel() {
        val w = Wheel(
            id = Store.nextId(wheels),
            title = "گردونه ${wheels.size + 1}",
            tag = "",
            items = mutableListOf("گزینه ۱", "گزینه ۲", "گزینه ۳")
        )
        wheels.add(w)
        Store.save(this, wheels)
        startActivity(Intent(this, EditorActivity::class.java).putExtra("id", w.id))
    }

    private fun open(w: Wheel, cls: Class<*>) =
        startActivity(Intent(this, cls).putExtra("id", w.id))

    private fun delete(w: Wheel) {
        AlertDialog.Builder(this)
            .setTitle(w.title)
            .setMessage("این گردونه حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                wheels.remove(w)
                Store.save(this, wheels)
                findViewById<TextView>(R.id.chip_all).performClick()
                toast("حذف شد")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun copy(w: Wheel) {
        val c = w.copy(
            id = Store.nextId(wheels),
            title = w.title + " (کپی)",
            items = w.items.toMutableList()
        )
        wheels.add(c)
        Store.save(this, wheels)
        findViewById<TextView>(R.id.chip_all).performClick()
        toast("کپی شد ✅")
    }

    private fun toast(m: String) =
        Toast.makeText(this, m, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP, 0, 140)
        }.show()

    inner class WheelAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = shown[p].id
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(this@LibraryActivity)
                .inflate(R.layout.row_wheel, parent, false)
            val w = shown[pos]
            v.findViewById<TextView>(R.id.tv_title).text = w.title
            val tagTv = v.findViewById<TextView>(R.id.tv_tag)
            if (w.tag.isBlank()) tagTv.visibility = View.GONE else {
                tagTv.visibility = View.VISIBLE
                tagTv.text = w.tag
            }
            val used = if (w.lastUsed == 0L) "هرگز" else formatAgo(w.lastUsed)
            v.findViewById<TextView>(R.id.tv_meta).text =
                "${w.items.size} گزینه • ${w.spins} چرخش • آخرین استفاده: $used"
            v.findViewById<TextView>(R.id.btn_play).setOnClickListener { open(w, SpinActivity::class.java) }
            v.findViewById<TextView>(R.id.btn_edit).setOnClickListener { open(w, EditorActivity::class.java) }
            v.findViewById<TextView>(R.id.btn_copy).setOnClickListener { copy(w) }
            v.findViewById<TextView>(R.id.btn_delete).setOnClickListener { delete(w) }
            return v
        }
    }

    private fun formatAgo(ts: Long): String {
        val d = (System.currentTimeMillis() - ts) / 86_400_000L
        return when {
            d == 0L -> "امروز"
            d == 1L -> "دیروز"
            d < 30 -> "$d روز پیش"
            d < 365 -> "${d / 30} ماه پیش"
            else -> "${d / 365} سال پیش"
        }
    }
}
