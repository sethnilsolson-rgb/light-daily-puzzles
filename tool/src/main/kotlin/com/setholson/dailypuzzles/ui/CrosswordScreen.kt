package com.setholson.dailypuzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.OfflinePuzzleBank
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.model.CrosswordClue
import com.setholson.dailypuzzles.model.CrosswordProgress
import com.setholson.dailypuzzles.model.DailyCrosswordPuzzle
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class CrosswordUiState(
    val puzzle: DailyCrosswordPuzzle? = null,
    val progress: CrosswordProgress = CrosswordProgress(dateIso = ""),
    val selectedRow: Int = 0,
    val selectedCol: Int = 0,
    val isAcross: Boolean = true,
    val checkedWrongCells: Set<String> = emptySet(),
    val statusNote: String = "",
    val isLoading: Boolean = true,
)

class CrosswordViewModel(
    private val repository: PuzzleRepository,
) : LightViewModel<Unit>() {

    private val _ui = MutableStateFlow(CrosswordUiState())
    val ui: StateFlow<CrosswordUiState> = _ui.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val today = OfflinePuzzleBank.todayLocalIso()
            val puzzle = repository.getTodayCrossword(today)
            val progress = repository.getCrosswordProgress(today)
            val firstClue = puzzle.across.firstOrNull() ?: puzzle.down.firstOrNull()
            _ui.value = CrosswordUiState(
                puzzle = puzzle,
                progress = progress,
                selectedRow = firstClue?.row ?: 0,
                selectedCol = firstClue?.col ?: 0,
                isAcross = puzzle.across.isNotEmpty(),
                statusNote = if (progress.completed) "\u2605 Solved!" else "",
                isLoading = false,
            )
        }
    }

    fun tickSecond() {
        val s = _ui.value
        if (s.isLoading || s.puzzle == null || s.progress.completed) return
        val next = s.progress.copy(elapsedSeconds = s.progress.elapsedSeconds + 1)
        _ui.value = s.copy(progress = next)
        if (next.elapsedSeconds % 10 == 0) {
            viewModelScope.launch { repository.saveCrosswordProgress(next) }
        }
    }

    fun onCellTap(r: Int, c: Int) {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        if (puzzle.grid[r][c] == '#') return
        if (s.selectedRow == r && s.selectedCol == c) {
            val otherDir = !s.isAcross
            if (findClueForCell(puzzle, r, c, otherDir) != null) {
                _ui.value = s.copy(isAcross = otherDir, statusNote = "")
            }
        } else {
            val preferCurrent = findClueForCell(puzzle, r, c, s.isAcross) != null
            _ui.value = s.copy(
                selectedRow = r,
                selectedCol = c,
                isAcross = if (preferCurrent) s.isAcross else !s.isAcross,
                statusNote = "",
            )
        }
    }

    fun jumpToClue(clue: CrosswordClue, across: Boolean) {
        val s = _ui.value
        // Jump to the first empty cell in this clue, or the starting cell if filled
        var targetR = clue.row
        var targetC = clue.col
        for (i in 0 until clue.answer.length) {
            val r = if (across) clue.row else clue.row + i
            val c = if (across) clue.col + i else clue.col
            if (s.progress.entries["$r,$c"].isNullOrEmpty()) {
                targetR = r
                targetC = c
                break
            }
        }
        _ui.value = s.copy(
            selectedRow = targetR,
            selectedCol = targetC,
            isAcross = across,
            statusNote = "",
        )
    }

    fun toggleDirection() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val nextDir = !s.isAcross
        if (findClueForCell(puzzle, s.selectedRow, s.selectedCol, nextDir) != null) {
            _ui.value = s.copy(isAcross = nextDir, statusNote = "")
        } else {
            val fallback = if (nextDir) puzzle.across.firstOrNull() else puzzle.down.firstOrNull()
            if (fallback != null) {
                _ui.value = s.copy(
                    selectedRow = fallback.row,
                    selectedCol = fallback.col,
                    isAcross = nextDir,
                    statusNote = "",
                )
            }
        }
    }

    fun cycleClue(delta: Int) {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val list = if (s.isAcross) puzzle.across else puzzle.down
        if (list.isEmpty()) return
        val cur = findClueForCell(puzzle, s.selectedRow, s.selectedCol, s.isAcross)
        val idx = list.indexOfFirst { it.number == cur?.number }.coerceAtLeast(0)
        val nextIdx = ((idx + delta) % list.size + list.size) % list.size
        jumpToClue(list[nextIdx], s.isAcross)
    }

    fun onLetter(ch: Char) {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        if (s.progress.completed) return
        val r = s.selectedRow
        val c = s.selectedCol
        if (puzzle.grid[r][c] == '#') return

        val key = "$r,$c"
        val updatedEntries = s.progress.entries.toMutableMap()
        updatedEntries[key] = ch.uppercaseChar().toString()
        val solved = isSolved(puzzle, updatedEntries)
        val allFilled = !solved && isCompletelyFilled(puzzle, updatedEntries)
        val updatedProg = s.progress.copy(
            dateIso = OfflinePuzzleBank.todayLocalIso(),
            entries = updatedEntries,
            completed = solved,
        )

        val (nr, nc) = nextCellInWord(puzzle, r, c, s.isAcross, updatedEntries)
        _ui.value = s.copy(
            progress = updatedProg,
            selectedRow = nr,
            selectedCol = nc,
            checkedWrongCells = s.checkedWrongCells - key,
            statusNote = when {
                solved -> "\u2605 Solved!"
                allFilled -> "Almost! Tap Check"
                else -> ""
            },
        )
        viewModelScope.launch { repository.saveCrosswordProgress(updatedProg) }
    }

    fun onBackspace() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        if (s.progress.completed) return
        val r = s.selectedRow
        val c = s.selectedCol
        val key = "$r,$c"
        val updatedEntries = s.progress.entries.toMutableMap()
        if (!updatedEntries[key].isNullOrEmpty()) {
            updatedEntries.remove(key)
            val updatedProg = s.progress.copy(entries = updatedEntries)
            _ui.value = s.copy(
                progress = updatedProg,
                checkedWrongCells = s.checkedWrongCells - key,
                statusNote = "",
            )
            viewModelScope.launch { repository.saveCrosswordProgress(updatedProg) }
        } else {
            val (pr, pc) = prevCellInWord(puzzle, r, c, s.isAcross)
            val prevKey = "$pr,$pc"
            updatedEntries.remove(prevKey)
            val updatedProg = s.progress.copy(entries = updatedEntries)
            _ui.value = s.copy(
                progress = updatedProg,
                selectedRow = pr,
                selectedCol = pc,
                checkedWrongCells = s.checkedWrongCells - prevKey,
                statusNote = "",
            )
            viewModelScope.launch { repository.saveCrosswordProgress(updatedProg) }
        }
    }

    fun checkActiveWord() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val clue = findClueForCell(puzzle, s.selectedRow, s.selectedCol, s.isAcross) ?: return
        val wrong = s.checkedWrongCells.toMutableSet()
        var wordWrong = 0
        var filledCount = 0
        for (i in 0 until clue.answer.length) {
            val r = if (s.isAcross) clue.row else clue.row + i
            val c = if (s.isAcross) clue.col + i else clue.col
            val key = "$r,$c"
            val entered = s.progress.entries[key]?.uppercase(Locale.US)
            if (!entered.isNullOrBlank()) {
                filledCount++
                if (entered != puzzle.grid[r][c].toString()) {
                    wrong.add(key)
                    wordWrong++
                } else {
                    wrong.remove(key)
                }
            }
        }
        val msg = when {
            filledCount == 0 -> "Word is empty"
            wordWrong == 0 -> "\u2713 Word OK!"
            else -> "$wordWrong wrong (\u00D7)"
        }
        _ui.value = s.copy(checkedWrongCells = wrong, statusNote = msg)
    }

    fun checkGrid() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val wrong = mutableSetOf<String>()
        var filledCount = 0
        for ((k, v) in s.progress.entries) {
            if (v.isBlank()) continue
            filledCount++
            val parts = k.split(",")
            val r = parts[0].toInt()
            val c = parts[1].toInt()
            if (puzzle.grid[r][c].toString() != v.uppercase(Locale.US)) {
                wrong.add(k)
            }
        }
        val msg = when {
            filledCount == 0 -> "Grid is empty"
            wrong.isEmpty() -> "\u2713 All OK!"
            else -> "${wrong.size} wrong (\u00D7)"
        }
        _ui.value = s.copy(checkedWrongCells = wrong, statusNote = msg)
    }

    fun clearWrongLetters() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        if (s.progress.completed) return
        val updatedEntries = s.progress.entries.toMutableMap()
        var removed = 0
        val iter = updatedEntries.entries.iterator()
        while (iter.hasNext()) {
            val (k, v) = iter.next()
            if (v.isBlank()) continue
            val parts = k.split(",")
            val r = parts[0].toInt()
            val c = parts[1].toInt()
            if (puzzle.grid[r][c].toString() != v.uppercase(Locale.US)) {
                iter.remove()
                removed++
            }
        }
        val updatedProg = s.progress.copy(entries = updatedEntries)
        _ui.value = s.copy(
            progress = updatedProg,
            checkedWrongCells = emptySet(),
            statusNote = if (removed == 0) "No wrong letters" else "Cleared $removed wrong",
        )
        viewModelScope.launch { repository.saveCrosswordProgress(updatedProg) }
    }

    fun revealSelectedCell() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        if (s.progress.completed) return
        val r = s.selectedRow
        val c = s.selectedCol
        val answerChar = puzzle.grid[r][c]
        if (answerChar == '#') return

        val key = "$r,$c"
        val updatedEntries = s.progress.entries.toMutableMap()
        updatedEntries[key] = answerChar.toString()
        val solved = isSolved(puzzle, updatedEntries)
        val updatedProg = s.progress.copy(
            dateIso = OfflinePuzzleBank.todayLocalIso(),
            entries = updatedEntries,
            hintsUsed = s.progress.hintsUsed + 1,
            completed = solved,
        )
        val (nr, nc) = nextCellInWord(puzzle, r, c, s.isAcross, updatedEntries)
        _ui.value = s.copy(
            progress = updatedProg,
            selectedRow = nr,
            selectedCol = nc,
            checkedWrongCells = s.checkedWrongCells - key,
            statusNote = if (solved) "\u2605 Solved!" else "Hint: $answerChar",
        )
        viewModelScope.launch { repository.saveCrosswordProgress(updatedProg) }
    }

    fun revealActiveWord() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        if (s.progress.completed) return
        val clue = findClueForCell(puzzle, s.selectedRow, s.selectedCol, s.isAcross) ?: return

        val updatedEntries = s.progress.entries.toMutableMap()
        val remainingWrong = s.checkedWrongCells.toMutableSet()
        var revealedCount = 0
        for (i in 0 until clue.answer.length) {
            val r = if (s.isAcross) clue.row else clue.row + i
            val c = if (s.isAcross) clue.col + i else clue.col
            val key = "$r,$c"
            val ans = puzzle.grid[r][c].toString()
            if (updatedEntries[key]?.uppercase(Locale.US) != ans) {
                updatedEntries[key] = ans
                revealedCount++
            }
            remainingWrong.remove(key)
        }
        val solved = isSolved(puzzle, updatedEntries)
        val updatedProg = s.progress.copy(
            dateIso = OfflinePuzzleBank.todayLocalIso(),
            entries = updatedEntries,
            hintsUsed = s.progress.hintsUsed + revealedCount.coerceAtLeast(1),
            completed = solved,
        )
        _ui.value = s.copy(
            progress = updatedProg,
            checkedWrongCells = remainingWrong,
            statusNote = if (solved) "\u2605 Solved!" else "Revealed word",
        )
        viewModelScope.launch { repository.saveCrosswordProgress(updatedProg) }
    }

    fun resetGrid() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val freshProg = CrosswordProgress(dateIso = OfflinePuzzleBank.todayLocalIso())
        val firstClue = puzzle.across.firstOrNull() ?: puzzle.down.firstOrNull()
        _ui.value = s.copy(
            progress = freshProg,
            selectedRow = firstClue?.row ?: 0,
            selectedCol = firstClue?.col ?: 0,
            isAcross = puzzle.across.isNotEmpty(),
            checkedWrongCells = emptySet(),
            statusNote = "Grid cleared",
        )
        viewModelScope.launch { repository.saveCrosswordProgress(freshProg) }
    }

    companion object {
        fun findClueForCell(
            puzzle: DailyCrosswordPuzzle,
            r: Int,
            c: Int,
            across: Boolean,
        ): CrosswordClue? {
            return if (across) {
                puzzle.across.firstOrNull { cl ->
                    cl.row == r && c in cl.col until (cl.col + cl.answer.length)
                }
            } else {
                puzzle.down.firstOrNull { cl ->
                    cl.col == c && r in cl.row until (cl.row + cl.answer.length)
                }
            }
        }

        fun clueNumberMap(puzzle: DailyCrosswordPuzzle): Map<Pair<Int, Int>, Int> {
            val map = mutableMapOf<Pair<Int, Int>, Int>()
            puzzle.across.forEach { map[it.row to it.col] = it.number }
            puzzle.down.forEach { map[it.row to it.col] = it.number }
            return map
        }

        fun isClueFilled(
            clue: CrosswordClue,
            across: Boolean,
            entries: Map<String, String>,
        ): Boolean {
            for (i in 0 until clue.answer.length) {
                val r = if (across) clue.row else clue.row + i
                val c = if (across) clue.col + i else clue.col
                if (entries["$r,$c"].isNullOrBlank()) return false
            }
            return true
        }

        private fun isCompletelyFilled(puzzle: DailyCrosswordPuzzle, entries: Map<String, String>): Boolean {
            for (r in 0 until puzzle.rows) {
                for (c in 0 until puzzle.cols) {
                    if (puzzle.grid[r][c] == '#') continue
                    if (entries["$r,$c"].isNullOrBlank()) return false
                }
            }
            return true
        }

        private fun isSolved(puzzle: DailyCrosswordPuzzle, entries: Map<String, String>): Boolean {
            for (r in 0 until puzzle.rows) {
                for (c in 0 until puzzle.cols) {
                    val expected = puzzle.grid[r][c]
                    if (expected == '#') continue
                    val actual = entries["$r,$c"]?.uppercase(Locale.US)
                    if (actual != expected.toString()) return false
                }
            }
            return true
        }

        private fun nextCellInWord(
            puzzle: DailyCrosswordPuzzle,
            r: Int,
            c: Int,
            across: Boolean,
            entries: Map<String, String>,
        ): Pair<Int, Int> {
            val clue = findClueForCell(puzzle, r, c, across) ?: return r to c
            val len = clue.answer.length
            val curOffset = if (across) c - clue.col else r - clue.row
            for (offset in (curOffset + 1) until len) {
                val nr = if (across) clue.row else clue.row + offset
                val nc = if (across) clue.col + offset else clue.col
                if (entries["$nr,$nc"].isNullOrEmpty()) return nr to nc
            }
            val step = (curOffset + 1).coerceAtMost(len - 1)
            return if (across) clue.row to (clue.col + step) else (clue.row + step) to clue.col
        }

        private fun prevCellInWord(
            puzzle: DailyCrosswordPuzzle,
            r: Int,
            c: Int,
            across: Boolean,
        ): Pair<Int, Int> {
            val clue = findClueForCell(puzzle, r, c, across) ?: return r to c
            val curOffset = if (across) c - clue.col else r - clue.row
            val prev = (curOffset - 1).coerceAtLeast(0)
            return if (across) clue.row to (clue.col + prev) else (clue.row + prev) to clue.col
        }
    }
}

