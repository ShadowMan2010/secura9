package com.secura9.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.floor
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Character sets used in the rain
    private val katakana = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン"
    private val latin    = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#\$%&"
    private val symbols  = "ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"
    private val allChars = (katakana + latin + symbols).toCharArray()

    // Rendering config
    private val fontSize   = 36f
    private val frameDelay = 50L  // ~20 fps

    // Column state
    private var columns    = 0
    private lateinit var drops: IntArray       // y position (in char units) per column
    private lateinit var speeds: IntArray      // how many frames to skip per column
    private lateinit var counters: IntArray    // frame counter per column
    private lateinit var chars: Array<CharArray> // current char grid (rows x cols)
    private lateinit var glowCols: BooleanArray // columns with a bright head

    private var rows = 0

    private var rainColor: Int = Color.argb(255, 0, 255, 70)

    // Paints
    private val paintHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.WHITE
        textSize = fontSize
        typeface = Typeface.MONOSPACE
        setShadowLayer(18f, 0f, 0f, Color.argb(255, 180, 255, 180))
    }
    private val paintBright = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintMid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintDim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBg = Paint().apply {
        color = Color.argb(30, 0, 0, 0)   // semi-transparent black for trail fade
    }

    fun setColor(newColor: Int) {
        rainColor = newColor
        val r = android.graphics.Color.red(newColor)
        val g = android.graphics.Color.green(newColor)
        val b = android.graphics.Color.blue(newColor)
        paintBright.color = android.graphics.Color.argb(255, r, g, b)
        paintBright.textSize = fontSize
        paintBright.typeface = Typeface.MONOSPACE
        paintBright.setShadowLayer(12f, 0f, 0f, android.graphics.Color.argb(200, r, g, b))
        paintMid.color = android.graphics.Color.argb(200, (r * 0.8).toInt(), (g * 0.8).toInt(), (b * 0.8).toInt())
        paintMid.textSize = fontSize
        paintMid.typeface = Typeface.MONOSPACE
        paintDim.color = android.graphics.Color.argb(100, (r * 0.6).toInt(), (g * 0.6).toInt(), (b * 0.6).toInt())
        paintDim.textSize = fontSize
        paintDim.typeface = Typeface.MONOSPACE
        paintFade.color = android.graphics.Color.argb(40, (r * 0.4).toInt(), (g * 0.4).toInt(), (b * 0.4).toInt())
        paintFade.textSize = fontSize
        paintFade.typeface = Typeface.MONOSPACE
        paintHead.setShadowLayer(18f, 0f, 0f, android.graphics.Color.argb(255, r.coerceAtLeast(180), g.coerceAtLeast(180), b.coerceAtLeast(180)))
    }

    // Handler for animation loop
    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            tick()
            invalidate()
            handler.postDelayed(this, frameDelay)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        columns = floor(w / fontSize).toInt()
        rows    = floor(h / fontSize).toInt() + 2

        drops    = IntArray(columns) { Random.nextInt(-rows, 0) }
        speeds   = IntArray(columns) { Random.nextInt(1, 4) }
        counters = IntArray(columns) { 0 }
        chars    = Array(rows) { CharArray(columns) { randomChar() } }
        glowCols = BooleanArray(columns) { false }
    }

    private fun randomChar() = allChars[Random.nextInt(allChars.size)]

    private fun tick() {
        if (columns == 0) return
        for (col in 0 until columns) {
            counters[col]++
            if (counters[col] < speeds[col]) continue
            counters[col] = 0

            val row = drops[col]
            // Randomly mutate some chars in the trail
            if (row > 0 && Random.nextFloat() < 0.15f) {
                val mutRow = Random.nextInt(maxOf(0, row - 8), row.coerceAtMost(rows - 1))
                chars[mutRow][col] = randomChar()
            }
            // Write new head char
            if (row in 0 until rows) {
                chars[row][col] = randomChar()
            }
            drops[col]++
            // Reset when off screen
            if (drops[col] > rows + Random.nextInt(10, 30)) {
                drops[col] = Random.nextInt(-20, -2)
                speeds[col] = Random.nextInt(1, 4)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Soft fade background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        if (columns == 0) return

        for (col in 0 until columns) {
            val headRow = drops[col]
            val x = col * fontSize

            for (row in 0 until rows) {
                val y = (row + 1) * fontSize
                val ch = chars[row][col].toString()
                val dist = headRow - row

                val paint = when {
                    dist == 0  -> paintHead
                    dist in 1..2 -> paintBright
                    dist in 3..6 -> paintMid
                    dist in 7..14 -> paintDim
                    dist in 15..24 -> paintFade
                    else -> null
                }
                paint?.let { canvas.drawText(ch, x, y, it) }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(animRunnable)
    }
}
