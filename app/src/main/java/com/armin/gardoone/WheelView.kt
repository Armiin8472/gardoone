package com.armin.gardoone

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class WheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var items: MutableList<String> = mutableListOf()
        set(value) { field = value; invalidate() }

    var styleIndex: Int = 0
        set(value) { field = value; invalidate() }

    var labelSize: Float = 34f
        set(value) { field = value; invalidate() }

    var onFinished: ((String) -> Unit)? = null
    var onTick: (() -> Unit)? = null
    var spinning = false
        private set

    private var rotation = 0f
    private var lastTickIndex = -1
    private var anim: ValueAnimator? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1D23"); strokeWidth = 3f
    }

    // 0: classic, 1: neon, 2: pastel, 3: ocean, 4: sunset, 5: mono
    private val palettes = arrayOf(
        intArrayOf(
            Color.parseColor("#E74C3C"), Color.parseColor("#3498DB"),
            Color.parseColor("#2ECC71"), Color.parseColor("#F39C12"),
            Color.parseColor("#9B59B6"), Color.parseColor("#1ABC9C"),
            Color.parseColor("#E67E22"), Color.parseColor("#34495E"),
            Color.parseColor("#D35400"), Color.parseColor("#16A085"),
            Color.parseColor("#C0392B"), Color.parseColor("#8E44AD")
        ),
        intArrayOf(
            Color.parseColor("#FF2E63"), Color.parseColor("#08D9D6"),
            Color.parseColor("#252A34"), Color.parseColor("#EAEAEA"),
            Color.parseColor("#7F5AF0"), Color.parseColor("#2CB67D"),
            Color.parseColor("#FF8906"), Color.parseColor("#F25F4C"),
            Color.parseColor("#12E09C"), Color.parseColor("#70C1FF"),
            Color.parseColor("#FFD803"), Color.parseColor("#C3F73A")
        ),
        intArrayOf(
            Color.parseColor("#FFADAD"), Color.parseColor("#FFD6A5"),
            Color.parseColor("#FDFFB6"), Color.parseColor("#CAFFBF"),
            Color.parseColor("#9BF6FF"), Color.parseColor("#A0C4FF"),
            Color.parseColor("#BDB2FF"), Color.parseColor("#FFC6FF"),
            Color.parseColor("#FFFFFC"), Color.parseColor("#FDE2E4"),
            Color.parseColor("#D8E2DC"), Color.parseColor("#EECAD5")
        ),
        intArrayOf(
            Color.parseColor("#00B4D8"), Color.parseColor("#0077B6"),
            Color.parseColor("#023E8A"), Color.parseColor("#48CAE4"),
            Color.parseColor("#90E0EF"), Color.parseColor("#03045E"),
            Color.parseColor("#0096C7"), Color.parseColor("#ADE8F4"),
            Color.parseColor("#00B4D8"), Color.parseColor("#0077B6"),
            Color.parseColor("#48CAE4"), Color.parseColor("#023E8A")
        ),
        intArrayOf(
            Color.parseColor("#FF6B6B"), Color.parseColor("#EE5A24"),
            Color.parseColor("#F79F1F"), Color.parseColor("#FFC312"),
            Color.parseColor("#E1B12C"), Color.parseColor("#F3541D"),
            Color.parseColor("#D63031"), Color.parseColor("#FF793F"),
            Color.parseColor("#ED553B"), Color.parseColor("#FC5C65"),
            Color.parseColor("#FD9644"), Color.parseColor("#FA8231")
        ),
        intArrayOf(
            Color.parseColor("#2F3136"), Color.parseColor("#4F545C"),
            Color.parseColor("#72767D"), Color.parseColor("#2C2F33"),
            Color.parseColor("#5D5E62"), Color.parseColor("#36393F"),
            Color.parseColor("#8E9297"), Color.parseColor("#40444B"),
            Color.parseColor("#2F3136"), Color.parseColor("#666A6F"),
            Color.parseColor("#4F545C"), Color.parseColor("#72767D")
        )
    )

    fun cancel() {
        anim?.cancel()
        spinning = false
    }

    /** fair: pick winner first then animate; otherwise random landing angle */
    fun spin(durMs: Long = 4500L, fair: Boolean = true) {
        if (spinning || items.size < 2) return
        spinning = true
        lastTickIndex = -1
        val n = items.size
        val turns = Random.nextLong(4, 9)
        val targetIndex = Random.nextInt(n)
        val slice = 360f / n
        val target = if (fair) {
            // land targetIndex under the pointer (270deg canvas angle)
            val wanted = 270f - (targetIndex * slice + slice / 2f)
            var t = rotation + (turns * 360f)
            // normalize current rotation then add needed delta
            val base = rotation % 360f
            var delta = (wanted - base) % 360f
            if (delta < 0) delta += 360f
            t = rotation + delta + (turns * 360f)
            t
        } else {
            rotation + turns * 360f + Random.nextFloat() * 360f
        }
        anim = ValueAnimator.ofFloat(rotation, target).apply {
            duration = durMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                rotation = it.animatedValue as Float
                val w = winnerIndex()
                if (w != lastTickIndex) { lastTickIndex = w; onTick?.invoke() }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    spinning = false
                    rotation %= 360f
                    val winner = winnerIndex()
                    invalidate()
                    if (winner >= 0) onFinished?.invoke(items[winner])
                }
            })
            start()
        }
    }

    private fun winnerIndex(): Int {
        if (items.isEmpty()) return -1
        val n = items.size
        val slice = 360f / n
        val a = (270f - rotation) % 360f
        var idx = (a / slice).toInt()
        idx = ((idx % n) + n) % n
        return idx
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 12f
        val n = items.size
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val palette = palettes[styleIndex % palettes.size]

        if (n == 0) {
            paint.shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                Color.parseColor("#2A2F3A"), Color.parseColor("#1A1D26"),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
            textPaint.textSize = 40f
            canvas.drawText("گزینه‌ای نیست", cx, cy, textPaint)
            return
        }

        val slice = 360f / n

        for (i in 0 until n) {
            paint.color = palette[i % palette.size]
            canvas.drawArc(rect, rotation + i * slice, slice, true, paint)
        }

        // dim slices in elimination mode handled by caller via items; here plain
        textPaint.textSize = labelSize
        for (i in 0 until n) {
            val startA = rotation + i * slice
            val a0 = Math.toRadians(startA.toDouble())
            canvas.drawLine(cx, cy, cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat(), linePaint)

            val midDeg = startA + slice / 2
            val mid = Math.toRadians(midDeg.toDouble())
            val lx = cx + (r * 0.62f) * cos(mid).toFloat()
            val ly = cy + (r * 0.62f) * sin(mid).toFloat()
            canvas.save()
            canvas.rotate((midDeg + 90).toFloat(), lx, ly)
            // auto-shrink for many slices
            val fit = if (items[i].length > 8 && labelSize > 26f) 26f else labelSize
            textPaint.textSize = fit
            canvas.drawText(items[i].take(12), lx, ly - 10f, textPaint)
            canvas.restore()
        }

        paint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            Color.parseColor("#F1C40F"), Color.parseColor("#B7950B"),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        canvas.drawCircle(cx, cy, r + 2f, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#1A1D23")
        canvas.drawCircle(cx, cy, r * 0.11f, paint)
        paint.color = Color.parseColor("#F1C40F")
        canvas.drawCircle(cx, cy, r * 0.05f, paint)

        if (!spinning) {
            val w = winnerIndex()
            if (w >= 0) {
                paint.color = Color.parseColor("#55FFFFFF")
                canvas.drawArc(rect, rotation + w * slice, slice, true, paint)
            }
        }

        val tipY = (cy - r) + 46f
        paint.color = Color.parseColor("#FFD700")
        val ptr = Path().apply {
            moveTo(cx - 30f, 2f)
            lineTo(cx + 30f, 2f)
            lineTo(cx, tipY)
            close()
        }
        canvas.drawPath(ptr, paint)
        paint.color = Color.parseColor("#7D6608")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawPath(ptr, paint)
        paint.style = Paint.Style.FILL
    }
}
