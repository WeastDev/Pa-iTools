package com.weastdev.itools.picker

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Memberi jarak tipis dan merata antar sel grid (horizontal & vertical),
 * tanpa menambah padding di tepi luar RecyclerView (biar tetap rapat
 * ke tepi layar seperti di desain).
 */
class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingPx: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % spanCount
        val half = spacingPx / 2

        outRect.left = if (column == 0) 0 else half
        outRect.right = if (column == spanCount - 1) 0 else half
        outRect.top = half
        outRect.bottom = half
    }
}
