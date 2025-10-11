// WrapContentNonScrollableLinearLayoutManager.kt
package com.example.herenow.ui.common

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WrapContentNonScrollableLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun canScrollVertically(): Boolean = false

    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int
    ) {
        // Hitung tinggi total semua item (aman karena item sedikit)
        var totalHeight = 0
        val itemCount = state.itemCount
        for (i in 0 until itemCount) {
            val view = try {
                recycler.getViewForPosition(i)
            } catch (e: Exception) {
                // kalau adapter belum siap (diffing, dsb.), fallback ke super
                super.onMeasure(recycler, state, widthSpec, heightSpec)
                return
            }

            // wajib addView sebelum measure agar margin terhitung
            addView(view)
            measureChildWithMargins(view, 0, 0)

            val params = view.layoutParams as RecyclerView.LayoutParams
            val measured = getDecoratedMeasuredHeight(view) + params.topMargin + params.bottomMargin
            totalHeight += measured

            // Lepas & recycle view supaya tidak "nempel" di layout pass berikutnya
            detachAndScrapView(view, recycler)
        }

        val exactHeight = View.MeasureSpec.makeMeasureSpec(totalHeight, View.MeasureSpec.EXACTLY)
        super.onMeasure(recycler, state, widthSpec, exactHeight)
    }
}
