package com.setholson.dailypuzzles.data

import com.setholson.dailypuzzles.model.DailyFermiPuzzle
import com.setholson.dailypuzzles.model.FermiGuessRecord
import com.setholson.dailypuzzles.model.FermiProgress
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object FermiScoring {

    /**
     * Computes the multiplicative estimation error: max(guess, answer) / min(guess, answer).
     * Exact matches return 1.0.
     */
    fun computeMultiple(guess: Double, answer: Double): Double {
        if (guess <= 0.0 || answer <= 0.0) return 1000.0
        val hi = maxOf(guess, answer)
        val lo = minOf(guess, answer)
        return hi / lo
    }

    fun directionLabel(guess: Double, answer: Double): String {
        val ratio = computeMultiple(guess, answer)
        if (ratio <= 1.005) return "Exact"
        return if (guess > answer) "High" else "Low"
    }

    /** Geometric mean of question multiples across today's estimation trio. */
    fun geometricMean(records: List<FermiGuessRecord>): Double {
        if (records.isEmpty()) return 0.0
        var product = 1.0
        for (r in records) {
            product *= r.multiple.coerceAtLeast(1.0)
        }
        return product.pow(1.0 / records.size.toDouble())
    }

    /**
     * Parses flexible Fermi estimates like "450000", "450,000", "450k", "1.2M", "3.5B", "2T", "1e6".
     */
    fun parseEstimate(raw: String): Double? {
        val cleaned = raw.trim().replace(",", "").replace(" ", "").uppercase(Locale.US)
        if (cleaned.isEmpty()) return null
        val multiplier = when {
            cleaned.endsWith("T") -> 1e12
            cleaned.endsWith("B") -> 1e9
            cleaned.endsWith("M") -> 1e6
            cleaned.endsWith("K") -> 1e3
            else -> 1.0
        }
        val numPart = if (multiplier > 1.0) cleaned.dropLast(1) else cleaned
        val base = numPart.toDoubleOrNull() ?: return null
        val value = base * multiplier
        return if (value.isFinite() && value > 0.0) value else null
    }

    fun formatMultiple(multiple: Double): String {
        return when {
            multiple >= 100.0 -> String.format(Locale.US, "%.0f\u00D7", multiple)
            multiple >= 10.0 -> String.format(Locale.US, "%.1f\u00D7", multiple)
            else -> String.format(Locale.US, "%.2f\u00D7", multiple)
        }
    }

    fun formatNumberCompact(value: Double): String {
        val a = abs(value)
        return when {
            a >= 1e12 -> String.format(Locale.US, "%.2fT", value / 1e12).trimTrailingZeros()
            a >= 1e9 -> String.format(Locale.US, "%.2fB", value / 1e9).trimTrailingZeros()
            a >= 1e6 -> String.format(Locale.US, "%.2fM", value / 1e6).trimTrailingZeros()
            a >= 1e4 -> String.format(Locale.US, "%.1fK", value / 1e3).trimTrailingZeros()
            a >= 100.0 -> NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())
            a >= 1.0 -> if (abs(value - value.toLong()) < 0.01) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", value).trimTrailingZeros()
            }
            else -> String.format(Locale.US, "%.3f", value).trimTrailingZeros()
        }
    }

    fun formatNumberExact(value: Double): String {
        return if (value >= 1e9) {
            formatNumberCompact(value)
        } else if (abs(value - value.toLong()) < 0.001) {
            NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    fun ratingLabel(multiple: Double): String = when {
        multiple <= 1.25 -> "Bullseye"
        multiple <= 2.0 -> "Sharp"
        multiple <= 5.0 -> "Close"
        multiple <= 15.0 -> "Order of magnitude"
        else -> "Way off"
    }

    /** Generates a clean spoiler-free Daily Fermi result summary. */
    fun buildShareText(puzzle: DailyFermiPuzzle, progress: FermiProgress): String {
        val geo = geometricMean(progress.records)
        val header = "Daily Fermi #${puzzle.puzzleNumber} \u00B7 ${formatMultiple(geo)} avg"
        val lines = progress.records.mapIndexed { idx, rec ->
            val icon = when {
                rec.multiple <= 1.5 -> "\u25CF" // ●
                rec.multiple <= 4.0 -> "\u25D0" // ◐
                else -> "\u25CB" // ○
            }
            "Q${idx + 1}: $icon ${formatMultiple(rec.multiple)} (${rec.direction})"
        }
        return buildString {
            append(header)
            append('\n')
            append(lines.joinToString(" | "))
        }
    }

    private fun String.trimTrailingZeros(): String {
        if (!contains('.')) return this
        val suffix = lastOrNull()?.takeIf { it in listOf('K', 'M', 'B', 'T') }
        val body = if (suffix != null) dropLast(1) else this
        val trimmed = body.trimEnd('0').trimEnd('.')
        return if (suffix != null) "$trimmed$suffix" else trimmed
    }
}
