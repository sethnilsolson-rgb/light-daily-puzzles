package com.setholson.dailypuzzles.data

import com.setholson.dailypuzzles.model.LockoutStatus
import com.setholson.dailypuzzles.model.PuzzleSettings
import java.util.Calendar
import java.util.Locale

object LockoutPolicy {

    /**
     * Evaluates whether puzzles are currently locked based on:
     * 1. Manual "Lock Until Tomorrow" (`lockedUntilDateIso == todayIso`), or
     * 2. Optional Daily Unlock Window (`unlockStart..unlockEnd`, supports crossing midnight).
     */
    fun evaluate(settings: PuzzleSettings, todayIso: String, nowCal: Calendar = Calendar.getInstance()): LockoutStatus {
        if (settings.lockedUntilDateIso == todayIso) {
            return LockoutStatus(
                isLocked = true,
                reason = "Locked for the rest of today. Fresh puzzles return tomorrow.",
            )
        }
        if (!settings.lockoutScheduleEnabled) {
            return LockoutStatus(isLocked = false, reason = "")
        }

        val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val startMin = settings.unlockStartHour * 60 + settings.unlockStartMinute
        val endMin = settings.unlockEndHour * 60 + settings.unlockEndMinute

        val insideWindow = if (startMin == endMin) {
            true
        } else if (startMin < endMin) {
            nowMin in startMin until endMin
        } else {
            // Crosses midnight (e.g. 21:00 -> 01:00)
            nowMin >= startMin || nowMin < endMin
        }

        return if (insideWindow) {
            LockoutStatus(isLocked = false, reason = "")
        } else {
            val startStr = formatTime(settings.unlockStartHour, settings.unlockStartMinute)
            val endStr = formatTime(settings.unlockEndHour, settings.unlockEndMinute)
            LockoutStatus(
                isLocked = true,
                reason = "Puzzles open daily $startStr \u2013 $endStr.",
            )
        }
    }

    fun formatTime(hour24: Int, minute: Int): String {
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val amPm = if (hour24 < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
    }
}
