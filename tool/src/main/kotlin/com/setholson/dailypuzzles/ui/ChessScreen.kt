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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.setholson.dailypuzzles.data.ChessColor
import com.setholson.dailypuzzles.data.ChessEngine
import com.setholson.dailypuzzles.data.ChessMove
import com.setholson.dailypuzzles.data.ChessPosition
import com.setholson.dailypuzzles.data.OfflinePuzzleBank
import com.setholson.dailypuzzles.data.PieceType
import com.setholson.dailypuzzles.data.PuzzleRepository
import com.setholson.dailypuzzles.data.Square
import com.setholson.dailypuzzles.model.ChessProgress
import com.setholson.dailypuzzles.model.DailyChessPuzzle
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

enum class ChessFeedbackKind {
    INFO,
    WRONG,
    GOOD,
    HINT,
    SOLVED,
}

data class ChessUiState(
    val puzzle: DailyChessPuzzle? = null,
    val progress: ChessProgress = ChessProgress(dateIso = ""),
    val position: ChessPosition? = null,
    val playerColor: ChessColor = ChessColor.WHITE,
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val pendingPromotionFrom: Square? = null,
    val pendingPromotionTo: Square? = null,
    val lastMoveFrom: Square? = null,
    val lastMoveTo: Square? = null,
    val lastWrongSquare: Square? = null,
    val hintFrom: Square? = null,
    val hintTo: Square? = null,
    val feedback: String = "",
    val feedbackKind: ChessFeedbackKind = ChessFeedbackKind.INFO,
    val isOpponentThinking: Boolean = false,
    val isLoading: Boolean = true,
) {
    val totalPlayerMoves: Int
        get() = ((puzzle?.solutionSan?.size ?: 0) + 1) / 2

    val currentPlayerMoveNumber: Int
        get() = ((progress.currentMoveIndex / 2) + 1).coerceAtMost(totalPlayerMoves.coerceAtLeast(1))
}

