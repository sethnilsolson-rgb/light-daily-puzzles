package com.setholson.dailypuzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.LetterEval
import com.setholson.dailypuzzles.data.OfflinePuzzleBank
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.data.WordleLogic
import com.setholson.dailypuzzles.model.DailyWordlePuzzle
import com.setholson.dailypuzzles.model.WordleProgress
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class WordleUiState(
    val puzzle: DailyWordlePuzzle? = null,
    val progress: WordleProgress = WordleProgress(dateIso = ""),
    val currentInput: String = "",
    val message: String = "",
    val isLoading: Boolean = true,
)

class WordleViewModel(
    private val repository: PuzzleRepository,
) : LightViewModel<Unit>() {

    private val _ui = MutableStateFlow(WordleUiState())
    val ui: StateFlow<WordleUiState> = _ui.asStateFlow()

    private var validWords: Set<String> = emptySet()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val today = OfflinePuzzleBank.todayLocalIso()
            val puzzle = repository.getTodayWord(today)
            val progress = repository.getWordleProgress(today)
            validWords = repository.getValidFiveLetterWords()
            _ui.value = WordleUiState(
                puzzle = puzzle,
                progress = progress,
                currentInput = "",
                message = "",
                isLoading = false,
            )
        }
    }

    fun onLetter(ch: Char) {
        val state = _ui.value
        if (state.isLoading || state.progress.completed) return
        if (state.currentInput.length >= 5) return
        _ui.value = state.copy(
            currentInput = state.currentInput + ch.uppercaseChar(),
            message = "",
        )
    }

    fun onBackspace() {
        val state = _ui.value
        if (state.isLoading || state.progress.completed) return
        if (state.currentInput.isEmpty()) return
        _ui.value = state.copy(
            currentInput = state.currentInput.dropLast(1),
            message = "",
        )
    }

    fun onEnter() {
        val state = _ui.value
        val puzzle = state.puzzle ?: return
        if (state.isLoading || state.progress.completed) return
        val guess = state.currentInput.uppercase(Locale.US)
        if (guess.length < 5) {
            _ui.value = state.copy(message = "Need 5 letters")
            return
        }
        if (guess != puzzle.solution && validWords.isNotEmpty() && guess !in validWords) {
            _ui.value = state.copy(message = "Not in word list")
            return
        }

        val nextGuesses = state.progress.guesses + guess
        val won = guess == puzzle.solution
        val completed = won || nextGuesses.size >= 6
        val updatedProg = state.progress.copy(
            dateIso = puzzle.dateIso,
            guesses = nextGuesses,
            won = won,
            completed = completed,
        )
        val statusMsg = when {
            won -> when (nextGuesses.size) {
                1 -> "Genius! (1/6)"
                2 -> "Magnificent! (2/6)"
                3 -> "Impressive! (3/6)"
                4 -> "Splendid! (4/6)"
                5 -> "Great! (5/6)"
                else -> "Phew! (6/6)"
            }
            completed -> "Answer: ${puzzle.solution}"
            else -> ""
        }
        _ui.value = state.copy(
            progress = updatedProg,
            currentInput = "",
            message = statusMsg,
        )
        viewModelScope.launch {
            repository.saveWordleProgress(updatedProg)
        }
    }

    fun buildShareText(): String {
        val puzzle = _ui.value.puzzle ?: return ""
        return WordleLogic.buildShareText(puzzle, _ui.value.progress)
    }
}

