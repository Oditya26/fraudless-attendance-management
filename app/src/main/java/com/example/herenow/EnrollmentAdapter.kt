package com.example.herenow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.herenow.model.EnrollmentCourse

class EnrollmentAdapter(
    private var items: List<EnrollmentCourse>,
    private val onClick: (EnrollmentCourse) -> Unit
) : RecyclerView.Adapter<EnrollmentAdapter.EnrollVH>() {

    fun submitList(newItems: List<EnrollmentCourse>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class EnrollVH(v: View) : RecyclerView.ViewHolder(v) {
        val txtRoom: TextView = v.findViewById(R.id.txtRoomCode)
        val txtCourseCode: TextView = v.findViewById(R.id.txtCourseCode)
        val txtCourseTitle: TextView = v.findViewById(R.id.txtCourseTitle)
        val txtClassType: TextView = v.findViewById(R.id.txtClassType)
        val txtCredits: TextView = v.findViewById(R.id.txtCredits)       // ⬅️ baru
        val txtInstructor: TextView = v.findViewById(R.id.txtInstructor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnrollVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_enrollment_course, parent, false)
        return EnrollVH(view)
    }

    override fun onBindViewHolder(holder: EnrollVH, position: Int) {
        val item = items[position]
        holder.txtRoom.text = item.room
        holder.txtCourseCode.text = item.courseCode
        holder.txtCourseTitle.text = item.courseTitle
        holder.txtClassType.text = item.classType

        // Credits -> "X Credits"
        holder.txtCredits.text = "${item.credits} Credits"

        // Instructor -> "LecturerId - LecturerFullName"
        // Gabungkan hanya jika berbeda dan tidak kosong
        val id = item.lecturerId?.trim().orEmpty()
        val name = item.instructor?.trim().orEmpty()

        val instructorDisplay = when {
            id.isNotEmpty() && name.isNotEmpty() && !name.contains(id, ignoreCase = true) -> "$id - $name"
            name.isNotEmpty() -> name
            id.isNotEmpty() -> id
            else -> "-"
        }

        holder.txtInstructor.text = instructorDisplay


        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