class ChessViewModel(
    private val repository: PuzzleRepository,
) : LightViewModel<Unit>() {

    private val _ui = MutableStateFlow(ChessUiState())
    val ui: StateFlow<ChessUiState> = _ui.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val today = OfflinePuzzleBank.todayLocalIso()
            val puzzle = repository.getTodayChess(today)
            val progress = repository.getChessProgress(today)
            val startPos = ChessEngine.parseFen(puzzle.fen)
            val currentPos = ChessEngine.positionAfterMoves(
                puzzle.fen,
                puzzle.solutionSan,
                progress.currentMoveIndex,
            )
            val colorName = if (startPos.turn == ChessColor.WHITE) "White" else "Black"
            val solvedSummary = solvedBannerText(progress)
            _ui.value = ChessUiState(
                puzzle = puzzle,
                progress = progress,
                position = currentPos,
                playerColor = startPos.turn,
                feedback = if (progress.completed) solvedSummary else "Your turn ($colorName) \u2014 tap a $colorName piece",
                feedbackKind = if (progress.completed) ChessFeedbackKind.SOLVED else ChessFeedbackKind.INFO,
                isLoading = false,
            )
        }
    }

    fun onSquareTap(sq: Square) {
        val state = _ui.value
        val pos = state.position ?: return
        val puzzle = state.puzzle ?: return
        if (state.isLoading || state.progress.completed || state.isOpponentThinking) return
        if (state.pendingPromotionFrom != null) return

        // If tapping a legal target of the currently selected piece, execute the move
        val selected = state.selectedSquare
        if (selected != null && sq in state.legalTargets) {
            val candidates = ChessEngine.legalMovesFrom(pos, selected).filter { it.to == sq }
            if (candidates.any { it.promotion != null }) {
                _ui.value = state.copy(
                    pendingPromotionFrom = selected,
                    pendingPromotionTo = sq,
                    lastWrongSquare = null,
                    feedback = "Choose promotion piece",
                    feedbackKind = ChessFeedbackKind.INFO,
                )
                return
            }
            val move = candidates.firstOrNull() ?: return
            attemptPlayerMove(puzzle, pos, move)
            return
        }

        // Otherwise select player's own piece
        val piece = pos.pieceAt(sq)
        if (piece != null && piece.color == pos.turn) {
            val targets = ChessEngine.legalMovesFrom(pos, sq).map { it.to }.toSet()
            val colorName = if (pos.turn == ChessColor.WHITE) "White" else "Black"
            _ui.value = state.copy(
                selectedSquare = sq,
                legalTargets = targets,
                lastWrongSquare = null,
                feedback = if (targets.isEmpty()) {
                    "No legal moves for ${sq.toAlgebraic()}"
                } else {
                    "Selected ${sq.toAlgebraic()} ($colorName) \u2014 tap destination"
                },
                feedbackKind = ChessFeedbackKind.INFO,
            )
        } else {
            _ui.value = state.copy(
                selectedSquare = null,
                legalTargets = emptySet(),
                lastWrongSquare = null,
            )
        }
    }

    fun onChoosePromotion(pieceType: PieceType) {
        val state = _ui.value
        val puzzle = state.puzzle ?: return
        val pos = state.position ?: return
        val from = state.pendingPromotionFrom ?: return
        val to = state.pendingPromotionTo ?: return
        val move = ChessEngine.legalMovesFrom(pos, from)
            .firstOrNull { it.to == to && it.promotion == pieceType }
            ?: return
        _ui.value = state.copy(pendingPromotionFrom = null, pendingPromotionTo = null)
        attemptPlayerMove(puzzle, pos, move)
    }

    fun cancelPromotion() {
        val colorName = if (_ui.value.playerColor == ChessColor.WHITE) "White" else "Black"
        _ui.value = _ui.value.copy(
            pendingPromotionFrom = null,
            pendingPromotionTo = null,
            selectedSquare = null,
            legalTargets = emptySet(),
            feedback = "Your turn ($colorName)",
            feedbackKind = ChessFeedbackKind.INFO,
        )
    }

    fun requestHint() {
        val state = _ui.value
        val puzzle = state.puzzle ?: return
        val pos = state.position ?: return
        if (state.progress.completed || state.isOpponentThinking) return
        val expectedSan = puzzle.solutionSan.getOrNull(state.progress.currentMoveIndex) ?: return
        val expectedMove = ChessEngine.parseSanMove(pos, expectedSan) ?: return

        val alreadyShowedPiece = state.hintFrom == expectedMove.from && state.hintTo == null
        val alreadyShowedMove = state.hintFrom == expectedMove.from && state.hintTo == expectedMove.to
        val nextProg = if (alreadyShowedMove) {
            state.progress
        } else {
            state.progress.copy(hintsUsed = state.progress.hintsUsed + 1)
        }
        val showDest = alreadyShowedPiece || alreadyShowedMove

        _ui.value = state.copy(
            progress = nextProg,
            hintFrom = expectedMove.from,
            hintTo = if (showDest) expectedMove.to else null,
            lastWrongSquare = null,
            selectedSquare = expectedMove.from,
            legalTargets = ChessEngine.legalMovesFrom(pos, expectedMove.from).map { it.to }.toSet(),
            feedback = if (showDest) {
                "Hint: Move ${expectedMove.from.toAlgebraic()} \u2192 ${expectedMove.to.toAlgebraic()}"
            } else {
                "Hint: Move the piece on ${expectedMove.from.toAlgebraic()}"
            },
            feedbackKind = ChessFeedbackKind.HINT,
        )
        viewModelScope.launch { repository.saveChessProgress(nextProg) }
    }

    /** Executes the next expected move automatically (counts as a hint) when the player is stuck. */
    fun playHintMove() {
        val state = _ui.value
        val puzzle = state.puzzle ?: return
        val pos = state.position ?: return
        if (state.progress.completed || state.isOpponentThinking) return
        val expectedSan = puzzle.solutionSan.getOrNull(state.progress.currentMoveIndex) ?: return
        val expectedMove = ChessEngine.parseSanMove(pos, expectedSan) ?: return

        val nextProg = state.progress.copy(hintsUsed = state.progress.hintsUsed + 1)
        _ui.value = state.copy(
            progress = nextProg,
            pendingPromotionFrom = null,
            pendingPromotionTo = null,
            lastWrongSquare = null,
        )
        attemptPlayerMove(puzzle, pos, expectedMove, overrideProgress = nextProg)
    }

    fun resetPuzzle() {
        val state = _ui.value
        val puzzle = state.puzzle ?: return
        val startPos = ChessEngine.parseFen(puzzle.fen)
        val freshProg = ChessProgress(dateIso = puzzle.dateIso)
        val colorName = if (startPos.turn == ChessColor.WHITE) "White" else "Black"
        _ui.value = state.copy(
            progress = freshProg,
            position = startPos,
            selectedSquare = null,
            legalTargets = emptySet(),
            pendingPromotionFrom = null,
            pendingPromotionTo = null,
            lastMoveFrom = null,
            lastMoveTo = null,
            lastWrongSquare = null,
            hintFrom = null,
            hintTo = null,
            feedback = "Your turn ($colorName) \u2014 tap a $colorName piece",
            feedbackKind = ChessFeedbackKind.INFO,
            isOpponentThinking = false,
        )
        viewModelScope.launch { repository.saveChessProgress(freshProg) }
    }

    private fun attemptPlayerMove(
        puzzle: DailyChessPuzzle,
        pos: ChessPosition,
        move: ChessMove,
        overrideProgress: ChessProgress? = null,
    ) {
        val state = _ui.value
        val baseProg = overrideProgress ?: state.progress
        val idx = baseProg.currentMoveIndex
        val expectedSan = puzzle.solutionSan.getOrNull(idx) ?: return
        val expectedMove = ChessEngine.parseSanMove(pos, expectedSan)

        val afterPlayer = ChessEngine.applyMove(pos, move)
        val isExactMatch = expectedMove != null &&
            move.from == expectedMove.from &&
            move.to == expectedMove.to &&
            move.promotion == expectedMove.promotion
        val isImmediateMate = ChessEngine.isCheckmate(afterPlayer)

        if (!isExactMatch && !isImmediateMate) {
            val nextProg = baseProg.copy(
                dateIso = puzzle.dateIso,
                mistakes = baseProg.mistakes + 1,
            )
            _ui.value = state.copy(
                progress = nextProg,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastWrongSquare = move.to,
                feedback = "\u2717 Wrong (${move.from.toAlgebraic()}\u2192${move.to.toAlgebraic()}) \u2014 try again (Miss #${nextProg.mistakes})",
                feedbackKind = ChessFeedbackKind.WRONG,
            )
            viewModelScope.launch { repository.saveChessProgress(nextProg) }
            return
        }

        // Player's move is correct!
        val nextIdx = idx + 1
        if (isImmediateMate || nextIdx >= puzzle.solutionSan.size) {
            val doneProg = baseProg.copy(
                dateIso = puzzle.dateIso,
                currentMoveIndex = puzzle.solutionSan.size,
                completed = true,
            )
            _ui.value = state.copy(
                progress = doneProg,
                position = afterPlayer,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMoveFrom = move.from,
                lastMoveTo = move.to,
                lastWrongSquare = null,
                hintFrom = null,
                hintTo = null,
                feedback = solvedBannerText(doneProg),
                feedbackKind = ChessFeedbackKind.SOLVED,
            )
            viewModelScope.launch { repository.saveChessProgress(doneProg) }
            return
        }

        // Opponent auto-reply after brief visual pause
        val midProg = baseProg.copy(dateIso = puzzle.dateIso, currentMoveIndex = nextIdx)
        _ui.value = state.copy(
            progress = midProg,
            position = afterPlayer,
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMoveFrom = move.from,
            lastMoveTo = move.to,
            lastWrongSquare = null,
            hintFrom = null,
            hintTo = null,
            feedback = "\u2713 Good move! Opponent replying\u2026",
            feedbackKind = ChessFeedbackKind.GOOD,
            isOpponentThinking = true,
        )

        viewModelScope.launch {
            delay(420L)
            val replySan = puzzle.solutionSan.getOrNull(nextIdx)
            val replyMove = replySan?.let { ChessEngine.parseSanMove(afterPlayer, it) }
            if (replyMove != null) {
                val afterReply = ChessEngine.applyMove(afterPlayer, replyMove)
                val finalIdx = nextIdx + 1
                val done = finalIdx >= puzzle.solutionSan.size
                val updated = midProg.copy(currentMoveIndex = finalIdx, completed = done)
                val colorName = if (state.playerColor == ChessColor.WHITE) "White" else "Black"
                _ui.value = _ui.value.copy(
                    progress = updated,
                    position = afterReply,
                    lastMoveFrom = replyMove.from,
                    lastMoveTo = replyMove.to,
                    feedback = if (done) {
                        solvedBannerText(updated)
                    } else {
                        "Your turn ($colorName) \u2014 find the next move"
                    },
                    feedbackKind = if (done) ChessFeedbackKind.SOLVED else ChessFeedbackKind.INFO,
                    isOpponentThinking = false,
                )
                repository.saveChessProgress(updated)
            } else {
                val updated = midProg.copy(completed = true)
                _ui.value = _ui.value.copy(
                    progress = updated,
                    feedback = solvedBannerText(updated),
                    feedbackKind = ChessFeedbackKind.SOLVED,
                    isOpponentThinking = false,
                )
                repository.saveChessProgress(updated)
            }
        }
    }

    private fun solvedBannerText(prog: ChessProgress): String {
        val detail = if (prog.mistakes == 0 && prog.hintsUsed == 0) {
            "Perfect!"
        } else {
            val m = if (prog.mistakes == 1) "1 miss" else "${prog.mistakes} misses"
            val h = if (prog.hintsUsed == 1) "1 hint" else "${prog.hintsUsed} hints"
            "$m \u00B7 $h"
        }
        return "\u2605 Puzzle Solved! ($detail)"
    }
}

