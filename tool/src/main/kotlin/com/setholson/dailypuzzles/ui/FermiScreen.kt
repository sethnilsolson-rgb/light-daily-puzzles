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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.FermiScoring
import com.setholson.dailypuzzles.data.OfflinePuzzleBank
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.model.DailyFermiPuzzle
import com.setholson.dailypuzzles.model.FermiGuessRecord
import com.setholson.dailypuzzles.model.FermiProgress
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

data class FermiUiState(
    val puzzle: DailyFermiPuzzle? = null,
    val progress: FermiProgress = FermiProgress(dateIso = ""),
    val activeIndex: Int = 0,
    val showingReveal: Boolean = false,
    val inputText: String = "",
    val errorText: String = "",
    val showScratchCalc: Boolean = false,
    val calcLeft: Double? = null,
    val calcOp: String? = null,
    val calcDisplay: String = "",
    val isLoading: Boolean = true,
)

class FermiViewModel(
    private val repository: PuzzleRepository,
) : LightViewModel<Unit>() {

    private val _ui = MutableStateFlow(FermiUiState())
    val ui: StateFlow<FermiUiState> = _ui.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val today = OfflinePuzzleBank.todayLocalIso()
            val puzzle = repository.getTodayFermi(today)
            val progress = repository.getFermiProgress(today)
            val nextIdx = progress.records.size.coerceAtMost(puzzle.questions.lastIndex.coerceAtLeast(0))
            _ui.value = FermiUiState(
                puzzle = puzzle,
                progress = progress,
                activeIndex = nextIdx,
                showingReveal = false,
                isLoading = false,
            )
        }
    }

    fun onKey(token: String) {
        val s = _ui.value
        if (s.showingReveal || s.progress.completed) return
        if (s.showScratchCalc) {
            onCalcKey(token)
            return
        }
        val cur = s.inputText
        val next = when (token) {
            "DEL" -> cur.dropLast(1)
            "CLR" -> ""
            "K", "M", "B" -> {
                val base = cur.trimEnd('K', 'M', 'B')
                if (base.isEmpty()) "" else base + token
            }
            "." -> if ('.' in cur || cur.lastOrNull() in listOf('K', 'M', 'B')) cur else if (cur.isEmpty()) "0." else "$cur."
            else -> {
                if (cur.lastOrNull() in listOf('K', 'M', 'B')) cur else (cur + token).take(14)
            }
        }
        _ui.value = s.copy(inputText = next, errorText = "")
    }

    fun toggleScratchCalc() {
        val s = _ui.value
        _ui.value = s.copy(showScratchCalc = !s.showScratchCalc, errorText = "")
    }

    private fun onCalcKey(token: String) {
        val s = _ui.value
        when (token) {
            "CLR" -> _ui.value = s.copy(calcLeft = null, calcOp = null, calcDisplay = "")
            "DEL" -> _ui.value = s.copy(calcDisplay = s.calcDisplay.dropLast(1))
            "+", "\u2212", "\u00D7", "\u00F7" -> {
                val parsed = FermiScoring.parseEstimate(s.calcDisplay) ?: s.calcLeft ?: return
                val combined = if (s.calcLeft != null && s.calcOp != null && s.calcDisplay.isNotEmpty()) {
                    evalCalc(s.calcLeft, parsed, s.calcOp)
                } else {
                    parsed
                }
                _ui.value = s.copy(calcLeft = combined, calcOp = token, calcDisplay = "")
            }
            "=" -> {
                val left = s.calcLeft ?: return
                val op = s.calcOp ?: return
                val right = FermiScoring.parseEstimate(s.calcDisplay) ?: return
                val res = evalCalc(left, right, op)
                _ui.value = s.copy(
                    calcLeft = null,
                    calcOp = null,
                    calcDisplay = FermiScoring.formatNumberCompact(res),
                )
            }
            "K", "M", "B" -> {
                val base = s.calcDisplay.trimEnd('K', 'M', 'B')
                if (base.isNotEmpty()) _ui.value = s.copy(calcDisplay = base + token)
            }
            "." -> {
                if ('.' !in s.calcDisplay) {
                    _ui.value = s.copy(calcDisplay = if (s.calcDisplay.isEmpty()) "0." else "${s.calcDisplay}.")
                }
            }
            else -> {
                if (s.calcDisplay.lastOrNull() !in listOf('K', 'M', 'B')) {
                    _ui.value = s.copy(calcDisplay = (s.calcDisplay + token).take(14))
                }
            }
        }
    }

    fun useCalcResult() {
        val s = _ui.value
        val valNum = FermiScoring.parseEstimate(s.calcDisplay) ?: s.calcLeft ?: return
        _ui.value = s.copy(
            inputText = FermiScoring.formatNumberCompact(valNum),
            showScratchCalc = false,
            errorText = "",
        )
    }

    private fun evalCalc(a: Double, b: Double, op: String): Double = when (op) {
        "+" -> a + b
        "\u2212" -> (a - b).coerceAtLeast(0.0)
        "\u00D7" -> a * b
        "\u00F7" -> if (b == 0.0) a else a / b
        else -> b
    }

    fun submitGuess() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val q = puzzle.questions.getOrNull(s.activeIndex) ?: return
        val guess = FermiScoring.parseEstimate(s.inputText)
        if (guess == null || guess <= 0.0) {
            _ui.value = s.copy(errorText = "Enter a positive estimate (e.g. 500K or 1.2M)")
            return
        }
        val multiple = FermiScoring.computeMultiple(guess, q.answer)
        val direction = FermiScoring.directionLabel(guess, q.answer)
        val record = FermiGuessRecord(
            questionId = q.id,
            guess = guess,
            multiple = multiple,
            direction = direction,
        )
        val nextRecords = s.progress.records.filterNot { it.questionId == q.id } + record
        val completed = nextRecords.size >= puzzle.questions.size
        val nextProg = s.progress.copy(
            dateIso = puzzle.dateIso,
            records = nextRecords,
            completed = completed,
        )
        _ui.value = s.copy(
            progress = nextProg,
            showingReveal = true,
            showScratchCalc = false,
            errorText = "",
        )
        viewModelScope.launch {
            repository.saveFermiProgress(nextProg)
        }
    }

    fun nextQuestion() {
        val s = _ui.value
        val puzzle = s.puzzle ?: return
        val nextIdx = (s.activeIndex + 1).coerceAtMost(puzzle.questions.lastIndex)
        _ui.value = s.copy(
            activeIndex = nextIdx,
            showingReveal = false,
            inputText = "",
            errorText = "",
            calcLeft = null,
            calcOp = null,
            calcDisplay = "",
        )
    }

    fun buildShareText(): String {
        val puzzle = _ui.value.puzzle ?: return ""
        return FermiScoring.buildShareText(puzzle, _ui.value.progress)
    }
}

