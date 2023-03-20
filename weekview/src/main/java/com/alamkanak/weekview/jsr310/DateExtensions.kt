package com.alamkanak.weekview.jsr310

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

@RequiresApi(Build.VERSION_CODES.O)
internal fun Calendar.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(timeInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun Calendar.toLocalDateTime(): LocalDateTime {
    return Instant.ofEpochMilli(timeInMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun LocalDate.toCalendar(): Calendar {
    val instant = atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
    val calendar = Calendar.getInstance()
    calendar.time = Date.from(instant)
    return calendar
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun LocalDateTime.toCalendar(): Calendar {
    val instant = atZone(ZoneId.systemDefault()).toInstant()
    val calendar = Calendar.getInstance()
    calendar.time = Date.from(instant)
    return calendar
}
