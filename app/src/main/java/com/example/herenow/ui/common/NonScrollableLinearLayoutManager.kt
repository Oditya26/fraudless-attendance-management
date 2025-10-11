// NonScrollableLinearLayoutManager.kt
package com.example.herenow.ui.common

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

class NonScrollableLinearLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun canScrollVertically(): Boolean = false
}
