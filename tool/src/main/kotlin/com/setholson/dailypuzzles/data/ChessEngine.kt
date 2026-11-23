package com.setholson.dailypuzzles.data

import kotlin.math.abs

enum class ChessColor {
    WHITE,
    BLACK;

    fun opposite(): ChessColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val sanChar: Char) {
    KING('K'),
    QUEEN('Q'),
    ROOK('R'),
    BISHOP('B'),
    KNIGHT('N'),
    PAWN('P'),
}

data class ChessPiece(
    val color: ChessColor,
    val type: PieceType,
) {
    /** Crisp Unicode chess glyph (outline for white, solid for black) with text-selector to prevent emoji fallback. */
    fun glyph(): String = when (color) {
        ChessColor.WHITE -> when (type) {
            PieceType.KING -> "\u2654\uFE0E"
            PieceType.QUEEN -> "\u2655\uFE0E"
            PieceType.ROOK -> "\u2656\uFE0E"
            PieceType.BISHOP -> "\u2657\uFE0E"
            PieceType.KNIGHT -> "\u2658\uFE0E"
            PieceType.PAWN -> "\u2659\uFE0E"
        }
        ChessColor.BLACK -> when (type) {
            PieceType.KING -> "\u265A\uFE0E"
            PieceType.QUEEN -> "\u265B\uFE0E"
            PieceType.ROOK -> "\u265C\uFE0E"
            PieceType.BISHOP -> "\u265D\uFE0E"
            PieceType.KNIGHT -> "\u265E\uFE0E"
            PieceType.PAWN -> "\u265F\uFE0E"
        }
    }

    /** Solid-silhouette chess glyph (always filled) so White can be drawn pure white and Black pure black. */
    fun solidGlyph(): String = when (type) {
        PieceType.KING -> "\u265A\uFE0E"
        PieceType.QUEEN -> "\u265B\uFE0E"
        PieceType.ROOK -> "\u265C\uFE0E"
        PieceType.BISHOP -> "\u265D\uFE0E"
        PieceType.KNIGHT -> "\u265E\uFE0E"
        PieceType.PAWN -> "\u265F\uFE0E"
    }
}

data class Square(val row: Int, val col: Int) {
    fun toAlgebraic(): String = "${'a' + col}${8 - row}"

    companion object {
        fun fromAlgebraic(s: String): Square? {
            if (s.length != 2) return null
            val col = s[0] - 'a'
            val row = 8 - (s[1] - '0')
            return if (row in 0..7 && col in 0..7) Square(row, col) else null
        }
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
)

data class ChessPosition(
    val board: List<List<ChessPiece?>>, // 8x8, row 0 = rank 8, row 7 = rank 1
    val turn: ChessColor,
    val castlingRights: String, // e.g. "KQkq" or "-"
    val enPassant: Square?,
) {
    fun pieceAt(sq: Square): ChessPiece? = board[sq.row][sq.col]
    fun pieceAt(row: Int, col: Int): ChessPiece? = board[row][col]
}

object ChessEngine {

    fun parseFen(fen: String): ChessPosition {
        val parts = fen.trim().split(Regex("\\s+"))
        val rows = parts[0].split("/")
        require(rows.size == 8) { "Invalid FEN board: $fen" }
        val board = List(8) { r ->
            val line = MutableList<ChessPiece?>(8) { null }
            var c = 0
            for (ch in rows[r]) {
                if (ch in '1'..'8') {
                    c += (ch - '0')
                } else {
                    val color = if (ch.isUpperCase()) ChessColor.WHITE else ChessColor.BLACK
                    val type = when (ch.uppercaseChar()) {
                        'K' -> PieceType.KING
                        'Q' -> PieceType.QUEEN
                        'R' -> PieceType.ROOK
                        'B' -> PieceType.BISHOP
                        'N' -> PieceType.KNIGHT
                        'P' -> PieceType.PAWN
                        else -> error("Unknown FEN piece '$ch'")
                    }
                    if (c in 0..7) line[c] = ChessPiece(color, type)
                    c++
                }
            }
            line.toList()
        }
        val turn = if (parts.getOrNull(1) == "b") ChessColor.BLACK else ChessColor.WHITE
        val castling = parts.getOrNull(2) ?: "-"
        val ep = parts.getOrNull(3)?.takeIf { it != "-" }?.let { Square.fromAlgebraic(it) }
        return ChessPosition(board = board, turn = turn, castlingRights = castling, enPassant = ep)
    }

