package com.setholson.dailypuzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.FermiScoring
import com.setholson.dailypuzzles.data.LetterEval
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.data.WordleLogic
import com.setholson.dailypuzzles.model.ChessProgress
import com.setholson.dailypuzzles.model.CrosswordProgress
import com.setholson.dailypuzzles.model.DailyChessPuzzle
import com.setholson.dailypuzzles.model.DailyCrosswordPuzzle
import com.setholson.dailypuzzles.model.DailyFermiPuzzle
import com.setholson.dailypuzzles.model.DailyWordlePuzzle
import com.setholson.dailypuzzles.model.FermiProgress
import com.setholson.dailypuzzles.model.TodayDashboardState
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
import java.text.NumberFormat
import java.util.Locale

enum class ShareScope(val label: String) {
    ALL("All Done"),
    WORDLE("Word"),
    FERMI("Fermi"),
    CHESS("Chess"),
    CROSSWORD("Crossword"),
}

// Vibrant RGB colors: Light Phone III renders monochrome on screen,
// while screenshots save in full color for crisp sharing via Messages.
private val CardBg = Color(0xFF121214)
private val CardSurface = Color(0xFF1C1C20)
private val CardInk = Color(0xFFFFFFFF)
private val CardMuted = Color(0xFFB5B5BE)
private val ExactGreen = Color(0xFF538D4E)
private val PresentYellow = Color(0xFFB59F3B)
private val MissDark = Color(0xFF3A3A3C)
private val AccentBlue = Color(0xFF4A90E2)

class ShareCardViewModel(
    private val repository: PuzzleRepository,
    initialScope: ShareScope,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(TodayDashboardState())
    val state: StateFlow<TodayDashboardState> = _state.asStateFlow()

    private val _scope = MutableStateFlow(initialScope)
    val scope: StateFlow<ShareScope> = _scope.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            _state.value = repository.loadTodayDashboard()
        }
    }

    fun selectScope(newScope: ShareScope) {
        _scope.value = newScope
    }
}

/**
 * Pure LightOS-native spoiler-free Share Card screen.
 * Designed for Power + Volume Down screenshot -> Album -> Messages.
 */