class WordleScreen(
    private val sealedActivity: SealedLightActivity,
) : LightScreen<Unit, WordleViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }

    override val viewModelClass: Class<WordleViewModel>
        get() = WordleViewModel::class.java

    override fun createViewModel(): WordleViewModel = WordleViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val ui by viewModel.ui.collectAsState()

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content
            val bg = LightThemeTokens.colors.background
            val title = ui.puzzle?.let { p ->
                if (p.puzzleNumber > 0) "Daily Word #${p.puzzleNumber}" else "Daily Word"
            } ?: "Daily Word"

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .swipeToBack { goBack() },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                        ),
                        center = LightTopBarCenter.Text(title),
                        rightButton = null,
                        modifier = Modifier.padding(bottom = 0.1f.gridUnitsAsDp()),
                    )

                    if (ui.isLoading || ui.puzzle == null) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Loading Daily Word\u2026", color = content.copy(alpha = 0.7f), fontSize = 15.sp)
                        }
                    } else {
                        val puzzle = ui.puzzle!!
                        val keyStates = remember(ui.progress.guesses, puzzle.solution) {
                            WordleLogic.keyboardStates(ui.progress.guesses, puzzle.solution)
                        }

                        // Compact legend so monochrome Exact / Present / Miss is crystal clear
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "\u25A0 Exact   \u25C9 Wrong spot   \u25A1 Miss",
                                color = content.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                            )
                        }

                        // 6 x 5 Wordle Grid (dynamically sized to fit 360x413dp cleanly without clipping)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val tileSize = minOf(
                                44.dp,
                                ((maxHeight - 22.dp) / 6f).coerceAtLeast(24.dp),
                                ((maxWidth - 20.dp) / 5f).coerceAtLeast(24.dp),
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                for (rowIdx in 0 until 6) {
                                    val submitted = ui.progress.guesses.getOrNull(rowIdx)
                                    val evals = submitted?.let { WordleLogic.evaluateGuess(it, puzzle.solution) }
                                    val isCurrentRow = !ui.progress.completed && rowIdx == ui.progress.guesses.size

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        for (colIdx in 0 until 5) {
                                            val letter = when {
                                                submitted != null -> submitted[colIdx].toString()
                                                isCurrentRow && colIdx < ui.currentInput.length -> ui.currentInput[colIdx].toString()
                                                else -> ""
                                            }
                                            val eval = evals?.get(colIdx) ?: LetterEval.UNUSED
                                            WordleTile(
                                                letter = letter,
                                                eval = eval,
                                                isCurrentFilled = isCurrentRow && letter.isNotEmpty(),
                                                tileSize = tileSize,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Feedback / Share Result Banner
                        if (ui.message.isNotBlank() || ui.progress.completed) {
                            val bannerText = when {
                                ui.message.isNotBlank() -> ui.message
                                ui.progress.won -> "Solved in ${ui.progress.guesses.size}/6"
                                else -> "Answer: ${puzzle.solution}"
                            }
                            Text(
                                text = bannerText,
                                color = content,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        if (ui.progress.completed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(content, RoundedCornerShape(10.dp))
                                    .lightClickable {
                                        navigateTo(screenFactory = { act ->
                                            ShareCardScreen(act, ShareScope.WORDLE)
                                        })
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Open Share Card",
                                    color = bg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        } else {
                            // Built-in Compact OLED QWERTY Keyboard
                            WordleKeyboard(
                                keyStates = keyStates,
                                onLetter = { viewModel.onLetter(it) },
                                onEnter = { viewModel.onEnter() },
                                onBackspace = { viewModel.onBackspace() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordleTile(
    letter: String,
    eval: LetterEval,
    isCurrentFilled: Boolean,
    tileSize: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background

    val tileBg = when (eval) {
        LetterEval.EXACT -> content
        LetterEval.PRESENT -> content.copy(alpha = 0.16f)
        LetterEval.ABSENT -> bg
        LetterEval.UNUSED -> bg
    }
    val borderWidth = when {
        eval == LetterEval.PRESENT -> 2.5.dp
        eval == LetterEval.EXACT -> 1.dp
        isCurrentFilled -> 1.8.dp
        else -> 1.dp
    }
    val borderColor = when {
        eval == LetterEval.EXACT -> content
        eval == LetterEval.PRESENT -> content
        eval == LetterEval.ABSENT -> content.copy(alpha = 0.22f)
        isCurrentFilled -> content.copy(alpha = 0.85f)
        else -> content.copy(alpha = 0.35f)
    }
    val textColor = when (eval) {
        LetterEval.EXACT -> bg
        LetterEval.PRESENT -> content
        LetterEval.ABSENT -> content.copy(alpha = 0.42f)
        LetterEval.UNUSED -> content
    }

    Box(
        modifier = Modifier
            .size(tileSize)
            .background(tileBg, RoundedCornerShape(6.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = textColor,
            fontSize = (tileSize.value * 0.48f).coerceIn(14f, 21f).sp,
            fontWeight = FontWeight.Bold,
        )
        // Corner indicator dot for PRESENT ("wrong spot") for instant monochrome clarity
        if (eval == LetterEval.PRESENT) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .background(content, CircleShape),
            )
        }
    }
}

@Composable
private fun WordleKeyboard(
    keyStates: Map<Char, LetterEval>,
    onLetter: (Char) -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (ch in rows[0]) {
                WordleKeyButton(
                    label = ch.toString(),
                    eval = keyStates[ch] ?: LetterEval.UNUSED,
                    modifier = Modifier.weight(1f),
                    onClick = { onLetter(ch) },
                )
            }
        }
        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (ch in rows[1]) {
                WordleKeyButton(
                    label = ch.toString(),
                    eval = keyStates[ch] ?: LetterEval.UNUSED,
                    modifier = Modifier.weight(1f),
                    onClick = { onLetter(ch) },
                )
            }
        }
        // Row 3 (ENTER + ZXCVBNM + ⌫)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            WordleKeyButton(
                label = "GO",
                eval = LetterEval.UNUSED,
                emphasized = true,
                modifier = Modifier.weight(1.5f),
                onClick = onEnter,
            )
            for (ch in rows[2]) {
                WordleKeyButton(
                    label = ch.toString(),
                    eval = keyStates[ch] ?: LetterEval.UNUSED,
                    modifier = Modifier.weight(1f),
                    onClick = { onLetter(ch) },
                )
            }
            WordleKeyButton(
                label = "\u232B",
                eval = LetterEval.UNUSED,
                emphasized = true,
                modifier = Modifier.weight(1.5f),
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun WordleKeyButton(
    label: String,
    eval: LetterEval,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background

    val keyBg = when (eval) {
        LetterEval.EXACT -> content
        LetterEval.PRESENT -> content.copy(alpha = 0.22f)
        LetterEval.ABSENT -> bg
        LetterEval.UNUSED -> if (emphasized) content.copy(alpha = 0.14f) else bg
    }
    val borderColor = when (eval) {
        LetterEval.EXACT -> content
        LetterEval.PRESENT -> content
        LetterEval.ABSENT -> content.copy(alpha = 0.16f)
        LetterEval.UNUSED -> content.copy(alpha = if (emphasized) 0.7f else 0.45f)
    }
    val textColor = when (eval) {
        LetterEval.EXACT -> bg
        LetterEval.PRESENT -> content
        LetterEval.ABSENT -> content.copy(alpha = 0.30f)
        LetterEval.UNUSED -> content
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .background(keyBg, RoundedCornerShape(6.dp))
            .border(if (eval == LetterEval.PRESENT) 2.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (label.length > 1) 12.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