class FermiScreen(
    private val sealedActivity: SealedLightActivity,
) : LightScreen<Unit, FermiViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }

    override val viewModelClass: Class<FermiViewModel>
        get() = FermiViewModel::class.java

    override fun createViewModel(): FermiViewModel = FermiViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val ui by viewModel.ui.collectAsState()

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content
            val bg = LightThemeTokens.colors.background
            val sidePad = 1.5f.gridUnitsAsDp()
            val title = ui.puzzle?.let { p ->
                if (p.puzzleNumber > 0) "Daily Fermi #${p.puzzleNumber}" else "Daily Fermi"
            } ?: "Daily Fermi"

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .swipeToBack { goBack() },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
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
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Loading Daily Fermi\u2026", color = content.copy(alpha = 0.7f), fontSize = 15.sp)
                        }
                    } else if (ui.progress.completed && !ui.showingReveal) {
                        // Final Daily Fermi Summary Screen
                        val puzzle = ui.puzzle!!
                        val geo = FermiScoring.geometricMean(ui.progress.records)
                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = sidePad),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = sidePad, top = 8.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, content, RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Daily Average Score",
                                        color = content.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        text = FermiScoring.formatMultiple(geo),
                                        color = content,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${FermiScoring.ratingLabel(geo)} \u00B7 Daily Fermi #${puzzle.puzzleNumber}",
                                        color = content.copy(alpha = 0.75f),
                                        fontSize = 13.sp,
                                    )
                                }

                                puzzle.questions.forEachIndexed { idx, q ->
                                    val rec = ui.progress.records.firstOrNull { it.questionId == q.id }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, content.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Q${idx + 1}. ${q.prompt}",
                                            color = content,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (rec != null) {
                                            Text(
                                                text = "You: ${FermiScoring.formatNumberCompact(rec.guess)}  \u2022  Answer: ${FermiScoring.formatNumberExact(q.answer)} ${q.unit}",
                                                color = content.copy(alpha = 0.78f),
                                                fontSize = 12.sp,
                                            )
                                            Text(
                                                text = "${FermiScoring.formatMultiple(rec.multiple)} ${rec.direction} (${FermiScoring.ratingLabel(rec.multiple)})",
                                                color = content,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(content, RoundedCornerShape(10.dp))
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                ShareCardScreen(act, ShareScope.FERMI)
                                            })
                                        }
                                        .padding(vertical = 13.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Open Fermi Share Card",
                                        color = bg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    } else {
                        val puzzle = ui.puzzle!!
                        val q = puzzle.questions[ui.activeIndex]
                        val curRec = ui.progress.records.firstOrNull { it.questionId == q.id }

                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = sidePad),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = sidePad, top = 4.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Progress pill
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Question ${ui.activeIndex + 1} of ${puzzle.questions.size}",
                                        color = content.copy(alpha = 0.72f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Daily #${puzzle.puzzleNumber}",
                                        color = content.copy(alpha = 0.58f),
                                        fontSize = 11.sp,
                                    )
                                }

                                // Prompt Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, content.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = q.prompt,
                                        color = content,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 22.sp,
                                    )
                                    if (q.unit.isNotBlank()) {
                                        Text(
                                            text = "Answer in: ${q.unit}",
                                            color = content.copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                        )
                                    }
                                }

                                if (ui.showingReveal && curRec != null) {
                                    // Reveal card with exact answer + multiple + napkin math steps
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.5.dp, content, RoundedCornerShape(10.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column {
                                                Text("Exact Answer", color = content.copy(alpha = 0.65f), fontSize = 11.sp)
                                                Text(
                                                    text = "${FermiScoring.formatNumberExact(q.answer)} ${q.unit}",
                                                    color = content,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = FermiScoring.formatMultiple(curRec.multiple),
                                                    color = content,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "${curRec.direction} \u00B7 ${FermiScoring.ratingLabel(curRec.multiple)}",
                                                    color = content.copy(alpha = 0.75f),
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Your guess: ${FermiScoring.formatNumberCompact(curRec.guess)}",
                                            color = content.copy(alpha = 0.78f),
                                            fontSize = 13.sp,
                                        )

                                        if (q.mathSteps.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Back-of-the-envelope math:",
                                                color = content.copy(alpha = 0.68f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            q.mathSteps.forEach { step ->
                                                val left = step.getOrNull(0) ?: ""
                                                val right = step.getOrNull(1) ?: ""
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text(
                                                        text = left,
                                                        color = content.copy(alpha = 0.8f),
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Text(
                                                        text = right,
                                                        color = content,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val isLast = ui.activeIndex >= puzzle.questions.lastIndex
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(content, RoundedCornerShape(10.dp))
                                            .lightClickable { viewModel.nextQuestion() }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (isLast) "See Daily Summary & Share" else "Next Question \u2192",
                                            color = bg,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    // Estimate input display + Napkin Calculator toggle
                                    val parsedPreview = FermiScoring.parseEstimate(ui.inputText)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.5.dp, content, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (ui.showScratchCalc) {
                                                    val prefix = if (ui.calcLeft != null && ui.calcOp != null) {
                                                        "${FermiScoring.formatNumberCompact(ui.calcLeft!!)} ${ui.calcOp} "
                                                    } else {
                                                        ""
                                                    }
                                                    (prefix + ui.calcDisplay).ifBlank { "Scratchpad: 0" }
                                                } else {
                                                    ui.inputText.ifBlank { "Tap estimate below" }
                                                },
                                                color = content,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (!ui.showScratchCalc && parsedPreview != null) {
                                                Text(
                                                    text = "= ${FermiScoring.formatNumberExact(parsedPreview)} ${q.unit}",
                                                    color = content.copy(alpha = 0.65f),
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (ui.showScratchCalc) content else bg,
                                                    RoundedCornerShape(6.dp),
                                                )
                                                .border(1.dp, content.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                                .lightClickable { viewModel.toggleScratchCalc() }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = if (ui.showScratchCalc) "Estimate" else "Calc",
                                                color = if (ui.showScratchCalc) bg else content,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }

                                    if (ui.errorText.isNotBlank()) {
                                        Text(text = ui.errorText, color = content, fontSize = 12.sp)
                                    }

                                    // Compact Built-In Fermi Keypad (Zero system keyboard clutter!)
                                    if (ui.showScratchCalc) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            listOf("+", "\u2212", "\u00D7", "\u00F7", "=").forEach { op ->
                                                FermiPadKey(
                                                    label = op,
                                                    modifier = Modifier.weight(1f),
                                                    emphasized = true,
                                                    onClick = { viewModel.onKey(op) },
                                                )
                                            }
                                        }
                                    }

                                    val keypadRows = listOf(
                                        listOf("7", "8", "9", "K"),
                                        listOf("4", "5", "6", "M"),
                                        listOf("1", "2", "3", "B"),
                                        listOf("CLR", "0", ".", "DEL"),
                                    )
                                    keypadRows.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            row.forEach { key ->
                                                FermiPadKey(
                                                    label = if (key == "DEL") "\u232B" else key,
                                                    modifier = Modifier.weight(1f),
                                                    emphasized = key in listOf("K", "M", "B", "CLR", "DEL"),
                                                    onClick = { viewModel.onKey(key) },
                                                )
                                            }
                                        }
                                    }

                                    // Primary Submit or Use Calc Result button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(content, RoundedCornerShape(10.dp))
                                            .lightClickable {
                                                if (ui.showScratchCalc) viewModel.useCalcResult() else viewModel.submitGuess()
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (ui.showScratchCalc) "Use Result as Estimate" else "Lock In Estimate",
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
}

@Composable
private fun FermiPadKey(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val bg = LightThemeTokens.colors.background
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (emphasized) content.copy(alpha = 0.14f) else bg, RoundedCornerShape(7.dp))
            .border(1.dp, content.copy(alpha = if (emphasized) 0.7f else 0.45f), RoundedCornerShape(7.dp))
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