class ShareCardScreen(
    private val sealedActivity: SealedLightActivity,
    private val initialScope: ShareScope = ShareScope.ALL,
) : LightScreen<Unit, ShareCardViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }

    override val viewModelClass: Class<ShareCardViewModel>
        get() = ShareCardViewModel::class.java

    override fun createViewModel(): ShareCardViewModel = ShareCardViewModel(repository, initialScope)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val scope by viewModel.scope.collectAsState()
        var cleanShotMode by remember { mutableStateOf(false) }

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .swipeToBack { goBack() },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!cleanShotMode) {
                        LightTopBar(
                            leftButton = LightBarButton.LightIcon(
                                icon = LightIcons.BACK,
                                onClick = { goBack() },
                            ),
                            center = LightTopBarCenter.Text("Share Card"),
                            rightButton = null,
                            modifier = Modifier.padding(bottom = 0.05f.gridUnitsAsDp()),
                        )

                        // Scope switcher when multiple puzzles are done
                        if (state.completedCount > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                            ) {
                                val tabs = buildList {
                                    add(ShareScope.ALL)
                                    if (state.wordleProgress?.completed == true) add(ShareScope.WORDLE)
                                    if (state.fermiProgress?.completed == true) add(ShareScope.FERMI)
                                    if (state.chessProgress?.completed == true) add(ShareScope.CHESS)
                                    if (state.crosswordProgress?.completed == true) add(ShareScope.CROSSWORD)
                                }
                                tabs.forEach { tab ->
                                    val selected = scope == tab
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (selected) Color.White else Color.Black,
                                                RoundedCornerShape(6.dp),
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .lightClickable { viewModel.selectScope(tab) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = tab.label,
                                            color = if (selected) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Centered Spoiler-Free Graphic Result Card (tap to toggle frameless Clean Shot mode)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .lightClickable { cleanShotMode = !cleanShotMode }
                            .padding(horizontal = 14.dp, vertical = if (cleanShotMode) 18.dp else 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isLoading) {
                            Text("Preparing card\u2026", color = CardMuted, fontSize = 14.sp)
                        } else {
                            ResultGraphicCard(
                                state = state,
                                scope = scope,
                            )
                        }
                    }

                    // Bottom Screenshot Guide (hidden in Clean Shot Mode)
                    if (!cleanShotMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Snap: Power + Vol Down \u2192 tap preview \u2192 Send \u2192 Trash",
                                color = content.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Tap card for frameless shot (sends in full color!)",
                                color = content.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultGraphicCard(
    state: TodayDashboardState,
    scope: ShareScope,
) {
    val showWord = (scope == ShareScope.ALL || scope == ShareScope.WORDLE) &&
        state.wordlePuzzle != null && state.wordleProgress?.completed == true
    val showFermi = (scope == ShareScope.ALL || scope == ShareScope.FERMI) &&
        state.fermiPuzzle != null && state.fermiProgress?.completed == true
    val showChess = (scope == ShareScope.ALL || scope == ShareScope.CHESS) &&
        state.chessPuzzle != null && state.chessProgress?.completed == true
    val showCrossword = (scope == ShareScope.ALL || scope == ShareScope.CROSSWORD) &&
        state.crosswordPuzzle != null && state.crosswordProgress?.completed == true

    val compact = scope == ShareScope.ALL && listOf(showWord, showFermi, showChess, showCrossword).count { it } >= 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DAILY PUZZLES",
                color = CardMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = state.todayPretty.ifBlank { state.todayIso },
                color = CardInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (showWord) {
            DailyWordCardSection(
                puzzle = state.wordlePuzzle,
                progress = state.wordleProgress,
                compact = compact,
            )
        }

        if (showFermi) {
            FermiCardSection(
                puzzle = state.fermiPuzzle,
                progress = state.fermiProgress,
                compact = compact,
            )
        }

        if (showChess) {
            ChessCardSection(
                puzzle = state.chessPuzzle,
                progress = state.chessProgress,
            )
        }

        if (showCrossword) {
            CrosswordCardSection(
                puzzle = state.crosswordPuzzle,
                progress = state.crosswordProgress,
            )
        }

        if (!showWord && !showFermi && !showChess && !showCrossword) {
            Text(
                text = "Finish a puzzle first to generate today\u2019s share card.",
                color = CardMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DailyWordCardSection(
    puzzle: DailyWordlePuzzle,
    progress: WordleProgress,
    compact: Boolean,
) {
    val numFormatted = if (puzzle.puzzleNumber > 0) {
        NumberFormat.getIntegerInstance(Locale.US).format(puzzle.puzzleNumber)
    } else {
        ""
    }
    val score = if (progress.won) "${progress.guesses.size}/6" else "X/6"
    val tileSize = if (compact) 20.dp else 30.dp
    val tileGap = if (compact) 4.dp else 5.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (numFormatted.isNotEmpty()) "Daily Word #$numFormatted" else "Daily Word",
                color = CardInk,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = score,
                color = if (progress.won) ExactGreen else CardMuted,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(tileGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            progress.guesses.forEach { guess ->
                val evals = WordleLogic.evaluateGuess(guess, puzzle.solution)
                Row(horizontalArrangement = Arrangement.spacedBy(tileGap)) {
                    evals.forEach { eval ->
                        val color = when (eval) {
                            LetterEval.EXACT -> ExactGreen
                            LetterEval.PRESENT -> PresentYellow
                            else -> MissDark
                        }
                        Box(
                            modifier = Modifier
                                .size(tileSize)
                                .background(color, RoundedCornerShape(4.dp))
                                .border(
                                    width = if (eval == LetterEval.PRESENT) 1.5.dp else 0.5.dp,
                                    color = when (eval) {
                                        LetterEval.EXACT -> Color.White.copy(alpha = 0.5f)
                                        LetterEval.PRESENT -> Color.White.copy(alpha = 0.85f)
                                        else -> Color.White.copy(alpha = 0.14f)
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FermiCardSection(
    puzzle: DailyFermiPuzzle,
    progress: FermiProgress,
    compact: Boolean,
) {
    val geo = FermiScoring.geometricMean(progress.records)
    val header = if (puzzle.puzzleNumber > 0) "Daily Fermi #${puzzle.puzzleNumber}" else "Daily Fermi"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header,
                color = CardInk,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${FermiScoring.formatMultiple(geo)} avg",
                color = fermiAccuracyColor(geo),
                fontSize = if (compact) 14.sp else 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            progress.records.forEachIndexed { idx, rec ->
                val dotColor = fermiAccuracyColor(rec.multiple)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(CardBg, RoundedCornerShape(8.dp))
                        .border(1.dp, dotColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(vertical = if (compact) 5.dp else 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(dotColor, CircleShape),
                        )
                        Text(
                            text = "Q${idx + 1}",
                            color = CardMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = FermiScoring.formatMultiple(rec.multiple),
                        color = CardInk,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = rec.direction,
                        color = CardMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChessCardSection(
    puzzle: DailyChessPuzzle,
    progress: ChessProgress,
) {
    val perfect = progress.mistakes == 0 && progress.hintsUsed == 0
    val statusText = if (perfect) "\u2605 Perfect" else "${progress.mistakes} miss \u00B7 ${progress.hintsUsed} hint"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Daily Chess #${puzzle.puzzleNumber}",
                color = CardInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = puzzle.title,
                color = CardMuted,
                fontSize = 11.sp,
            )
        }
        Text(
            text = statusText,
            color = if (perfect) ExactGreen else AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CrosswordCardSection(
    puzzle: DailyCrosswordPuzzle,
    progress: CrosswordProgress,
) {
    val mins = progress.elapsedSeconds / 60
    val secs = progress.elapsedSeconds % 60
    val timeStr = String.format(Locale.US, "%d:%02d", mins, secs)
    val hintStr = if (progress.hintsUsed == 0) "No hints" else "${progress.hintsUsed} hints"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Daily Crossword #${puzzle.id}",
                color = CardInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "9\u00D79 Medium \u00B7 $hintStr",
                color = CardMuted,
                fontSize = 11.sp,
            )
        }
        Text(
            text = timeStr,
            color = if (progress.hintsUsed == 0) ExactGreen else AccentBlue,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun fermiAccuracyColor(multiple: Double): Color = when {
    multiple <= 1.5 -> ExactGreen
    multiple <= 4.0 -> PresentYellow
    else -> CardMuted
}
