package com.example.herenow.data

import android.content.Context
import com.example.herenow.data.remote.core.RetrofitProvider
import com.example.herenow.data.remote.sessions.ClassMetaDto
import com.example.herenow.data.remote.sessions.ClassSessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

sealed class ClassDetailResult {
    data class Success(
        val clazz: ClassMetaDto,
        val sessions: List<ClassSessionDto>
    ) : ClassDetailResult()

    // pakai `object` agar kompatibel ke Kotlin < 1.9
    object Unauthorized : ClassDetailResult()

    data class Failure(val message: String) : ClassDetailResult()
}

class SessionsByClassRepository(ctx: Context) {
    private val api = RetrofitProvider.provideSessionsByClassApi(ctx)

    /**
     * Memanggil /api/sessions/class/{classId}
     * - Mengembalikan Success jika `clazz` tidak null (sessions boleh kosong)
     * - Sessions di-sort berdasarkan `sessionNumber`
     * - Menangani 401 sebagai Unauthorized
     * - Pesan error dari errorBody ditampilkan jika ada
     */
    suspend fun fetch(classId: Int): ClassDetailResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.byClass(classId)

            if (resp.isSuccessful) {
                val body = resp.body()
                val clazz = body?.clazz
                val sessions = (body?.sessions.orEmpty()).sortedBy { it.sessionNumber }

                return@withContext if (clazz != null) {
                    ClassDetailResult.Success(clazz, sessions)
                } else {
                    ClassDetailResult.Failure("Data kelas tidak ditemukan.")
                }
            } else {
                val code = resp.code()
                // errorBody hanya boleh dibaca sekali
                val rawErr = runCatching { resp.errorBody()?.string().orEmpty() }.getOrDefault("")
                return@withContext if (code == 401) {
                    ClassDetailResult.Unauthorized
                } else {
                    val msg = if (rawErr.isNotBlank()) "HTTP $code: $rawErr" else "HTTP $code"
                    ClassDetailResult.Failure(msg)
                }
            }
        } catch (e: HttpException) {
            // Cadangan jika dilempar sebagai exception
            return@withContext if (e.code() == 401) {
                ClassDetailResult.Unauthorized
            } else {
                ClassDetailResult.Failure("HTTP ${e.code()}: ${e.message()}")
            }
        } catch (e: IOException) {
            return@withContext ClassDetailResult.Failure("Tidak bisa terhubung ke server. Periksa koneksi internet Anda.")
        } catch (e: Exception) {
            return@withContext ClassDetailResult.Failure(e.localizedMessage ?: "Error tidak diketahui")
        }
    }
}
