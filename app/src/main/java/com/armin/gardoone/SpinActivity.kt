package com.armin.gardoone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SpinActivity : AppCompatActivity() {

    private lateinit var wheel: WheelView
    private lateinit var tvResult: TextView
    private lateinit var tvRound: TextView
    private lateinit var btnMode: TextView
    private lateinit var hsHistory: HorizontalScrollView
    private lateinit var llHistory: LinearLayout

    private lateinit var wheels: MutableList<Wheel>
    private lateinit var wheelData: Wheel

    // active pool (for elimination mode)
    private var pool = mutableListOf<String>()
    private var history = mutableListOf<String>()
    private var mode = 0 // 0 normal, 1 elimination, 2 fair final (best of N)
    private var round = 0
    private var modeWins = mutableMapOf<String, Int>()
    private var finalTarget = 0

    private var soundPool: SoundPool? = null
    private var sndTick = 0
    private var sndWin = 0
    private var sndSpin = 0
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spin)

        wheel = findViewById(R.id.wheel)
        tvResult = findViewById(R.id.tv_result)
        tvRound = findViewById(R.id.tv_round)
        btnMode = findViewById(R.id.btn_mode)
        hsHistory = findViewById(R.id.hs_history)
        llHistory = findViewById(R.id.ll_history)

        wheels = Store.load(this)
        val id = intent.getLongExtra("id", -1L)
        wheelData = wheels.find { it.id == id } ?: wheels.firstOrNull()
            ?: run { finish(); return }

        pool = wheelData.items.toMutableList()
        findViewById<TextView>(R.id.tv_wheel_title).text = wheelData.title
        applyWheelStyle()

        // vibrate permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.VIBRATE), 1)
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
            .also { sp ->
                sndTick = sp.load(this, R.raw.tick, 1)
                sndWin = sp.load(this, R.raw.win, 1)
                sndSpin = sp.load(this, R.raw.spin, 1)
                sp.setOnLoadCompleteListener { _, _, _ -> ready = true }
            }

        wheel.onTick = { if (ready) soundPool?.play(sndTick, 1f, 1f, 1, 0, 1f) }
        wheel.onFinished = { winner -> onWinner(winner) }

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        btnMode.setOnClickListener {
            mode = (mode + 1) % 3
            resetPool()
            updateModeUi()
        }

        findViewById<Button>(R.id.btn_spin).setOnClickListener { doSpin() }
        updateRound()
    }

    private fun applyWheelStyle() {
        wheel.items = pool
        wheel.styleIndex = wheelData.styleIndex
        wheel.labelSize = wheelData.textSize.toFloat()
    }

    private fun resetPool() {
        pool = wheelData.items.toMutableList()
        history.clear()
        modeWins.clear()
        round = 0
        llHistory.removeAllViews()
        hsHistory.visibility = View.GONE
        tvResult.visibility = View.GONE
        applyWheelStyle()
        updateRound()
    }

    private fun updateModeUi() {
        when (mode) {
            0 -> {
                btnMode.text = "عادی"
                btnMode.setTextColor(ContextCompat.getColor(this, R.color.mode_normal))
            }
            1 -> {
                btnMode.text = "حذفی 🗑"
                btnMode.setTextColor(ContextCompat.getColor(this, R.color.mode_elim))
            }
            else -> {
                btnMode.text = "بهترین ${finalTargetLabel()}"
                btnMode.setTextColor(ContextCompat.getColor(this, R.color.mode_final))
            }
        }
        toast(
            when (mode) {
                0 -> "حالت عادلانه: ${if (wheelData.fair) "روشن" : "خاموش"}"
                1 -> "حالت حذفی: بازنده از گردونه خارج می‌شود"
                else -> "حالت فینال: ${finalTargetLabel()} چرخش"
            }
        )
    }

    private fun finalTargetLabel(): Int {
        if (finalTarget == 0) finalTarget = 5
        return finalTarget
    }

    private fun updateRound() {
        tvRound.text = when (mode) {
            0 -> "${pool.size} گزینه"
            1 -> "دور ${round + 1} • ${pool.size} باقی‌مانده"
            else -> "$round / ${finalTargetLabel()} • " + modeWins.entries.joinToString(" • ") { "${it.key}: ${it.value}" }
        }
    }

    private fun doSpin() {
        if (pool.size < 2) {
            if (mode == 1 && pool.size == 1) {
                tvResult.visibility = View.VISIBLE
                tvResult.text = "🏆 برنده نهایی: ${pool[0]}"
            } else toast("حداقل ۲ گزینه لازم است")
            return
        }
        tvResult.visibility = View.GONE
        if (ready) soundPool?.play(sndSpin, 1f, 1f, 1, 0, 1f)
        wheel.spin(durMs = wheelData.spinTime * 1000L, fair = wheelData.fair)
    }

    private fun onWinner(winner: String) {
        if (ready) soundPool?.play(sndWin, 1f, 1f, 2, 0, 1f)
        vibrate()
        tvResult.visibility = View.VISIBLE
        round++

        when (mode) {
            0 -> {
                tvResult.text = "🎉 $winner"
                addHistory(winner)
            }
            1 -> {
                tvResult.text = "🗑 خارج: $winner"
                addHistory(winner)
                pool.remove(winner)
                wheel.items = pool
                if (pool.size == 1) {
                    tvResult.text = "🏆 برنده نهایی: ${pool[0]}"
                    toast("قهرمان: ${pool[0]} 🏆")
                }
            }
            else -> {
                modeWins[winner] = (modeWins[winner] ?: 0) + 1
                addHistory(winner)
                if (round >= finalTargetLabel()) {
                    val champ = modeWins.maxByOrNull { it.value }
                    if (champ != null) {
                        val draw = modeWins.values.filter { it == champ.value }.size > 1
                        tvResult.text = if (draw) "🤝 مساوی: ${modeWins.entries.joinToString()}"
                        else "🏆 برنده: ${champ.key} (${champ.value} برد)"
                    }
                    finalTarget += 5 // next session offers +5
                    round = 0
                    modeWins.clear()
                }
            }
        }
        wheelData.spins++
        wheelData.lastUsed = System.currentTimeMillis()
        val i = wheels.indexOfFirst { it.id == wheelData.id }
        if (i >= 0) wheels[i] = wheelData
        Store.save(this, wheels)
        updateRound()
    }

    private fun addHistory(w: String) {
        history.add(w)
        hsHistory.visibility = View.VISIBLE
        val v = LayoutInflater.from(this).inflate(R.layout.chip_history, llHistory, false)
        v.findViewById<TextView>(R.id.tv_hist).text = w
        llHistory.addView(v)
        hsHistory.post { hsHistory.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun vibrate() {
        try {
            val vm = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                vm.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vm.vibrate(350)
            }
        } catch (_: Exception) {}
    }

    private fun toast(m: String) =
        Toast.makeText(this, m, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP, 0, 120)
        }.show()

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
    }
}