class ChessScreen(
    private val sealedActivity: SealedLightActivity,
) : LightScreen<Unit, ChessViewModel>(sealedActivity) {

    private val repository by lazy { PuzzleRepository.getInstance(lightContext) }

    override val viewModelClass: Class<ChessViewModel>
        get() = ChessViewModel::class.java

    override fun createViewModel(): ChessViewModel = ChessViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val ui by viewModel.ui.collectAsState()

        LightTheme(colors = themeColors) {
            val content = LightThemeTokens.colors.content
            val bg = LightThemeTokens.colors.background
            val isDarkMode = bg.red < 0.5f

            val lightSqColor = if (isDarkMode) Color(0xFF828282) else Color(0xFFE2E2E2)
            val darkSqColor = if (isDarkMode) Color(0xFF3E3E3E) else Color(0xFF949494)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .swipeToBack(sideOnly = true) { goBack() },
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
                        center = LightTopBarCenter.Text("Daily Chess"),
                        rightButton = null,
                        modifier = Modifier.padding(bottom = 0.05f.gridUnitsAsDp()),
                    )

                    val puzzle = ui.puzzle
                    val pos = ui.position
                    if (ui.isLoading || puzzle == null || pos == null) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("Loading Daily Chess\u2026", color = content.copy(alpha = 0.7f), fontSize = 15.sp)
                        }
                    } else {
                        val flipped = ui.playerColor == ChessColor.BLACK

                        // Starting Color Badge + Progress + Title (ALWAYS visible above board)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Unmistakable Play White / Play Black pill
                            val isPlayingWhite = ui.playerColor == ChessColor.WHITE
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (isPlayingWhite) Color.White else Color.Black,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .border(1.2.dp, content, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = if (isPlayingWhite) "\u265A\uFE0E White" else "\u265A\uFE0E Black",
                                    color = if (isPlayingWhite) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Text(
                                text = puzzle.title,
                                color = content,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            val rightStat = when {
                                ui.progress.completed -> "\u2605 Solved"
                                ui.progress.mistakes > 0 -> "Move ${ui.currentPlayerMoveNumber}/${ui.totalPlayerMoves} \u00B7 ${ui.progress.mistakes}x"
                                else -> "Move ${ui.currentPlayerMoveNumber}/${ui.totalPlayerMoves}"
                            }
                            Text(
                                text = rightStat,
                                color = content.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Responsive 8x8 Chessboard (dynamically fits remaining space on 360x413dp!)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val boardSize = minOf(maxWidth, maxHeight)
                            val pieceFontSp = (boardSize.value / 8f * 0.72f).coerceIn(18f, 28f).sp

                            Box(
                                modifier = Modifier
                                    .size(boardSize)
                                    .border(1.5.dp, content.copy(alpha = 0.9f)),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    for (displayRow in 0..7) {
                                        val boardRow = if (flipped) 7 - displayRow else displayRow
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        ) {
                                            for (displayCol in 0..7) {
                                                val boardCol = if (flipped) 7 - displayCol else displayCol
                                                val sq = Square(boardRow, boardCol)
                                                val isLightSq = (boardRow + boardCol) % 2 == 0
                                                val piece = pos.pieceAt(sq)
                                                val isSelected = sq == ui.selectedSquare
                                                val isTarget = sq in ui.legalTargets
                                                val isLastMove = sq == ui.lastMoveFrom || sq == ui.lastMoveTo
                                                val isHintFrom = sq == ui.hintFrom
                                                val isHintTo = sq == ui.hintTo
                                                val isWrongSq = sq == ui.lastWrongSquare

                                                val baseSqColor = if (isLightSq) lightSqColor else darkSqColor
                                                val sqBg = when {
                                                    isSelected -> if (isDarkMode) Color(0xFFB8B8B8) else Color(0xFF686868)
                                                    isHintFrom || isHintTo -> if (isDarkMode) Color(0xFFA8A8A8) else Color(0xFF787878)
                                                    isLastMove -> if (isDarkMode) Color(0xFF686868) else Color(0xFFB5B5B5)
                                                    else -> baseSqColor
                                                }

                                                val showRankLabel = displayCol == 0
                                                val showFileLabel = displayRow == 7
                                                val coordColor = if (isLightSq) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.72f)

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxSize()
                                                        .background(sqBg)
                                                        .then(
                                                            when {
                                                                isWrongSq -> Modifier.border(2.5.dp, content)
                                                                isSelected || isHintFrom || isHintTo -> Modifier.border(2.dp, content)
                                                                else -> Modifier
                                                            },
                                                        )
                                                        .lightClickable { viewModel.onSquareTap(sq) },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    // Subtle rank & file labels on outer edge squares
                                                    if (showRankLabel) {
                                                        Text(
                                                            text = "${8 - boardRow}",
                                                            color = coordColor,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            lineHeight = 7.sp,
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(start = 1.5.dp, top = 1.dp),
                                                        )
                                                    }
                                                    if (showFileLabel) {
                                                        Text(
                                                            text = "${'a' + boardCol}",
                                                            color = coordColor,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            lineHeight = 7.sp,
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(end = 1.5.dp, bottom = 0.5.dp),
                                                        )
                                                    }

                                                    // Piece rendered pure white (with dark halo) or pure black (with light halo)
                                                    if (piece != null) {
                                                        val isWhitePiece = piece.color == ChessColor.WHITE
                                                        Text(
                                                            text = piece.solidGlyph(),
                                                            style = TextStyle(
                                                                color = if (isWhitePiece) Color.White else Color.Black,
                                                                fontSize = pieceFontSp,
                                                                fontWeight = FontWeight.Normal,
                                                                shadow = Shadow(
                                                                    color = if (isWhitePiece) Color.Black else Color.White,
                                                                    offset = Offset(0f, 0f),
                                                                    blurRadius = 4f,
                                                                ),
                                                            ),
                                                        )
                                                    }

                                                    // Legal target indicator
                                                    if (isTarget) {
                                                        if (piece == null) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(9.dp)
                                                                    .background(
                                                                        if (isHintTo) content else Color.Black.copy(alpha = 0.65f),
                                                                        CircleShape,
                                                                    )
                                                                    .border(1.dp, Color.White, CircleShape),
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(2.dp)
                                                                    .border(2.dp, content, CircleShape),
                                                            )
                                                        }
                                                    }

                                                    // Explicit on-board X marker when a move misses
                                                    if (isWrongSq) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(1.dp)
                                                                .size(13.dp)
                                                                .background(Color.Black, CircleShape)
                                                                .border(1.dp, Color.White, CircleShape),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text(
                                                                text = "\u00D7",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                lineHeight = 9.sp,
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

                        // Always-visible High-Contrast Status / Miss / Hint / Solved Banner
                        val invertBanner = ui.feedbackKind == ChessFeedbackKind.WRONG ||
                            ui.feedbackKind == ChessFeedbackKind.SOLVED
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .background(
                                    if (invertBanner) content else bg,
                                    RoundedCornerShape(6.dp),
                                )
                                .border(
                                    width = if (ui.feedbackKind == ChessFeedbackKind.HINT) 1.5.dp else 1.dp,
                                    color = content.copy(alpha = if (invertBanner || ui.feedbackKind == ChessFeedbackKind.HINT) 1f else 0.35f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ui.feedback,
                                color = if (invertBanner) bg else content,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        // Bottom Action Row (ALWAYS visible — Promotion / Solved Share / Hint + Play Move + Reset)
                        if (ui.pendingPromotionFrom != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Promote:", color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                listOf(
                                    PieceType.QUEEN to "Q",
                                    PieceType.ROOK to "R",
                                    PieceType.BISHOP to "B",
                                    PieceType.KNIGHT to "N",
                                ).forEach { (pt, lbl) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.5.dp, content, RoundedCornerShape(6.dp))
                                            .lightClickable { viewModel.onChoosePromotion(pt) }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(lbl, color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, content.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .lightClickable { viewModel.cancelPromotion() }
                                        .padding(horizontal = 9.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("\u2715", color = content, fontSize = 13.sp)
                                }
                            }
                        } else if (ui.progress.completed) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(content, RoundedCornerShape(8.dp))
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                ShareCardScreen(act, ShareScope.CHESS)
                                            })
                                        }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Open Share Card",
                                        color = bg,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, content.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                        .lightClickable { viewModel.resetPuzzle() }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Replay", color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val hintLabel = when {
                                    ui.hintFrom != null && ui.hintTo == null -> "Hint: Dest"
                                    ui.hintTo != null -> "Hint Shown"
                                    else -> "Hint"
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .border(1.2.dp, content.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                        .lightClickable { viewModel.requestHint() }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(hintLabel, color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .border(1.dp, content.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .lightClickable { viewModel.playHintMove() }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Play Move", color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .border(1.dp, content.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .lightClickable { viewModel.resetPuzzle() }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Reset", color = content.copy(alpha = 0.85f), fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}
