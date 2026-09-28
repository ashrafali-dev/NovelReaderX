package com.ashraf.novelreader

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

class SplitLayout(context: Context) : LinearLayout(context) {
    private val divider = View(context)
    private var downY = 0f
    private var startTop = 0
    private var top: View? = null
    private var bottom: View? = null

    init {
        orientation = VERTICAL
        divider.setBackgroundColor(Color.rgb(70, 70, 78))
        divider.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(6))
        divider.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startTop = top?.height ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = top ?: return@setOnTouchListener true
                    val b = bottom ?: return@setOnTouchListener true
                    val delta = (event.rawY - downY).toInt()
                    val available = height - divider.height
                    val min = dp(90)
                    val newTop = (startTop + delta).coerceIn(min, (available - min).coerceAtLeast(min))
                    p.layoutParams = (p.layoutParams as LayoutParams).apply { height = newTop; weight = 0f }
                    b.layoutParams = (b.layoutParams as LayoutParams).apply { height = available - newTop; weight = 0f }
                    requestLayout()
                    true
                }
                else -> false
            }
        }
    }

    fun setPanes(topView: View, bottomView: View) {
        removeAllViews()
        top = topView; bottom = bottomView
        addView(topView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(divider)
        addView(bottomView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun showSplit() {
        top?.visibility = View.VISIBLE
        bottom?.visibility = View.VISIBLE
        divider.visibility = View.VISIBLE
        top?.layoutParams = (top?.layoutParams as? LayoutParams)?.apply { height = 0; weight = 1f }
        bottom?.layoutParams = (bottom?.layoutParams as? LayoutParams)?.apply { height = 0; weight = 1f }
        requestLayout()
    }

    fun showNovelOnly() {
        top?.visibility = View.VISIBLE
        bottom?.visibility = View.GONE
        divider.visibility = View.GONE
        top?.layoutParams = (top?.layoutParams as? LayoutParams)?.apply { height = 0; weight = 1f }
        requestLayout()
    }

    fun showAiOnly() {
        top?.visibility = View.GONE
        bottom?.visibility = View.VISIBLE
        divider.visibility = View.GONE
        bottom?.layoutParams = (bottom?.layoutParams as? LayoutParams)?.apply { height = 0; weight = 1f }
        requestLayout()
    }

    fun resetHalf() = showSplit()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}