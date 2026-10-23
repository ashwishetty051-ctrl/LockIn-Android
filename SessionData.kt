package com.lockin.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val status: String
)

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: FocusSession)
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSession>>
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    suspend fun all(): List<FocusSession>
}

@Database(entities = [FocusSession::class], version = 1, exportSchema = false)
abstract class LockInDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}

data class ProgressSummary(
    val todayMinutes: Int,
    val todayCompleted: Int,
    val completed: Int,
    val interrupted: Int,
    val totalMinutes: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val weeklyMinutes: List<Int>
)

fun summarizeSessions(sessions: List<FocusSession>, today: LocalDate = LocalDate.now()): ProgressSummary {
    val complete = sessions.filter { it.status == "completed" }
    val interrupted = sessions.filter { it.status == "interrupted" }
    val todaySessions = sessions.filter { millisDate(it.endedAt) == today }
    val todayCompleted = complete.filter { millisDate(it.endedAt) == today }
    val daySet = complete.map { millisDate(it.endedAt) }.toSet()
    var current = 0
    var cursor = today
    while (cursor in daySet) {
        current++
        cursor = cursor.minusDays(1)
    }
    if (current == 0 && today.minusDays(1) in daySet) {
        cursor = today.minusDays(1)
        while (cursor in daySet) {
            current++
            cursor = cursor.minusDays(1)
        }
    }
    if (sessions.maxByOrNull { it.endedAt }?.status == "interrupted") current = 0
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    daySet.sorted().forEach { day ->
        run = if (previous != null && day == previous!!.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    val week = (6 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        sessions.filter { millisDate(it.endedAt) == day }.sumOf { it.actualMinutes }
    }
    return ProgressSummary(
        todaySessions.sumOf { it.actualMinutes },
        todayCompleted.size,
        complete.size,
        interrupted.size,
        complete.sumOf { it.actualMinutes } + interrupted.sumOf { it.actualMinutes },
        current,
        best,
        week
    )
}

private fun millisDate(time: Long): LocalDate =
    java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
