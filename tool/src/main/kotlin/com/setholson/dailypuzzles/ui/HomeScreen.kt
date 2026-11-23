package com.setholson.dailypuzzles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.FermiScoring
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.model.TodayDashboardState
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
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

class HomeViewModel(
    private val repository: PuzzleRepository,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(TodayDashboardState())
    val state: StateFlow<TodayDashboardState> = _state.asStateFlow()

    private var appliedThemeOnce = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value.chessPuzzle == null) {
                _state.value = _state.value.copy(isLoading = true)
            }
            val loaded = repository.loadTodayDashboard()
            if (!appliedThemeOnce) {
                appliedThemeOnce = true
                if (loaded.settings.darkMode) {
                    LightThemeController.setDarkTheme()
                } else {
                    LightThemeController.setLightTheme()
                }
            }
            _state.value = loaded
        }
    }

    fun buildCombinedShareText(): String = repository.buildCombinedShareText(_state.value)
}

@InitialScreen
class HomeScreen(
    private val sealedActivity: SealedLightActivity,
) : LightScreen<Unit, HomeViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel = HomeViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content
            val bg = LightThemeTokens.colors.background
            val sidePad = 1.5f.gridUnitsAsDp()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        leftButton = null,
                        center = LightTopBarCenter.Text("Daily Puzzles"),
                        rightButton = LightBarButton.LightIcon(
                            icon = LightIcons.SETTINGS,
                            onClick = {
                                navigateTo(screenFactory = { act -> SettingsScreen(act) })
                            },
                        ),
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = sidePad),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = sidePad, top = 6.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Today's Date & Calm Progress Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.todayPretty.ifBlank { "Today" },
                                    color = content,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${state.completedCount} / 4 done",
                                    color = content.copy(alpha = 0.72f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            Text(
                                text = "Today\u2019s puzzles only \u2014 offline, no archives, no endless scroll.",
                                color = content.copy(alpha = 0.58f),
                                fontSize = 12.sp,
                            )

                            if (state.isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, content.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                        .padding(18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Preparing today\u2019s puzzles\u2026",
                                        color = content.copy(alpha = 0.75f),
                                        fontSize = 14.sp,
                                    )
                                }
                            } else if (state.lockStatus.isLocked) {
                                // Calm Lockout Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, content.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Puzzles Locked",
                                        color = content,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = state.lockStatus.reason,
                                        color = content.copy(alpha = 0.78f),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .lightClickable {
                                                navigateTo(screenFactory = { act -> SettingsScreen(act) })
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Lockout Settings",
                                            color = content,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            } else {
                                // 1. Daily Word (5-letter word deduction)
                                val wp = state.wordlePuzzle
                                val wProg = state.wordleProgress
                                val wordSubtitle = when {
                                    wp == null -> "Loading..."
                                    wProg?.completed == true && wProg.won -> "Solved in ${wProg.guesses.size}/6"
                                    wProg?.completed == true -> "Finished (X/6)"
                                    (wProg?.guesses?.size ?: 0) > 0 -> "${wProg!!.guesses.size}/6 guesses used"
                                    else -> "5-letter daily word"
                                }
                                PuzzleHomeCard(
                                    title = "Daily Word",
                                    badge = if (wp != null) "#${wp.puzzleNumber}" else "",
                                    subtitle = wordSubtitle,
                                    completed = wProg?.completed == true,
                                    onClick = {
                                        navigateTo(screenFactory = { act -> WordleScreen(act) })
                                    },
                                )

                                // 2. Daily Fermi (3 daily estimation questions)
                                val fp = state.fermiPuzzle
                                val fProg = state.fermiProgress
                                val fermiSubtitle = when {
                                    fp == null -> "Loading..."
                                    fProg?.completed == true && fProg.records.isNotEmpty() -> {
                                        val avg = FermiScoring.formatMultiple(FermiScoring.geometricMean(fProg.records))
                                        "Completed \u00B7 $avg average"
                                    }
                                    (fProg?.records?.size ?: 0) > 0 -> {
                                        "${fProg!!.records.size} / ${fp.questions.size} answered"
                                    }
                                    else -> "${fp.questions.size} daily estimation questions"
                                }
                                PuzzleHomeCard(
                                    title = "Daily Fermi",
                                    badge = if (fp != null) "#${fp.puzzleNumber}" else "",
                                    subtitle = fermiSubtitle,
                                    completed = fProg?.completed == true,
                                    onClick = {
                                        navigateTo(screenFactory = { act -> FermiScreen(act) })
                                    },
                                )

                                // 3. Daily Chess (classic tactical puzzle)
                                val cp = state.chessPuzzle
                                val cProg = state.chessProgress
                                val chessSubtitle = when {
                                    cp == null -> "Loading..."
                                    cProg?.completed == true -> {
                                        if (cProg.mistakes == 0 && cProg.hintsUsed == 0) {
                                            "${cp.title} \u00B7 Solved \u2605"
                                        } else {
                                            "${cp.title} \u00B7 Solved"
                                        }
                                    }
                                    (cProg?.currentMoveIndex ?: 0) > 0 -> "${cp.title} \u00B7 In progress"
                                    else -> cp.title
                                }
                                PuzzleHomeCard(
                                    title = "Daily Chess",
                                    badge = if (cp != null) "#${cp.puzzleNumber}" else "",
                                    subtitle = chessSubtitle,
                                    completed = cProg?.completed == true,
                                    onClick = {
                                        navigateTo(screenFactory = { act -> ChessScreen(act) })
                                    },
                                )

                                // 4. Daily Crossword (9x9 Medium)
                                val cw = state.crosswordPuzzle
                                val cwProg = state.crosswordProgress
                                val totalCells = cw?.grid?.sumOf { row -> row.count { it != '#' } } ?: 1
                                val filledCells = cwProg?.entries?.count { it.value.isNotBlank() } ?: 0
                                val cwSubtitle = when {
                                    cw == null -> "Loading..."
                                    cwProg?.completed == true -> {
                                        val mins = cwProg.elapsedSeconds / 60
                                        val secs = cwProg.elapsedSeconds % 60
                                        String.format(Locale.US, "Solved in %d:%02d", mins, secs)
                                    }
                                    filledCells > 0 -> "$filledCells / $totalCells letters filled"
                                    else -> "${cw.across.size + cw.down.size} clues \u00B7 9\u00D79 Medium"
                                }
                                PuzzleHomeCard(
                                    title = "Daily Crossword",
                                    badge = if (cw != null) "9\u00D79 #${cw.id}" else "9\u00D79",
                                    subtitle = cwSubtitle,
                                    completed = cwProg?.completed == true,
                                    onClick = {
                                        navigateTo(screenFactory = { act -> CrosswordScreen(act) })
                                    },
                                )

                                // All done message
                                if (state.allCompleted) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, content.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "All 4 puzzles done for today. Fresh set tomorrow.",
                                            color = content,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }

                            // 1-Tap Combined Share Card for all completed puzzles
                            if (!state.isLoading && state.completedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(content, RoundedCornerShape(10.dp))
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                ShareCardScreen(act, ShareScope.ALL)
                                            })
                                        }
                                        .padding(vertical = 12.dp, horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Open Today\u2019s Share Card (${state.completedCount})",
                                        color = bg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleHomeCard(
    title: String,
    badge: String,
    subtitle: String,
    completed: Boolean,
    onClick: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    val borderAlpha = if (completed) 0.45f else 0.78f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (completed) 1.dp else 1.5.dp,
                color = content.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(12.dp),
            )
            .lightClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = content,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (badge.isNotBlank()) {
                    Text(
                        text = badge,
                        color = content.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = subtitle,
                color = content.copy(alpha = if (completed) 0.85f else 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .background(
                    if (completed) content else bg,
                    RoundedCornerShape(6.dp),
                )
                .border(1.dp, content.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (completed) "Done" else "Play",
                color = if (completed) bg else content,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
