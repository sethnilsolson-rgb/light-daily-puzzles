package com.setholson.dailypuzzles

import com.setholson.dailypuzzles.data.ChessColor
import com.setholson.dailypuzzles.data.ChessEngine
import com.setholson.dailypuzzles.data.FermiScoring
import com.setholson.dailypuzzles.data.LetterEval
import com.setholson.dailypuzzles.data.LockoutPolicy
import com.setholson.dailypuzzles.data.OfflinePuzzleBank
import com.setholson.dailypuzzles.data.PieceType
import com.setholson.dailypuzzles.data.WordleLogic
import com.setholson.dailypuzzles.model.DailyCrosswordPuzzle
import com.setholson.dailypuzzles.model.DailyFermiPuzzle
import com.setholson.dailypuzzles.model.DailyWordlePuzzle
import com.setholson.dailypuzzles.model.FermiGuessRecord
import com.setholson.dailypuzzles.model.FermiProgress
import com.setholson.dailypuzzles.model.PuzzleSettings
import com.setholson.dailypuzzles.model.WordleProgress
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PuzzlesTest {

    @Test
    fun chessEngine_solvesMultiMovePromotionAndMate() {
        val fen = "1R6/4r1pk/2p3Rp/1b1p3P/3BP3/r4PP1/p1P4K/8 w - - 0 1"
        val pgn = """
            [Event "Daily Puzzle"]
            [Result "*"]

            1. f4 a1=Q 2. Rxh6+ Kxh6 3. Rh8# *
        """.trimIndent()

        val moves = ChessEngine.extractPgnMoves(pgn)
        assertEquals(listOf("f4", "a1=Q", "Rxh6+", "Kxh6", "Rh8#"), moves)

        var pos = ChessEngine.parseFen(fen)
        assertEquals(ChessColor.WHITE, pos.turn)

        for (san in moves) {
            val move = ChessEngine.parseSanMove(pos, san)
            assertNotNull(move, "Failed to resolve SAN move '$san'")
            if (san == "a1=Q") assertEquals(PieceType.QUEEN, move.promotion)
            pos = ChessEngine.applyMove(pos, move)
        }
        assertTrue(ChessEngine.isCheckmate(pos))
    }

    @Test
    fun dailyChessBank_allSeedsAreLegalAndEndInCheckmate() {
        assertTrue(OfflinePuzzleBank.chessSeeds.size >= 24)
        var whiteCount = 0
        var blackCount = 0

        for (seed in OfflinePuzzleBank.chessSeeds) {
            var pos = ChessEngine.parseFen(seed.fen)
            if (pos.turn == ChessColor.WHITE) whiteCount++ else blackCount++

            for ((idx, san) in seed.moves.withIndex()) {
                val move = ChessEngine.parseSanMove(pos, san)
                assertNotNull(move, "Chess seed '${seed.title}' failed SAN '$san' at ply $idx")
                pos = ChessEngine.applyMove(pos, move)
            }
            assertTrue(
                ChessEngine.isCheckmate(pos),
                "Chess seed '${seed.title}' must end in checkmate (moves=${seed.moves})",
            )
        }
        assertTrue(whiteCount >= 10 && blackCount >= 8, "Should have a healthy mix of White & Black puzzles")
    }

    @Test
    fun dailyWordBank_allSolutionsAreUniqueValidFiveLetterWordsInDictionary() {
        val wordsFile = File("src/main/assets/words5.txt")
        assertTrue(wordsFile.exists(), "words5.txt must exist at ${wordsFile.absolutePath}")
        val validWords = wordsFile.readLines()
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.length == 5 }
            .toSet()

        val solutions = OfflinePuzzleBank.dailyWordSolutions
        assertTrue(solutions.size >= 365, "Expected >= 365 daily words, found ${solutions.size}")
        assertEquals(solutions.size, solutions.toSet().size, "Daily words must be unique")

        val missing = solutions.filter { it !in validWords }
        assertTrue(missing.isEmpty(), "Solution words missing from words5.txt: $missing")

        for (w in solutions) {
            assertEquals(5, w.length, "Word '$w' must be 5 letters")
            assertTrue(w.all { it in 'A'..'Z' }, "Word '$w' must be uppercase A-Z")
        }
    }

    @Test
    fun dailyWordLogic_handlesDuplicateLettersAndShareText() {
        val eval1 = WordleLogic.evaluateGuess("POPPY", "UPEND")
        assertEquals(
            listOf(LetterEval.PRESENT, LetterEval.ABSENT, LetterEval.ABSENT, LetterEval.ABSENT, LetterEval.ABSENT),
            eval1,
        )

        val eval2 = WordleLogic.evaluateGuess("SPEED", "UPEND")
        assertEquals(
            listOf(LetterEval.ABSENT, LetterEval.EXACT, LetterEval.EXACT, LetterEval.ABSENT, LetterEval.EXACT),
            eval2,
        )

        val puzzle = DailyWordlePuzzle(
            dateIso = "2026-11-23",
            puzzleNumber = 327,
            solution = "UPEND",
        )
        val prog = WordleProgress(
            dateIso = "2026-11-23",
            guesses = listOf("CRANE", "UPEND"),
            won = true,
            completed = true,
        )
        val share = WordleLogic.buildShareText(puzzle, prog)
        assertTrue(share.startsWith("Daily Word #327 2/6\n"))
        assertTrue(share.contains("\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9"))
    }

    @Test
    fun dailyFermiBank_andScoring_areValidAndDeterministic() {
        assertTrue(OfflinePuzzleBank.fermiQuestions.size >= 45)
        for (q in OfflinePuzzleBank.fermiQuestions) {
            assertTrue(q.prompt.isNotBlank())
            assertTrue(q.unit.isNotBlank())
            assertTrue(q.answer > 0.0, "Question ${q.id} must have positive answer")
            assertTrue(q.mathSteps.isNotEmpty(), "Question ${q.id} should include math steps")
        }

        val todayA = OfflinePuzzleBank.dailyFermi("2026-11-23")
        val todayB = OfflinePuzzleBank.dailyFermi("2026-11-23")
        assertEquals(todayA, todayB)
        assertEquals(3, todayA.questions.size)
        assertEquals(3, todayA.questions.map { it.id }.toSet().size)

        // Parsing shorthand inputs
        assertEquals(1_500_000.0, FermiScoring.parseEstimate("1.5M"))
        assertEquals(450_000.0, FermiScoring.parseEstimate("450k"))
        assertEquals(2_000_000_000.0, FermiScoring.parseEstimate("2B"))

        // Geometric mean of 2.0, 4.0, 1.0 = cbrt(8) = 2.0
        val records = listOf(
            FermiGuessRecord("q1", 200.0, 2.0, "High"),
            FermiGuessRecord("q2", 25.0, 4.0, "Low"),
            FermiGuessRecord("q3", 100.0, 1.0, "Exact"),
        )
        assertTrue(abs(FermiScoring.geometricMean(records) - 2.0) < 0.001)

        val share = FermiScoring.buildShareText(
            DailyFermiPuzzle("2026-11-23", 327, "2026-11-23", todayA.questions),
            FermiProgress("2026-11-23", records, completed = true),
        )
        assertTrue(share.contains("Daily Fermi #327"))
        assertTrue(share.contains("2.00\u00D7 avg"))
    }

    @Test
    fun dailyPuzzleNumber_isSequentialByCalendarDate() {
        assertEquals(1, OfflinePuzzleBank.dailyPuzzleNumber("2026-01-01"))
        assertEquals(2, OfflinePuzzleBank.dailyPuzzleNumber("2026-01-02"))
        assertEquals(327, OfflinePuzzleBank.dailyPuzzleNumber("2026-11-23"))
    }

    @Test
    fun lockoutPolicy_respectsScheduleAndManualLock() {
        val cal8am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 15)
        }
        val cal11am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }
        val scheduled = PuzzleSettings(
            lockoutScheduleEnabled = true,
            unlockStartHour = 7,
            unlockStartMinute = 0,
            unlockEndHour = 9,
            unlockEndMinute = 0,
        )
        assertFalse(LockoutPolicy.evaluate(scheduled, "2026-11-23", cal8am).isLocked)
        assertTrue(LockoutPolicy.evaluate(scheduled, "2026-11-23", cal11am).isLocked)

        val manualLocked = scheduled.copy(lockedUntilDateIso = "2026-11-23")
        assertTrue(LockoutPolicy.evaluate(manualLocked, "2026-11-23", cal8am).isLocked)
        assertFalse(LockoutPolicy.evaluate(manualLocked, "2026-11-24", cal8am).isLocked)
    }

    @Test
    fun bundledCrosswordsAsset_all45AreValidAndConsistent() {
        val file = File("src/main/assets/crosswords.json")
        assertTrue(file.exists(), "crosswords.json must exist at ${file.absolutePath}")
        val puzzles = Json.decodeFromString<List<DailyCrosswordPuzzle>>(file.readText())
        assertEquals(45, puzzles.size)
        for (p in puzzles) {
            assertEquals(9, p.rows)
            assertEquals(9, p.cols)
            assertEquals(9, p.grid.size)
            assertTrue(p.across.isNotEmpty() && p.down.isNotEmpty())
            for (cl in p.across) {
                val extracted = (0 until cl.answer.length).map { i -> p.grid[cl.row][cl.col + i] }.joinToString("")
                assertEquals(cl.answer, extracted, "Puzzle #${p.id} Across ${cl.number} mismatch")
                assertTrue(cl.clue.isNotBlank())
            }
            for (cl in p.down) {
                val extracted = (0 until cl.answer.length).map { i -> p.grid[cl.row + i][cl.col] }.joinToString("")
                assertEquals(cl.answer, extracted, "Puzzle #${p.id} Down ${cl.number} mismatch")
                assertTrue(cl.clue.isNotBlank())
            }
        }
    }
}
