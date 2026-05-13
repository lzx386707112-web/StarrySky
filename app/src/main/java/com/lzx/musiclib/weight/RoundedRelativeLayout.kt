package com.lzx.musiclib.weight

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RelativeLayout
import com.lzx.musiclib.R

/**
 * 圆角 [RelativeLayout]，子 View 绘制裁剪到圆角矩形内，替代原 RCRelativeLayout。
 */
open class RoundedRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private var cornerRadiusPx: Float = 0f

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RoundedRelativeLayout, defStyleAttr, 0)
        cornerRadiusPx = a.getDimension(R.styleable.RoundedRelativeLayout_round_corner, 0f)
        a.recycle()
        applyOutline()
    }

    private fun applyOutline() {
        if (cornerRadiusPx <= 0f) {
            clipToOutline = false
            outlineProvider = null
            return
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(host: View, outline: Outline) {
                outline.setRoundRect(0, 0, host.width, host.height, cornerRadiusPx)
            }
        }
        clipToOutline = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyOutline()
    }
}
