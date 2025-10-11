data class MyClassesResponse(val classes: List<MyClassItem>)

data class MyClassItem(
    val ClassId: Int,
    val LecturerId: String,
    val CourseId: String,
    val ClassCode: String,
    val LecturerRoleId: Int,
    val ClassYear: Int,
    val Semester: Int,
    val NumberOfSession: Int,
    val CourseCategory: String,
    val LecturerFullName: String,
    val CourseName: String,
    val Credit: Int
)

