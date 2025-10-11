package com.example.herenow

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.herenow.R
import com.example.herenow.model.Schedule
import java.time.LocalDate

@RequiresApi(Build.VERSION_CODES.O)
class CalendarAdapter(
    private val days: List<Int?>,
    private val monthDate: LocalDate, // bulan yang sedang ditampilkan
    private val schedules: List<Schedule>,
    private val onDateClick: (LocalDate, List<Schedule>) -> Unit,
    initialSelectedDate: LocalDate? = null
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    private val today: LocalDate = LocalDate.now()
    private var selectedPosition: Int = -1

    init {
        // Preselect jika initialSelectedDate berada di bulan yang sama
        initialSelectedDate?.let { initSel ->
            if (initSel.year == monthDate.year && initSel.month == monthDate.month) {
                val pos = days.indexOfFirst { it == initSel.dayOfMonth }
                if (pos != -1) selectedPosition = pos
            }
        }
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val todayOutline: View = itemView.findViewById(R.id.todayOutline)   // NEW
        val selectionBg: View = itemView.findViewById(R.id.selectionBg)
        val txtDay: TextView = itemView.findViewById(R.id.txtDay)
        val eventDot: View = itemView.findViewById(R.id.eventDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(v)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: DayViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val day = days[position]
        if (day == null) {
            holder.todayOutline.visibility = View.GONE
            holder.selectionBg.visibility = View.GONE
            holder.txtDay.text = ""
            holder.txtDay.isSelected = false
            holder.eventDot.visibility = View.GONE
            return
        }

        val currentDate = LocalDate.of(monthDate.year, monthDate.month, day)
        val isSelected = (position == selectedPosition)
        val isToday = (currentDate == today)

        // Teks
        holder.txtDay.text = day.toString()

        // Dot event
        val daySchedules = schedules.filter { it.date == currentDate }
        holder.eventDot.visibility = if (daySchedules.isNotEmpty()) View.VISIBLE else View.GONE

        // Seleksi solid
        holder.selectionBg.visibility = if (isSelected) View.VISIBLE else View.GONE

        // Outline hari ini (kecil & biru), hanya saat TIDAK selected
        holder.todayOutline.visibility = if (isToday && !isSelected) View.VISIBLE else View.GONE

        // Warna teks
        holder.txtDay.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                holder.itemView.context,
                if (isSelected) android.R.color.white else R.color.day_text_color
            )
        )

        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = position
            if (prev != -1) notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onDateClick(currentDate, daySchedules)
        }
    }

    override fun getItemCount(): Int = days.size
}