private enum class CrosswordOverlay {
    NONE,
    HINTS,
    CLUES,
    FULL_CLUE,
}

class CrosswordScreen(
    private val sealedActivity: SealedLightActivity,
) : LightScreen<Unit, CrosswordViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }

    override val viewModelClass: Class<CrosswordViewModel>
        get() = CrosswordViewModel::class.java

    override fun createViewModel(): CrosswordViewModel = CrosswordViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val ui by viewModel.ui.collectAsState()
        var activeOverlay by remember { mutableStateOf(CrosswordOverlay.NONE) }
        var confirmReset by remember { mutableStateOf(false) }

        LaunchedEffect(ui.isLoading, ui.progress.completed) {
            while (!ui.isLoading && !ui.progress.completed) {
                delay(1000L)
                viewModel.tickSecond()
            }
        }

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content
            val bg = LightThemeTokens.colors.background

            val mins = ui.progress.elapsedSeconds / 60
            val secs = ui.progress.elapsedSeconds % 60
            val timerText = String.format(Locale.US, "%d:%02d", mins, secs)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .swipeToBack(sideOnly = true) {
                        if (activeOverlay != CrosswordOverlay.NONE) {
                            activeOverlay = CrosswordOverlay.NONE
                            confirmReset = false
                        } else {
                            goBack()
                        }
                    },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = {
                                if (activeOverlay != CrosswordOverlay.NONE) {
                                    activeOverlay = CrosswordOverlay.NONE
                                    confirmReset = false
                                } else {
                                    goBack()
                                }
                            },
                        ),
                        center = LightTopBarCenter.Text(
                            if (ui.progress.completed) "Crossword \u00B7 Done" else "Crossword \u00B7 $timerText",
                        ),
                        rightButton = if (activeOverlay == CrosswordOverlay.NONE && ui.puzzle != null) {
                            LightBarButton.Text(
                                text = "Clues",
                                onClick = { activeOverlay = CrosswordOverlay.CLUES },
                            )
                        } else if (activeOverlay != CrosswordOverlay.NONE) {
                            LightBarButton.Text(
                                text = "Done",
                                onClick = {
                                    activeOverlay = CrosswordOverlay.NONE
                                    confirmReset = false
                                },
                            )
                        } else {
                            null
                        },
                        modifier = Modifier.padding(bottom = 0.02f.gridUnitsAsDp()),
                    )

                    val puzzle = ui.puzzle
                    if (ui.isLoading || puzzle == null) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("Loading crossword\u2026", color = content.copy(alpha = 0.7f), fontSize = 15.sp)
                        }
                    } else {
                        val numbers = remember(puzzle.id) { CrosswordViewModel.clueNumberMap(puzzle) }
                        val activeClue = CrosswordViewModel.findClueForCell(
                            puzzle,
                            ui.selectedRow,
                            ui.selectedCol,
                            ui.isAcross,
                        )
                        val activeWordCells: Set<Pair<Int, Int>> = remember(activeClue, ui.isAcross) {
                            if (activeClue == null) emptySet()
                            else (0 until activeClue.answer.length).map { i ->
                                if (ui.isAcross) activeClue.row to (activeClue.col + i)
                                else (activeClue.row + i) to activeClue.col
                            }.toSet()
                        }

                        // Middle Row: Square 9x9 Grid (left) + Quick Controls Rail (right)
                        // Dynamically fits the remaining height above Clue Banner & Keyboard on 360x413dp!
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            val railMinWidth = 108.dp
                            val gridSize = minOf(maxHeight, (maxWidth - railMinWidth - 8.dp).coerceAtLeast(160.dp))
                            val letterSp = (gridSize.value / 9f * 0.56f).coerceIn(11f, 17f).sp

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Crisp Square 9x9 Crossword Grid
                                Box(
                                    modifier = Modifier
                                        .size(gridSize)
                                        .border(1.5.dp, content.copy(alpha = 0.9f)),
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        for (r in 0 until puzzle.rows) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                            ) {
                                                for (c in 0 until puzzle.cols) {
                                                    val isBlock = puzzle.grid[r][c] == '#'
                                                    val isSelected = r == ui.selectedRow && c == ui.selectedCol
                                                    val isInWord = (r to c) in activeWordCells
                                                    val cellKey = "$r,$c"
                                                    val entered = ui.progress.entries[cellKey] ?: ""
                                                    val isWrong = cellKey in ui.checkedWrongCells
                                                    val clueNum = numbers[r to c]

                                                    val cellBg = when {
                                                        isBlock -> content.copy(alpha = 0.88f)
                                                        isSelected -> content
                                                        isInWord -> content.copy(alpha = 0.24f)
                                                        else -> bg
                                                    }
                                                    val letterColor = if (isSelected) bg else content

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxSize()
                                                            .background(cellBg)
                                                            .border(0.5.dp, content.copy(alpha = 0.38f))
                                                            .then(
                                                                if (!isBlock) {
                                                                    Modifier.lightClickable { viewModel.onCellTap(r, c) }
                                                                } else {
                                                                    Modifier
                                                                },
                                                            ),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        if (!isBlock) {
                                                            if (clueNum != null) {
                                                                Text(
                                                                    text = clueNum.toString(),
                                                                    color = letterColor.copy(alpha = 0.72f),
                                                                    fontSize = 6.5.sp,
                                                                    lineHeight = 7.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopStart)
                                                                        .padding(start = 1.dp, top = 0.5.dp),
                                                                )
                                                            }
                                                            Text(
                                                                text = entered,
                                                                color = letterColor,
                                                                fontSize = letterSp,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                            if (isWrong) {
                                                                Text(
                                                                    text = "\u00D7",
                                                                    color = letterColor,
                                                                    fontSize = 9.sp,
                                                                    lineHeight = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier
                                                                        .align(Alignment.BottomEnd)
                                                                        .padding(end = 1.dp),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Right-Side Quick Control Rail (Always reachable next to the grid!)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(gridSize),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Status / Word Tag Box
                                    val dirTag = activeClue?.let {
                                        "${it.number}${if (ui.isAcross) "A" else "D"} \u00B7 ${it.answer.length} ltrs"
                                    } ?: ""
                                    val noteIsAlert = ui.statusNote.contains("wrong") || ui.statusNote.contains("Almost")
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (noteIsAlert) content else bg,
                                                RoundedCornerShape(6.dp),
                                            )
                                            .border(
                                                1.dp,
                                                content.copy(alpha = if (noteIsAlert) 1f else 0.35f),
                                                RoundedCornerShape(6.dp),
                                            )
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = ui.statusNote.ifBlank { dirTag },
                                            color = if (noteIsAlert) bg else content,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }

                                    if (!ui.progress.completed) {
                                        CrosswordRailBtn(
                                            label = if (ui.isAcross) "\u2194 Across" else "\u2195 Down",
                                            onClick = { viewModel.toggleDirection() },
                                            modifier = Modifier.weight(1f),
                                        )
                                        CrosswordRailBtn(
                                            label = "Check Word",
                                            onClick = { viewModel.checkActiveWord() },
                                            modifier = Modifier.weight(1f),
                                        )
                                        CrosswordRailBtn(
                                            label = "+1 Letter",
                                            onClick = { viewModel.revealSelectedCell() },
                                            modifier = Modifier.weight(1f),
                                        )
                                        CrosswordRailBtn(
                                            label = "More Hints",
                                            emphasized = true,
                                            onClick = { activeOverlay = CrosswordOverlay.HINTS },
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                .padding(6.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "\u2605 Solved!\n$timerText\n${ui.progress.hintsUsed} hints",
                                                color = content,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 16.sp,
                                            )
                                        }
                                        CrosswordRailBtn(
                                            label = "Replay",
                                            onClick = { viewModel.resetGrid() },
                                            modifier = Modifier.height(34.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Full-Width Unwrapped Active Clue Banner (tap clue text to enlarge!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .border(1.2.dp, content.copy(alpha = 0.75f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .lightClickable { viewModel.cycleClue(-1) }
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("\u25C0", color = content, fontSize = 13.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .lightClickable { activeOverlay = CrosswordOverlay.FULL_CLUE },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val dirLabel = if (ui.isAcross) "A" else "D"
                                val clueHeader = activeClue?.let { "${it.number}$dirLabel" } ?: ""
                                Text(
                                    text = "$clueHeader \u00B7 ${activeClue?.clue ?: ""}",
                                    color = content,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .lightClickable { viewModel.cycleClue(1) }
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("\u25B6", color = content, fontSize = 13.sp)
                            }
                        }

                        // Bottom Area: Solved Share Card CTA OR Always-Visible QWERTY Keyboard
                        if (ui.progress.completed) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .background(content, RoundedCornerShape(10.dp))
                                    .lightClickable {
                                        navigateTo(screenFactory = { act ->
                                            ShareCardScreen(act, ShareScope.CROSSWORD)
                                        })
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Open Crossword Share Card",
                                    color = bg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            CrosswordKeyboard(
                                onLetter = { viewModel.onLetter(it) },
                                onBackspace = { viewModel.onBackspace() },
                            )
                        }
                    }
                }

                // Overlay Sheets (More Hints / All Clues / Full Enlarged Clue)
                val puzzle = ui.puzzle
                if (puzzle != null && activeOverlay != CrosswordOverlay.NONE) {
                    val activeClue = CrosswordViewModel.findClueForCell(
                        puzzle,
                        ui.selectedRow,
                        ui.selectedCol,
                        ui.isAcross,
                    )
                    when (activeOverlay) {
                        CrosswordOverlay.HINTS -> {
                            CrosswordHintsOverlay(
                                wrongCount = ui.checkedWrongCells.size,
                                confirmReset = confirmReset,
                                onCheckWord = {
                                    viewModel.checkActiveWord()
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                                onCheckGrid = {
                                    viewModel.checkGrid()
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                                onClearWrong = {
                                    viewModel.clearWrongLetters()
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                                onRevealLetter = {
                                    viewModel.revealSelectedCell()
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                                onRevealWord = {
                                    viewModel.revealActiveWord()
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                                onOpenClues = {
                                    confirmReset = false
                                    activeOverlay = CrosswordOverlay.CLUES
                                },
                                onReset = {
                                    if (!confirmReset) {
                                        confirmReset = true
                                    } else {
                                        viewModel.resetGrid()
                                        confirmReset = false
                                        activeOverlay = CrosswordOverlay.NONE
                                    }
                                },
                                onClose = {
                                    confirmReset = false
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                            )
                        }

                        CrosswordOverlay.CLUES -> {
                            CrosswordCluesOverlay(
                                puzzle = puzzle,
                                entries = ui.progress.entries,
                                initialAcross = ui.isAcross,
                                activeNumber = activeClue?.number,
                                onSelectClue = { clue, across ->
                                    viewModel.jumpToClue(clue, across)
                                    activeOverlay = CrosswordOverlay.NONE
                                },
                                onClose = { activeOverlay = CrosswordOverlay.NONE },
                            )
                        }

                        CrosswordOverlay.FULL_CLUE -> {
                            CrosswordFullClueOverlay(
                                clue = activeClue,
                                isAcross = ui.isAcross,
                                entries = ui.progress.entries,
                                onToggleDir = { viewModel.toggleDirection() },
                                onPrev = { viewModel.cycleClue(-1) },
                                onNext = { viewModel.cycleClue(1) },
                                onClose = { activeOverlay = CrosswordOverlay.NONE },
                            )
                        }

                        CrosswordOverlay.NONE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CrosswordRailBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (emphasized) content.copy(alpha = 0.14f) else bg, RoundedCornerShape(6.dp))
            .border(
                width = if (emphasized) 1.3.dp else 1.dp,
                color = content.copy(alpha = if (emphasized) 0.9f else 0.55f),
                shape = RoundedCornerShape(6.dp),
            )
            .lightClickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 11.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrosswordHintsOverlay(
    wrongCount: Int,
    confirmReset: Boolean,
    onCheckWord: () -> Unit,
    onCheckGrid: () -> Unit,
    onClearWrong: () -> Unit,
    onRevealLetter: () -> Unit,
    onRevealWord: () -> Unit,
    onOpenClues: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg.copy(alpha = 0.96f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, content, RoundedCornerShape(12.dp))
                .background(bg, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Check & Hints", color = content, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .border(1.dp, content.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .lightClickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Close", color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayActionBtn("Check Word", onCheckWord, Modifier.weight(1f))
                OverlayActionBtn("Check Grid", onCheckGrid, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayActionBtn("Reveal Letter", onRevealLetter, Modifier.weight(1f))
                OverlayActionBtn("Reveal Word", onRevealWord, Modifier.weight(1f))
            }
            OverlayActionBtn(
                label = if (wrongCount > 0) "Clear Wrong Letters ($wrongCount)" else "Clear All Wrong Letters",
                onClick = onClearWrong,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayActionBtn("All Clues List", onOpenClues, Modifier.weight(1.2f))
                OverlayActionBtn(
                    label = if (confirmReset) "Tap to Reset" else "Reset Grid",
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    inverted = confirmReset,
                )
            }
        }
    }
}

@Composable
private fun OverlayActionBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    Box(
        modifier = modifier
            .background(if (inverted) content else bg, RoundedCornerShape(8.dp))
            .border(1.2.dp, content.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .lightClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (inverted) bg else content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrosswordCluesOverlay(
    puzzle: DailyCrosswordPuzzle,
    entries: Map<String, String>,
    initialAcross: Boolean,
    activeNumber: Int?,
    onSelectClue: (CrosswordClue, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    var showAcross by remember { mutableStateOf(initialAcross) }
    val list = if (showAcross) puzzle.across else puzzle.down

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(if (showAcross) content else bg, RoundedCornerShape(6.dp))
                        .border(1.dp, content, RoundedCornerShape(6.dp))
                        .lightClickable { showAcross = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Across (${puzzle.across.size})",
                        color = if (showAcross) bg else content,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .background(if (!showAcross) content else bg, RoundedCornerShape(6.dp))
                        .border(1.dp, content, RoundedCornerShape(6.dp))
                        .lightClickable { showAcross = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Down (${puzzle.down.size})",
                        color = if (!showAcross) bg else content,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .border(1.dp, content.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .lightClickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Back", color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(list, key = { "${showAcross}_${it.number}" }) { clue ->
                val filled = CrosswordViewModel.isClueFilled(clue, showAcross, entries)
                val isCurrent = showAcross == initialAcross && clue.number == activeNumber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) content.copy(alpha = 0.16f) else bg,
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = content.copy(alpha = if (isCurrent) 0.9f else 0.35f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .lightClickable { onSelectClue(clue, showAcross) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${clue.number}${if (showAcross) "A" else "D"}",
                        color = content,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${clue.clue} (${clue.answer.length})",
                        color = content.copy(alpha = if (filled) 0.6f else 1f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (filled) {
                        Text("\u2713", color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrosswordFullClueOverlay(
    clue: CrosswordClue?,
    isAcross: Boolean,
    entries: Map<String, String>,
    onToggleDir: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg.copy(alpha = 0.95f))
            .lightClickable(onClick = onClose)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(12.dp))
                .border(1.5.dp, content, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val dirWord = if (isAcross) "Across" else "Down"
            val header = clue?.let { "${it.number} $dirWord (${it.answer.length} letters)" } ?: dirWord
            Text(header, color = content.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold)

            Text(
                text = clue?.clue ?: "",
                color = content,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            // Current letter slots preview
            if (clue != null) {
                val pattern = (0 until clue.answer.length).joinToString(" ") { i ->
                    val r = if (isAcross) clue.row else clue.row + i
                    val c = if (isAcross) clue.col + i else clue.col
                    entries["$r,$c"]?.ifBlank { "_" } ?: "_"
                }
                Text(
                    text = pattern,
                    color = content,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverlayActionBtn("\u25C0 Prev", onPrev, Modifier.weight(1f))
                OverlayActionBtn(if (isAcross) "Down" else "Across", onToggleDir, Modifier.weight(1f))
                OverlayActionBtn("Next \u25B6", onNext, Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(content, RoundedCornerShape(8.dp))
                    .lightClickable(onClick = onClose)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Back to Grid", color = bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CrosswordKeyboard(
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (ch in rows[0]) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(31.dp)
                        .background(bg, RoundedCornerShape(5.dp))
                        .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                        .lightClickable { onLetter(ch) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch.toString(), color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (ch in rows[1]) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(31.dp)
                        .background(bg, RoundedCornerShape(5.dp))
                        .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                        .lightClickable { onLetter(ch) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch.toString(), color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Row 3 + Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (ch in rows[2]) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(31.dp)
                        .background(bg, RoundedCornerShape(5.dp))
                        .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                        .lightClickable { onLetter(ch) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch.toString(), color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1.7f)
                    .height(31.dp)
                    .background(content.copy(alpha = 0.16f), RoundedCornerShape(5.dp))
                    .border(1.dp, content.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                    .lightClickable(onClick = onBackspace),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u232B", color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
