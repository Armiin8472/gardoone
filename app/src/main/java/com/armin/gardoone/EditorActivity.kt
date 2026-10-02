package com.armin.gardoone

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class EditorActivity : AppCompatActivity() {

    private lateinit var wheels: MutableList<Wheel>
    private lateinit var wheel: Wheel
    private lateinit var adapter: ItemAdapter

    private val styleNames = arrayListOf("کلاسیک", "نئون", "پاستلی", "اقیانوسی", "غروب", "تک‌رنگ")
    private val styleColors = intArrayOf(
        Color.parseColor("#E74C3C"), Color.parseColor("#FF2E63"),
        Color.parseColor("#FFADAD"), Color.parseColor("#00B4D8"),
        Color.parseColor("#FF6B6B"), Color.parseColor("#2F3136")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        wheels = Store.load(this)
        val id = intent.getLongExtra("id", -1L)
        wheel = wheels.find { it.id == id } ?: wheels.firstOrNull()
            ?: run { finish(); return }

        val etTitle = findViewById<EditText>(R.id.et_title)
        val etTag = findViewById<EditText>(R.id.et_tag)
        val etItem = findViewById<EditText>(R.id.et_item)
        etTitle.setText(wheel.title)
        etTag.setText(wheel.tag)

        // styles
        val ll = findViewById<LinearLayout>(R.id.ll_styles)
        val chips = ArrayList<TextView>()
        styleNames.forEachIndexed { i, name ->
            val tv = TextView(this)
            tv.text = name
            tv.setTextColor(if (i == wheel.styleIndex) Color.WHITE else Color.parseColor("#CCD3E0"))
            tv.setBackgroundResource(R.drawable.bg_chip)
            tv.setPadding(dp(18), dp(10), dp(18), dp(10))
            tv.setOnClickListener {
                wheel.styleIndex = i
                chips.forEachIndexed { j, c ->
                    c.isSelected = j == i
                    c.setTextColor(if (j == i) Color.WHITE else Color.parseColor("#CCD3E0"))
                }
                toast("استایل: $name")
            }
            tv.isSelected = i == wheel.styleIndex
            chips.add(tv)
            ll.addView(tv)
        }

        // text size 12..36
        val sbText = findViewById<SeekBar>(R.id.sb_text)
        val tvSize = findViewById<TextView>(R.id.tv_text_size)
        sbText.progress = wheel.textSize - 12
        tvSize.text = "${wheel.textSize}sp"
        sbText.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                wheel.textSize = p + 12
                tvSize.text = "${wheel.textSize}sp"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // spin time 2..11s
        val sbTime = findViewById<SeekBar>(R.id.sb_time)
        val tvTime = findViewById<TextView>(R.id.tv_time)
        sbTime.progress = (wheel.spinTime - 2).coerceIn(0, 9)
        tvTime.text = "${wheel.spinTime}s"
        sbTime.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                wheel.spinTime = p + 2
                tvTime.text = "${wheel.spinTime}s"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val swFair = findViewById<SwitchCompat>(R.id.sw_fair)
        swFair.isChecked = wheel.fair
        swFair.setOnCheckedChangeListener { _, c -> wheel.fair = c }

        // items list
        val lv = findViewById<ListView>(R.id.lv_items)
        adapter = ItemAdapter()
        lv.adapter = adapter

        findViewById<TextView>(R.id.btn_add_item).setOnClickListener {
            val t = etItem.text.toString().trim()
            if (t.isEmpty()) { toast("متن خالی است"); return@setOnClickListener }
            if (wheel.items.contains(t)) { toast("تکراری است"); return@setOnClickListener }
            wheel.items.add(t)
            adapter.notifyDataSetChanged()
            etItem.setText("")
        }

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_save).setOnClickListener { save(); toast("ذخیره شد 💾") }
        findViewById<TextView>(R.id.btn_go_spin).setOnClickListener {
            save()
            startActivity(Intent(this, SpinActivity::class.java).putExtra("id", wheel.id))
        }
    }

    private fun save() {
        wheel.title = findViewById<EditText>(R.id.et_title).text.toString().trim()
            .ifBlank { "گردونه" }
        wheel.tag = findViewById<EditText>(R.id.et_tag).text.toString().trim()
        val i = wheels.indexOfFirst { it.id == wheel.id }
        if (i >= 0) wheels[i] = wheel else wheels.add(wheel)
        Store.save(this, wheels)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) =
        Toast.makeText(this, m, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP, 0, 120)
        }.show()

    inner class ItemAdapter : BaseAdapter() {
        override fun getCount() = wheel.items.size
        override fun getItem(p: Int) = wheel.items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(this@EditorActivity)
                .inflate(R.layout.row_item, parent, false)
            v.findViewById<TextView>(R.id.tv_name).text = wheel.items[pos]
            v.findViewById<ImageView>(R.id.btn_up).setOnClickListener {
                if (pos > 0) {
                    val it2 = wheel.items.removeAt(pos)
                    wheel.items.add(pos - 1, it2)
                    adapter.notifyDataSetChanged()
                }
            }
            v.findViewById<ImageView>(R.id.btn_delete).setOnClickListener {
                if (wheel.items.size <= 2) { toast("حداقل ۲ گزینه لازم است"); return@setOnClickListener }
                wheel.items.removeAt(pos)
                adapter.notifyDataSetChanged()
            }
            return v
        }
    }
}
