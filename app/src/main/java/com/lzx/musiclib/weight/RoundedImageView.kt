package com.lzx.musiclib.weight

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import com.lzx.musiclib.R
import kotlin.math.min

/**
 * 圆角 / 圆形 [AppCompatImageView]，替代原 RCImageView。
 */
class RoundedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var cornerRadiusPx: Float = 0f
    private var roundAsCircle: Boolean = false

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RoundedImageView, defStyleAttr, 0)
        cornerRadiusPx = a.getDimension(R.styleable.RoundedImageView_round_corner, 0f)
        roundAsCircle = a.getBoolean(R.styleable.RoundedImageView_round_as_circle, false)
        a.recycle()
        applyOutline()
    }

    private fun applyOutline() {
        if (!roundAsCircle && cornerRadiusPx <= 0f) {
            clipToOutline = false
            outlineProvider = null
            return
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(host: View, outline: Outline) {
                val w = host.width
                val h = host.height
                if (w <= 0 || h <= 0) return
                if (roundAsCircle) {
                    val d = min(w, h)
                    val left = (w - d) / 2
                    val top = (h - d) / 2
                    outline.setOval(left, top, left + d, top + d)
                } else {
                    outline.setRoundRect(0, 0, w, h, cornerRadiusPx)
                }
            }
        }
        clipToOutline = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyOutline()
    }
}
