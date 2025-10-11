package com.example.herenow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private val sessions: List<Int>,
    initiallySelected: Int = -1,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    private var selectedPos: Int = sessions.indexOf(initiallySelected)

    inner class VH(item: View) : RecyclerView.ViewHolder(item) {
        val txt: TextView = item.findViewById(R.id.txtSession)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_chip, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val value = sessions[position]
        holder.txt.text = value.toString()
        holder.txt.isSelected = (position == selectedPos)
        holder.itemView.setOnClickListener {
            val prev = selectedPos
            selectedPos = position
            if (prev != -1) notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onSelect(value)
        }
    }

    override fun getItemCount(): Int = sessions.size
}
