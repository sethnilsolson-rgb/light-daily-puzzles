package com.setholson.dailypuzzles.model

import kotlinx.serialization.Serializable

enum class PuzzleType(val key: String, val label: String) {
    WORD("word", "Daily Word"),
    FERMI("fermi", "Daily Fermi"),
    CHESS("chess", "Daily Chess"),
    CROSSWORD("crossword", "Daily Crossword"),
}

@Serializable
data class PuzzleSettings(
    val lockoutScheduleEnabled: Boolean = false,
    val unlockStartHour: Int = 7,
    val unlockStartMinute: Int = 0,
    val unlockEndHour: Int = 9,
    val unlockEndMinute: Int = 0,
    val lockedUntilDateIso: String = "",
    val darkMode: Boolean = true,
)

@Serializable
data class DailyChessPuzzle(
    val dateIso: String,
    val puzzleNumber: Int,
    val title: String,
    val fen: String,
    val solutionSan: List<String>,
)

@Serializable
data class ChessProgress(
    val dateIso: String,
    val currentMoveIndex: Int = 0,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val completed: Boolean = false,
)

@Serializable
data class DailyWordlePuzzle(
    val dateIso: String,
    val puzzleNumber: Int,
    val solution: String, // 5 uppercase letters
)

@Serializable
data class WordleProgress(
    val dateIso: String,
    val guesses: List<String> = emptyList(),
    val won: Boolean = false,
    val completed: Boolean = false,
)

@Serializable
data class FermiQuestion(
    val id: String,
    val prompt: String,
    val unit: String,
    val answer: Double,
    val mathSteps: List<List<String>> = emptyList(),
)

@Serializable
data class DailyFermiPuzzle(
    val dateIso: String,
    val puzzleNumber: Int,
    val dateLabel: String,
    val questions: List<FermiQuestion>,
)

@Serializable
data class FermiGuessRecord(
    val questionId: String,
    val guess: Double,
    val multiple: Double,
    val direction: String, // "Exact", "High", "Low"
)

@Serializable
data class FermiProgress(
    val dateIso: String,
    val records: List<FermiGuessRecord> = emptyList(),
    val completed: Boolean = false,
)

@Serializable
data class CrosswordClue(
    val number: Int,
    val row: Int,
    val col: Int,
    val answer: String,
    val clue: String,
)

@Serializable
data class DailyCrosswordPuzzle(
    val id: Int,
    val title: String,
    val rows: Int,
    val cols: Int,
    val grid: List<String>, // '#' for black square, uppercase A-Z for answer
    val across: List<CrosswordClue>,
    val down: List<CrosswordClue>,
)

@Serializable
data class CrosswordProgress(
    val dateIso: String,
    val entries: Map<String, String> = emptyMap(), // "r,c" -> single uppercase letter
    val hintsUsed: Int = 0,
    val elapsedSeconds: Int = 0,
    val completed: Boolean = false,
)

data class TodayDashboardState(
    val todayIso: String = "",
    val todayPretty: String = "",
    val settings: PuzzleSettings = PuzzleSettings(),
    val lockStatus: LockoutStatus = LockoutStatus(isLocked = false, reason = ""),
    val chessPuzzle: DailyChessPuzzle? = null,
    val chessProgress: ChessProgress? = null,
    val wordlePuzzle: DailyWordlePuzzle? = null,
    val wordleProgress: WordleProgress? = null,
    val fermiPuzzle: DailyFermiPuzzle? = null,
    val fermiProgress: FermiProgress? = null,
    val crosswordPuzzle: DailyCrosswordPuzzle? = null,
    val crosswordProgress: CrosswordProgress? = null,
    val isLoading: Boolean = true,
) {
    val completedCount: Int
        get() = listOf(
            chessProgress?.completed == true,
            wordleProgress?.completed == true,
            fermiProgress?.completed == true,
            crosswordProgress?.completed == true,
        ).count { it }

    val allCompleted: Boolean
        get() = completedCount == 4
}

data class LockoutStatus(
    val isLocked: Boolean,
    val reason: String,
)