    /**
     * Extracts the ordered main-line SAN moves from a standard PGN string.
     * Strips PGN headers `[Key "Value"]`, comments `{...}`, variations `(...)`,
     * move numbers `1.`, `1...`, NAGs `$1`, and game results `*`, `1-0`, `0-1`, `1/2-1/2`.
     */
    fun extractPgnMoves(pgn: String): List<String> {
        val noHeaders = pgn.lines()
            .filterNot { it.trim().startsWith("[") }
            .joinToString(" ")
        // Strip {...} comments
        var cleaned = noHeaders.replace(Regex("\\{[^}]*\\}"), " ")
        // Strip (...) variations iteratively (handles simple nesting)
        repeat(4) {
            cleaned = cleaned.replace(Regex("\\([^()]*\\)"), " ")
        }
        // Strip ; line comments
        cleaned = cleaned.replace(Regex(";[^\\n]*"), " ")
        val tokens = cleaned.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val moves = mutableListOf<String>()
        for (raw in tokens) {
            if (raw in setOf("*", "1-0", "0-1", "1/2-1/2")) continue
            if (raw.startsWith("$")) continue
            // Strip leading move numbers like "1." or "12..." or "1.f4"
            val stripped = raw.replace(Regex("^\\d+\\.+"), "")
            if (stripped.isBlank()) continue
            moves.add(stripped)
        }
        return moves
    }

    fun legalMoves(pos: ChessPosition): List<ChessMove> {
        val pseudo = pseudoLegalMoves(pos)
        return pseudo.filter { move ->
            val next = applyMove(pos, move)
            !isKingInCheck(next, pos.turn)
        }
    }

    fun legalMovesFrom(pos: ChessPosition, from: Square): List<ChessMove> {
        return legalMoves(pos).filter { it.from == from }
    }

    fun isKingInCheck(pos: ChessPosition, color: ChessColor): Boolean {
        val kingSq = findKing(pos, color) ?: return false
        return isSquareAttacked(pos, kingSq, color.opposite())
    }

    fun isCheckmate(pos: ChessPosition): Boolean {
        return isKingInCheck(pos, pos.turn) && legalMoves(pos).isEmpty()
    }

    fun applyMove(pos: ChessPosition, move: ChessMove): ChessPosition {
        val mutable = pos.board.map { it.toMutableList() }
        val moving = mutable[move.from.row][move.from.col]
            ?: error("No piece at ${move.from.toAlgebraic()}")

        mutable[move.from.row][move.from.col] = null

        if (move.isEnPassant) {
            val capturedRow = move.from.row
            mutable[capturedRow][move.to.col] = null
        }

        val placedPiece = if (move.promotion != null) {
            ChessPiece(moving.color, move.promotion)
        } else {
            moving
        }
        mutable[move.to.row][move.to.col] = placedPiece

        if (move.isCastleKingSide) {
            val r = move.from.row
            val rook = mutable[r][7]
            mutable[r][7] = null
            mutable[r][5] = rook
        } else if (move.isCastleQueenSide) {
            val r = move.from.row
            val rook = mutable[r][0]
            mutable[r][0] = null
            mutable[r][3] = rook
        }

        // Update castling rights
        var rights = pos.castlingRights
        if (rights != "-") {
            if (moving.type == PieceType.KING) {
                rights = if (moving.color == ChessColor.WHITE) {
                    rights.replace("K", "").replace("Q", "")
                } else {
                    rights.replace("k", "").replace("q", "")
                }
            }
            if (move.from == Square(7, 7) || move.to == Square(7, 7)) rights = rights.replace("K", "")
            if (move.from == Square(7, 0) || move.to == Square(7, 0)) rights = rights.replace("Q", "")
            if (move.from == Square(0, 7) || move.to == Square(0, 7)) rights = rights.replace("k", "")
            if (move.from == Square(0, 0) || move.to == Square(0, 0)) rights = rights.replace("q", "")
            if (rights.isEmpty()) rights = "-"
        }

        // Update en passant target
        val newEp = if (moving.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            Square((move.from.row + move.to.row) / 2, move.from.col)
        } else {
            null
        }

        return ChessPosition(
            board = mutable.map { it.toList() },
            turn = pos.turn.opposite(),
            castlingRights = rights,
            enPassant = newEp,
        )
    }

