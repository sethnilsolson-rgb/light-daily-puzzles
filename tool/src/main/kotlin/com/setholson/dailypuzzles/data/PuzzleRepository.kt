package com.setholson.dailypuzzles.data

import com.setholson.dailypuzzles.db.PuzzleDao
import com.setholson.dailypuzzles.db.PuzzleDatabase
import com.setholson.dailypuzzles.db.PuzzleProgressEntity
import com.setholson.dailypuzzles.db.PuzzleSettingEntity
import com.setholson.dailypuzzles.model.ChessProgress
import com.setholson.dailypuzzles.model.CrosswordProgress
import com.setholson.dailypuzzles.model.DailyChessPuzzle
import com.setholson.dailypuzzles.model.DailyCrosswordPuzzle
import com.setholson.dailypuzzles.model.DailyFermiPuzzle
import com.setholson.dailypuzzles.model.DailyWordlePuzzle
import com.setholson.dailypuzzles.model.FermiProgress
import com.setholson.dailypuzzles.model.PuzzleSettings
import com.setholson.dailypuzzles.model.PuzzleType
import com.setholson.dailypuzzles.model.TodayDashboardState
import com.setholson.dailypuzzles.model.WordleProgress
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.buildDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

class PuzzleRepository(
    private val dao: PuzzleDao,
    private val lightContext: SealedLightContext,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var cachedWords5: Set<String>? = null

    @Volatile
    private var cachedCrosswords: List<DailyCrosswordPuzzle>? = null

    companion object {
        private const val SETTINGS_KEY = "daily_puzzle_settings_v1"
        const val DATABASE_NAME = "light_daily_puzzles_v1.db"

        @Volatile
        private var instance: PuzzleRepository? = null

        fun getInstance(lightContext: SealedLightContext): PuzzleRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = lightContext.buildDatabase(
                        PuzzleDatabase::class.java,
                        DATABASE_NAME,
                    ) {
                        fallbackToDestructiveMigration(true)
                    }
                    PuzzleRepository(db.dao(), lightContext).also { instance = it }
                }
            }
        }
    }

    suspend fun getSettings(): PuzzleSettings = withContext(Dispatchers.IO) {
        val raw = dao.getSetting(SETTINGS_KEY)?.value ?: return@withContext PuzzleSettings()
        runCatching { json.decodeFromString<PuzzleSettings>(raw) }.getOrDefault(PuzzleSettings())
    }

    suspend fun saveSettings(settings: PuzzleSettings) = withContext(Dispatchers.IO) {
        dao.upsertSetting(
            PuzzleSettingEntity(
                key = SETTINGS_KEY,
                value = json.encodeToString(settings),
            ),
        )
    }

    /**
     * Loads strictly today's 4 offline puzzles + saved progress + lockout state.
     * Prunes prior days so only today's puzzles ever exist.
     */
    suspend fun loadTodayDashboard(): TodayDashboardState = withContext(Dispatchers.IO) {
        val todayIso = OfflinePuzzleBank.todayLocalIso()
        val todayPretty = OfflinePuzzleBank.todayPrettyLabel()

        dao.pruneOldProgress(todayIso)

        val settings = getSettings()
        val lockStatus = LockoutPolicy.evaluate(settings, todayIso)

        val chess = getTodayChess(todayIso)
        val word = getTodayWord(todayIso)
        val fermi = getTodayFermi(todayIso)
        val crossword = getTodayCrossword(todayIso)

        val chessProg = getChessProgress(todayIso)
        val wordProg = getWordleProgress(todayIso)
        val fermiProg = getFermiProgress(todayIso)
        val crosswordProg = getCrosswordProgress(todayIso)

        TodayDashboardState(
            todayIso = todayIso,
            todayPretty = todayPretty,
            settings = settings,
            lockStatus = lockStatus,
            chessPuzzle = chess,
            chessProgress = chessProg,
            wordlePuzzle = word,
            wordleProgress = wordProg,
            fermiPuzzle = fermi,
            fermiProgress = fermiProg,
            crosswordPuzzle = crossword,
            crosswordProgress = crosswordProg,
            isLoading = false,
        )
    }

    fun getTodayChess(dateIso: String): DailyChessPuzzle = OfflinePuzzleBank.dailyChess(dateIso)

    fun getTodayWord(dateIso: String): DailyWordlePuzzle = OfflinePuzzleBank.dailyWord(dateIso)

    fun getTodayFermi(dateIso: String): DailyFermiPuzzle = OfflinePuzzleBank.dailyFermi(dateIso)

    suspend fun getTodayCrossword(dateIso: String): DailyCrosswordPuzzle = withContext(Dispatchers.IO) {
        val bank = cachedCrosswords ?: run {
            val raw = lightContext.readAsset("crosswords.json").toString(Charsets.UTF_8)
            json.decodeFromString<List<DailyCrosswordPuzzle>>(raw).also { cachedCrosswords = it }
        }
        OfflinePuzzleBank.selectDailyCrossword(dateIso, bank)
    }

    suspend fun getValidFiveLetterWords(): Set<String> = withContext(Dispatchers.IO) {
        cachedWords5 ?: run {
            val raw = lightContext.readAsset("words5.txt").toString(Charsets.UTF_8)
            val set = raw.lineSequence()
                .map { it.trim().uppercase(Locale.US) }
                .filter { it.length == 5 }
                .toHashSet()
            cachedWords5 = set
            set
        }
    }

    suspend fun getChessProgress(dateIso: String): ChessProgress = withContext(Dispatchers.IO) {
        val key = "${PuzzleType.CHESS.key}_$dateIso"
        val raw = dao.getProgress(key)?.progressJson ?: return@withContext ChessProgress(dateIso = dateIso)
        runCatching { json.decodeFromString<ChessProgress>(raw) }.getOrDefault(ChessProgress(dateIso = dateIso))
    }

    suspend fun saveChessProgress(progress: ChessProgress) = withContext(Dispatchers.IO) {
        dao.upsertProgress(
            PuzzleProgressEntity(
                progressKey = "${PuzzleType.CHESS.key}_${progress.dateIso}",
                dateIso = progress.dateIso,
                puzzleType = PuzzleType.CHESS.key,
                progressJson = json.encodeToString(progress),
                completed = progress.completed,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getWordleProgress(dateIso: String): WordleProgress = withContext(Dispatchers.IO) {
        val key = "${PuzzleType.WORD.key}_$dateIso"
        val raw = dao.getProgress(key)?.progressJson ?: return@withContext WordleProgress(dateIso = dateIso)
        runCatching { json.decodeFromString<WordleProgress>(raw) }.getOrDefault(WordleProgress(dateIso = dateIso))
    }

    suspend fun saveWordleProgress(progress: WordleProgress) = withContext(Dispatchers.IO) {
        dao.upsertProgress(
            PuzzleProgressEntity(
                progressKey = "${PuzzleType.WORD.key}_${progress.dateIso}",
                dateIso = progress.dateIso,
                puzzleType = PuzzleType.WORD.key,
                progressJson = json.encodeToString(progress),
                completed = progress.completed,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getFermiProgress(dateIso: String): FermiProgress = withContext(Dispatchers.IO) {
        val key = "${PuzzleType.FERMI.key}_$dateIso"
        val raw = dao.getProgress(key)?.progressJson ?: return@withContext FermiProgress(dateIso = dateIso)
        runCatching { json.decodeFromString<FermiProgress>(raw) }.getOrDefault(FermiProgress(dateIso = dateIso))
    }

    suspend fun saveFermiProgress(progress: FermiProgress) = withContext(Dispatchers.IO) {
        dao.upsertProgress(
            PuzzleProgressEntity(
                progressKey = "${PuzzleType.FERMI.key}_${progress.dateIso}",
                dateIso = progress.dateIso,
                puzzleType = PuzzleType.FERMI.key,
                progressJson = json.encodeToString(progress),
                completed = progress.completed,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getCrosswordProgress(dateIso: String): CrosswordProgress = withContext(Dispatchers.IO) {
        val key = "${PuzzleType.CROSSWORD.key}_$dateIso"
        val raw = dao.getProgress(key)?.progressJson ?: return@withContext CrosswordProgress(dateIso = dateIso)
        runCatching { json.decodeFromString<CrosswordProgress>(raw) }.getOrDefault(CrosswordProgress(dateIso = dateIso))
    }

    suspend fun saveCrosswordProgress(progress: CrosswordProgress) = withContext(Dispatchers.IO) {
        dao.upsertProgress(
            PuzzleProgressEntity(
                progressKey = "${PuzzleType.CROSSWORD.key}_${progress.dateIso}",
                dateIso = progress.dateIso,
                puzzleType = PuzzleType.CROSSWORD.key,
                progressJson = json.encodeToString(progress),
                completed = progress.completed,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Builds a combined spoiler-free text summary of all puzzles completed today. */
    fun buildCombinedShareText(state: TodayDashboardState): String {
        val sections = mutableListOf<String>()
        state.wordlePuzzle?.let { wp ->
            val prog = state.wordleProgress
            if (prog != null && prog.completed) {
                sections.add(WordleLogic.buildShareText(wp, prog))
            }
        }
        state.fermiPuzzle?.let { fp ->
            val prog = state.fermiProgress
            if (prog != null && prog.completed && prog.records.isNotEmpty()) {
                sections.add(FermiScoring.buildShareText(fp, prog))
            }
        }
        state.chessPuzzle?.let { cp ->
            val prog = state.chessProgress
            if (prog != null && prog.completed) {
                val stats = buildString {
                    append("Daily Chess #${cp.puzzleNumber} (${cp.title}): Solved")
                    if (prog.mistakes == 0 && prog.hintsUsed == 0) {
                        append(" \u2605 Perfect")
                    } else {
                        append(" (${prog.mistakes} miss, ${prog.hintsUsed} hint)")
                    }
                }
                sections.add(stats)
            }
        }
        state.crosswordPuzzle?.let { cw ->
            val prog = state.crosswordProgress
            if (prog != null && prog.completed) {
                val mins = prog.elapsedSeconds / 60
                val secs = prog.elapsedSeconds % 60
                val timeStr = String.format(Locale.US, "%d:%02d", mins, secs)
                val hintStr = if (prog.hintsUsed == 0) "no hints" else "${prog.hintsUsed} hints"
                sections.add("Daily Crossword #${cw.id}: Solved in $timeStr ($hintStr)")
            }
        }
        return if (sections.isEmpty()) {
            "Daily Puzzles (${state.todayIso}) \u2014 none finished yet."
        } else {
            sections.joinToString("\n\n")
        }
    }
}
