package com.example.herenow.data.remote.sessions

import com.google.gson.annotations.SerializedName

data class SessionsByDateResponse(
    @SerializedName("sessions") val sessions: List<SessionDto>
)

data class SessionDto(
    @SerializedName("SessionId") val sessionId: Int,
    @SerializedName("ClassId") val classId: Int,
    @SerializedName("SessionDate") val sessionDate: String, // "2022-09-01"
    @SerializedName("RoomId") val roomId: String,
    @SerializedName("SessionNumber") val sessionNumber: Int,
    @SerializedName("Shift") val shift: Int,
    @SerializedName("shift_start") val shiftStart: String,  // "07:20:00"
    @SerializedName("shift_end") val shiftEnd: String       // "09:00:00"
    // presences diabaikan utk kartu "sedang berlangsung"
)
