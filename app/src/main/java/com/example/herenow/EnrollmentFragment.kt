package com.example.herenow

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.ListPopupWindow
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.herenow.data.ClassesRepository
import com.example.herenow.data.MyClassesResult
import com.example.herenow.data.ProfileRepository
import com.example.herenow.data.local.TokenManager
import com.example.herenow.databinding.FragmentEnrollmentBinding
import com.example.herenow.model.EnrollmentCourse
import com.facebook.shimmer.ShimmerFrameLayout
import kotlinx.coroutines.launch

class EnrollmentFragment : Fragment() {

    private data class SemesterItem(
        val key: String,   // contoh: "2022-1"
        val label: String  // contoh: "Semester 1 • 2022" (bebas tampilannya)
    )

    private var _binding: FragmentEnrollmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: EnrollmentAdapter
    private var allCourses: List<EnrollmentCourse> = emptyList()

    private lateinit var profileRepo: ProfileRepository
    private lateinit var classesRepo: ClassesRepository
    private lateinit var tokenManager: TokenManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnrollmentBinding.inflate(inflater, container, false)

        profileRepo = ProfileRepository(requireContext())
        classesRepo = ClassesRepository(requireContext())
        tokenManager = TokenManager(requireContext())

        // Header nama user
        showNameSkeleton(true)
        loadAndSetUserName()

        // Recycler + item click: KIRIM HANYA classId (room akan diambil di Detail dari /api/sessions/class/{classId})
        adapter = EnrollmentAdapter(emptyList()) { course ->
            val detail = DetailFragment.newInstance(
                classId = course.classId,
                room = "-",                         // Fallback saja; Detail akan menimpa dari sessions[*].roomId
                code = course.courseCode,
                title = course.courseTitle,
                type = course.courseCategory,       // atau course.classType bila ada
                instructor = course.instructor,
                creditsText = "${course.credits} Credits",
                totalSessions = course.totalSessions,
                selectedSession = -1                // AUTO_PICK di DetailFragment
            )

            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left,  R.anim.slide_out_right
                )
                .replace(R.id.nav_host_fragment, detail)
                .addToBackStack(null)
                .commit()
        }
        binding.rvEnrollment.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEnrollment.adapter = adapter

        // Tombol semester akan di-enable setelah data masuk
        binding.btnSemester.isEnabled = false
        binding.btnSemester.text = getString(R.string.loading)

        // Load data dari /api/my-classes
        loadMyClasses()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        showNameSkeleton(true)
        loadAndSetUserName()
    }

    /** GET /api/me untuk set txtName */
    private fun loadAndSetUserName() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!profileRepo.hasToken()) return@launch
            when (val res = profileRepo.fetchMe()) {
                is com.example.herenow.data.MeResult.Success -> {
                    binding.txtName?.text = res.data.StudentFullName
                    showNameSkeleton(false)
                }
                is com.example.herenow.data.MeResult.Unauthorized -> {
                    // biarkan skeleton tetap tampil; opsional: arahkan ke login di tempat lain
                }
                is com.example.herenow.data.MeResult.Failure -> {
                    // JANGAN tampilkan toast/pesan mentah (bisa ada IP)
                    // Biarkan skeleton tetap ON
                }
            }
        }
    }

    private fun showNameSkeleton(show: Boolean) {
        val shimmer = binding.shimmerName as? ShimmerFrameLayout
        if (show) {
            binding.txtName?.visibility = View.GONE
            shimmer?.visibility = View.VISIBLE
            shimmer?.startShimmer()
        } else {
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            binding.txtName?.visibility = View.VISIBLE
        }
    }


    /** GET /api/my-classes, map ke EnrollmentCourse, isi UI */
    private fun loadMyClasses() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = classesRepo.fetchMyEnrollments()) {
                is MyClassesResult.Success -> {
                    allCourses = res.enrollments
                    if (allCourses.isEmpty()) {
                        adapter.submitList(emptyList())
                        binding.btnSemester.isEnabled = false
                        binding.btnSemester.text = getString(R.string.no_data)
                    } else {
                        setupSemesterDropdownAndList(allCourses)
                    }
                }
                is MyClassesResult.Unauthorized -> {
                    tokenManager.clear()
                    //Toast.makeText(requireContext(), "Sesi berakhir. Silakan login ulang.", Toast.LENGTH_SHORT).show()
                    startActivity(android.content.Intent(requireContext(), LoginActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    requireActivity().finish()
                }
                is MyClassesResult.Failure -> {
                    //Toast.makeText(requireContext(), res.message, Toast.LENGTH_SHORT).show()
                    adapter.submitList(emptyList())
                    binding.btnSemester.isEnabled = false
                    binding.btnSemester.text = getString(R.string.no_data)
                }
            }
        }
    }

    /** Bangun daftar semester (unik) dan setup dropdown + list awal */
    private fun setupSemesterDropdownAndList(data: List<EnrollmentCourse>) {
        val semesters: List<SemesterItem> = data.map {
            val year = it.classYear?.toString() ?: "0"
            val semNum = it.semesterNumber?.toString()?.toIntOrNull() ?: 0

            val semLabel = if (semNum % 2 == 1) {
                "Odd Semester, $year"
            } else {
                "Even Semester, $year"
            }

            SemesterItem(
                key = "$year-$semNum",
                label = semLabel
            )
        }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.key.split("-")[0].toIntOrNull() ?: 0 },
                { it.key.split("-")[1].toIntOrNull() ?: 0 }))

        if (semesters.isEmpty()) {
            adapter.submitList(emptyList())
            binding.btnSemester.isEnabled = false
            binding.btnSemester.text = getString(R.string.no_data)
            return
        }

        // 🔹 Pilih semester terbaru (tahun & semester terbesar)
        val latestSem = semesters.maxWithOrNull(compareBy<SemesterItem> {
            it.key.split("-")[0].toIntOrNull() ?: 0
        }.thenBy {
            it.key.split("-")[1].toIntOrNull() ?: 0
        }) ?: semesters.last()

        // 🔹 Set default semester
        setSemester(latestSem.key, data)
        binding.btnSemester.isEnabled = true
        binding.btnSemester.text = latestSem.label

        binding.btnSemester.setOnClickListener { v ->
            val popup = ListPopupWindow(requireContext(), null).apply {
                anchorView = v
                isModal = true
                width = v.width

                setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        R.layout.item_dropdown_center,
                        semesters.map { it.label }
                    )
                )

                horizontalOffset = 0
                setDropDownGravity(Gravity.START)

                setOnItemClickListener { _, _, position, _ ->
                    val selected = semesters[position]
                    binding.btnSemester.text = selected.label
                    setSemester(selected.key, data)
                    dismiss()
                }

                // 🔽 Saat popup ditutup, panah kembali ke bawah
                setOnDismissListener {
                    binding.btnSemester.setCompoundDrawablesWithIntrinsicBounds(
                        null, null,
                        resources.getDrawable(R.drawable.ic_chevron_down, null),
                        null
                    )
                }
            }

            // 🔼 Saat popup ditampilkan, ubah ke arrow up
            binding.btnSemester.setCompoundDrawablesWithIntrinsicBounds(
                null, null,
                resources.getDrawable(R.drawable.ic_chevron_up, null),
                null
            )

            popup.show()
        }

    }




    private fun setSemester(semesterKey: String, data: List<EnrollmentCourse>) {
        // filter berdasarkan KEY stabil, bukan label
        val list = data
            .filter { "${it.classYear}-${it.semesterNumber}" == semesterKey }
            .sortedWith(compareBy({ it.courseCode }, { it.courseTitle }))
        adapter.submitList(list)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