    /**
     * Resolves a Standard Algebraic Notation string (e.g. `Rxh6+`, `a1=Q`, `O-O`, `Nbd2`, `e4`)
     * into the exact legal [ChessMove] in [pos].
     */
    fun parseSanMove(pos: ChessPosition, rawSan: String): ChessMove? {
        var san = rawSan.trim()
            .replace("+", "")
            .replace("#", "")
            .replace("!", "")
            .replace("?", "")
        val legal = legalMoves(pos)

        if (san == "O-O" || san == "0-0") {
            return legal.firstOrNull { it.isCastleKingSide }
        }
        if (san == "O-O-O" || san == "0-0-0") {
            return legal.firstOrNull { it.isCastleQueenSide }
        }

        var promotion: PieceType? = null
        val promoIdx = san.indexOf('=')
        if (promoIdx >= 0 && promoIdx + 1 < san.length) {
            promotion = charToPieceType(san[promoIdx + 1])
            san = san.substring(0, promoIdx)
        }

        val pieceType: PieceType
        var rest: String
        if (san.isNotEmpty() && san[0] in listOf('K', 'Q', 'R', 'B', 'N')) {
            pieceType = charToPieceType(san[0]) ?: return null
            rest = san.substring(1)
        } else {
            pieceType = PieceType.PAWN
            rest = san
        }

        rest = rest.replace("x", "")
        if (rest.length < 2) return null

        val destStr = rest.takeLast(2)
        val destSq = Square.fromAlgebraic(destStr) ?: return null
        val disambig = rest.dropLast(2)

        var filterFile: Int? = null
        var filterRank: Int? = null
        for (ch in disambig) {
            if (ch in 'a'..'h') filterFile = ch - 'a'
            if (ch in '1'..'8') filterRank = 8 - (ch - '0')
        }

        return legal.firstOrNull { move ->
            val p = pos.pieceAt(move.from) ?: return@firstOrNull false
            p.type == pieceType &&
                move.to == destSq &&
                move.promotion == promotion &&
                (filterFile == null || move.from.col == filterFile) &&
                (filterRank == null || move.from.row == filterRank)
        }
    }

    /** Replays [count] SAN moves from [fen] and returns the resulting position. */
    fun positionAfterMoves(fen: String, sanMoves: List<String>, count: Int): ChessPosition {
        var pos = parseFen(fen)
        for (i in 0 until count.coerceAtMost(sanMoves.size)) {
            val move = parseSanMove(pos, sanMoves[i]) ?: break
            pos = applyMove(pos, move)
        }
        return pos
    }

    private fun charToPieceType(ch: Char): PieceType? = when (ch.uppercaseChar()) {
        'K' -> PieceType.KING
        'Q' -> PieceType.QUEEN
        'R' -> PieceType.ROOK
        'B' -> PieceType.BISHOP
        'N' -> PieceType.KNIGHT
        'P' -> PieceType.PAWN
        else -> null
    }

