package com.example.herenow.ui.common

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class HorizontalSpacingItemDecoration(
    private val horizontalSpace: Int,
    private val edgeSpace: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount

        outRect.left = if (position == 0) edgeSpace else horizontalSpace / 2
        outRect.right = if (position == itemCount - 1) edgeSpace else horizontalSpace / 2
    }
}
