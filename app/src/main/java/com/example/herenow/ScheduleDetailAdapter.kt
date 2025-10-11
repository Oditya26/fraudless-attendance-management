package com.example.herenow

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.example.herenow.model.Schedule
import com.example.herenow.R
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleDetailAdapter(
    private val schedules: List<Schedule>,
    private val resolveCourseTitle: (String) -> String,
    private val resolveCourseCategory: (Schedule) -> String,   // ⬅️ baru
    private val onMoreDetailClick: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleDetailAdapter.VH>() {

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale("id", "ID"))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtRoom: TextView = itemView.findViewById(R.id.txtScheduleRoom)
        val txtCourse: TextView = itemView.findViewById(R.id.txtScheduleCourse)
        val txtSession: TextView = itemView.findViewById(R.id.txtSession)
        val txtDate: TextView = itemView.findViewById(R.id.txtScheduleDate)
        val txtTime: TextView = itemView.findViewById(R.id.txtScheduleTime)
        val txtCourseCategory: TextView = itemView.findViewById(R.id.txtCourseCategory) // ⬅️ baru
        val btnMore: Button = itemView.findViewById(R.id.btnMoreDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_detail, parent, false)
        return VH(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = schedules[position]
        holder.txtRoom.text = s.room

        val title = resolveCourseTitle(s.courseCode)
        holder.txtCourse.text = "${s.courseCode} - $title"

        holder.txtSession.text = s.session.toString()
        holder.txtDate.text = s.date.format(dateFormatter)
        holder.txtTime.text = s.time

        // ⬅️ set Course Category
        holder.txtCourseCategory.text = resolveCourseCategory(s)

        holder.btnMore.setOnClickListener { onMoreDetailClick(s) }
    }

    override fun getItemCount(): Int = schedules.size
}
