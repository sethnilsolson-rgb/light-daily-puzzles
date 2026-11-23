package com.setholson.dailypuzzles.data

import com.setholson.dailypuzzles.model.DailyWordlePuzzle
import com.setholson.dailypuzzles.model.WordleProgress
import java.text.NumberFormat
import java.util.Locale

enum class LetterEval(val priority: Int) {
    UNUSED(0),
    ABSENT(1),
    PRESENT(2),
    EXACT(3),
}

object WordleLogic {

    /**
     * Evaluates a 5-letter guess against a 5-letter solution using standard
     * duplicate-letter rules (EXACT takes precedence over PRESENT).
     */
    fun evaluateGuess(guess: String, solution: String): List<LetterEval> {
        val g = guess.uppercase(Locale.US)
        val s = solution.uppercase(Locale.US)
        require(g.length == 5 && s.length == 5) { "Daily Word entries must be 5 letters" }

        val result = MutableList(5) { LetterEval.ABSENT }
        val remaining = IntArray(26)

        // First pass: mark EXACT matches and count unmatched solution letters
        for (i in 0 until 5) {
            if (g[i] == s[i]) {
                result[i] = LetterEval.EXACT
            } else {
                val idx = s[i] - 'A'
                if (idx in 0..25) remaining[idx]++
            }
        }

        // Second pass: mark PRESENT for remaining letters
        for (i in 0 until 5) {
            if (result[i] == LetterEval.EXACT) continue
            val idx = g[i] - 'A'
            if (idx in 0..25 && remaining[idx] > 0) {
                result[i] = LetterEval.PRESENT
                remaining[idx]--
            }
        }

        return result
    }

    /** Merges all guesses to compute the highest-priority status for each A-Z key. */
    fun keyboardStates(guesses: List<String>, solution: String): Map<Char, LetterEval> {
        val map = mutableMapOf<Char, LetterEval>()
        for (guess in guesses) {
            if (guess.length != 5) continue
            val evals = evaluateGuess(guess, solution)
            for (i in 0 until 5) {
                val ch = guess[i].uppercaseChar()
                val cur = map[ch] ?: LetterEval.UNUSED
                if (evals[i].priority > cur.priority) {
                    map[ch] = evals[i]
                }
            }
        }
        return map
    }

    /**
     * Generates a clean spoiler-free Daily Word result summary.
     */
    fun buildShareText(puzzle: DailyWordlePuzzle, progress: WordleProgress): String {
        val numFormatted = NumberFormat.getIntegerInstance(Locale.US).format(puzzle.puzzleNumber)
        val score = if (progress.won) "${progress.guesses.size}/6" else "X/6"
        val gridLines = progress.guesses.map { guess ->
            evaluateGuess(guess, puzzle.solution).joinToString("") { eval ->
                when (eval) {
                    LetterEval.EXACT -> "\uD83D\uDFE9" // 🟩
                    LetterEval.PRESENT -> "\uD83D\uDFE8" // 🟨
                    else -> "\u2B1B" // ⬛
                }
            }
        }
        return buildString {
            append("Daily Word #")
            append(numFormatted)
            append(' ')
            append(score)
            append('\n')
            append(gridLines.joinToString("\n"))
        }
    }
}