    private fun findKing(pos: ChessPosition, color: ChessColor): Square? {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = pos.board[r][c]
                if (p != null && p.color == color && p.type == PieceType.KING) {
                    return Square(r, c)
                }
            }
        }
        return null
    }

    private fun isSquareAttacked(pos: ChessPosition, target: Square, byColor: ChessColor): Boolean {
        // Pawn attacks
        val pawnSourceRow = if (byColor == ChessColor.WHITE) target.row + 1 else target.row - 1
        if (pawnSourceRow in 0..7) {
            for (dc in listOf(-1, 1)) {
                val c = target.col + dc
                if (c in 0..7) {
                    val p = pos.board[pawnSourceRow][c]
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // Knight attacks
        val knightOffsets = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1,
        )
        for ((dr, dc) in knightOffsets) {
            val r = target.row + dr
            val c = target.col + dc
            if (r in 0..7 && c in 0..7) {
                val p = pos.board[r][c]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        // King attacks
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = target.row + dr
                val c = target.col + dc
                if (r in 0..7 && c in 0..7) {
                    val p = pos.board[r][c]
                    if (p != null && p.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }

        // Sliding rook/queen
        val ortho = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in ortho) {
            var r = target.row + dr
            var c = target.col + dc
            while (r in 0..7 && c in 0..7) {
                val p = pos.board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += dr
                c += dc
            }
        }

        // Sliding bishop/queen
        val diag = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((dr, dc) in diag) {
            var r = target.row + dr
            var c = target.col + dc
            while (r in 0..7 && c in 0..7) {
                val p = pos.board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += dr
                c += dc
            }
        }

        return false
    }

    private fun pseudoLegalMoves(pos: ChessPosition): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val color = pos.turn
        for (r in 0..7) {
            for (c in 0..7) {
                val p = pos.board[r][c] ?: continue
                if (p.color != color) continue
                val from = Square(r, c)
                when (p.type) {
                    PieceType.PAWN -> addPawnMoves(pos, from, color, moves)
                    PieceType.KNIGHT -> addKnightMoves(pos, from, color, moves)
                    PieceType.BISHOP -> addSlidingMoves(pos, from, color, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1), moves)
                    PieceType.ROOK -> addSlidingMoves(pos, from, color, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1), moves)
                    PieceType.QUEEN -> addSlidingMoves(
                        pos,
                        from,
                        color,
                        listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1),
                        moves,
                    )
                    PieceType.KING -> addKingMoves(pos, from, color, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(pos: ChessPosition, from: Square, color: ChessColor, out: MutableList<ChessMove>) {
        val dir = if (color == ChessColor.WHITE) -1 else 1
        val startRow = if (color == ChessColor.WHITE) 6 else 1
        val promoRow = if (color == ChessColor.WHITE) 0 else 7
        val promos = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        val r1 = from.row + dir
        if (r1 in 0..7 && pos.board[r1][from.col] == null) {
            val to = Square(r1, from.col)
            if (r1 == promoRow) {
                promos.forEach { out.add(ChessMove(from, to, promotion = it)) }
            } else {
                out.add(ChessMove(from, to))
                val r2 = from.row + 2 * dir
                if (from.row == startRow && pos.board[r2][from.col] == null) {
                    out.add(ChessMove(from, Square(r2, from.col)))
                }
            }
        }

        // Captures & en passant
        for (dc in listOf(-1, 1)) {
            val c1 = from.col + dc
            if (r1 in 0..7 && c1 in 0..7) {
                val target = pos.board[r1][c1]
                val to = Square(r1, c1)
                if (target != null && target.color != color) {
                    if (r1 == promoRow) {
                        promos.forEach { out.add(ChessMove(from, to, promotion = it)) }
                    } else {
                        out.add(ChessMove(from, to))
                    }
                } else if (pos.enPassant == to) {
                    out.add(ChessMove(from, to, isEnPassant = true))
                }
            }
        }
    }

    private fun addKnightMoves(pos: ChessPosition, from: Square, color: ChessColor, out: MutableList<ChessMove>) {
        val offsets = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1,
        )
        for ((dr, dc) in offsets) {
            val r = from.row + dr
            val c = from.col + dc
            if (r in 0..7 && c in 0..7) {
                val target = pos.board[r][c]
                if (target == null || target.color != color) {
                    out.add(ChessMove(from, Square(r, c)))
                }
            }
        }
    }

    private fun addSlidingMoves(
        pos: ChessPosition,
        from: Square,
        color: ChessColor,
        dirs: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>,
    ) {
        for ((dr, dc) in dirs) {
            var r = from.row + dr
            var c = from.col + dc
            while (r in 0..7 && c in 0..7) {
                val target = pos.board[r][c]
                if (target == null) {
                    out.add(ChessMove(from, Square(r, c)))
                } else {
                    if (target.color != color) out.add(ChessMove(from, Square(r, c)))
                    break
                }
                r += dr
                c += dc
            }
        }
    }

    private fun addKingMoves(pos: ChessPosition, from: Square, color: ChessColor, out: MutableList<ChessMove>) {
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = from.row + dr
                val c = from.col + dc
                if (r in 0..7 && c in 0..7) {
                    val target = pos.board[r][c]
                    if (target == null || target.color != color) {
                        out.add(ChessMove(from, Square(r, c)))
                    }
                }
            }
        }

        // Castling
        val enemy = color.opposite()
        val homeRow = if (color == ChessColor.WHITE) 7 else 0
        if (from == Square(homeRow, 4) && !isSquareAttacked(pos, from, enemy)) {
            val ksChar = if (color == ChessColor.WHITE) 'K' else 'k'
            val qsChar = if (color == ChessColor.WHITE) 'Q' else 'q'
            if (ksChar in pos.castlingRights &&
                pos.board[homeRow][5] == null &&
                pos.board[homeRow][6] == null &&
                !isSquareAttacked(pos, Square(homeRow, 5), enemy) &&
                !isSquareAttacked(pos, Square(homeRow, 6), enemy)
            ) {
                out.add(ChessMove(from, Square(homeRow, 6), isCastleKingSide = true))
            }
            if (qsChar in pos.castlingRights &&
                pos.board[homeRow][3] == null &&
                pos.board[homeRow][2] == null &&
                pos.board[homeRow][1] == null &&
                !isSquareAttacked(pos, Square(homeRow, 3), enemy) &&
                !isSquareAttacked(pos, Square(homeRow, 2), enemy)
            ) {
                out.add(ChessMove(from, Square(homeRow, 2), isCastleQueenSide = true))
            }
        }
    }
}
