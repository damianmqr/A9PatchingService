package com.lmqr.ha9_comp_service.aod_views.chess_view

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ChessboardState(
    private var whiteTurn: Boolean = true,
    val pieceConfiguration: Array<IntArray> = Array(8) { IntArray(8) { -1 } }
): Parcelable {
    fun resetBoard() {
        ChessboardView.DEFAULT_CHESSBOARD.forEachIndexed { x, arr ->
            arr.forEachIndexed { y, piece -> pieceConfiguration[x][y] = piece }
        }
        whiteTurn = true
    }

    private fun justMove(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        pieceConfiguration[toX][toY] = pieceConfiguration[fromX][fromY]
        pieceConfiguration[fromX][fromY] = -1
    }

    fun performPGMMove(move: PGNMove) {
        move.run {
            if (specialState == 1) {
                val x = if (whiteTurn) 7 else 0
                justMove(x, 4, x, 6)
                justMove(x, 7, x, 5)
                return@run
            }
            if (specialState == 2) {
                val x = if (whiteTurn) 7 else 0
                justMove(x, 4, x, 2)
                justMove(x, 0, x, 3)
                return@run
            }

            val piece = if (whiteTurn) move.figure else move.figure + 6
            if (fromX >= 0 && fromY >= 0) {
                //en passant
                if (captures && (piece == ChessboardView.BLACK_PAWN || piece == ChessboardView.WHITE_PAWN)) {
                    val direction = if (piece == ChessboardView.BLACK_PAWN) -1 else 1
                    val oppositePawn =
                        if (piece == ChessboardView.BLACK_PAWN)
                            ChessboardView.WHITE_PAWN
                        else
                            ChessboardView.BLACK_PAWN

                    if (pieceConfiguration.getPiece(toX, toY) == ChessboardView.EMPTY
                        && pieceConfiguration.getPiece(toX + direction, toY) == oppositePawn
                    )
                        pieceConfiguration[toX + direction][toY] = ChessboardView.EMPTY
                }
                justMove(fromX, fromY, toX, toY)
                return@run
            }
            when (piece) {
                ChessboardView.BLACK_PAWN, ChessboardView.WHITE_PAWN -> {
                    val direction = if (piece == ChessboardView.BLACK_PAWN) -1 else 1
                    val oppositePawn =
                        if (piece == ChessboardView.BLACK_PAWN)
                            ChessboardView.WHITE_PAWN
                        else
                            ChessboardView.BLACK_PAWN

                    if (!captures) {
                        if (pieceConfiguration.getPiece(toX + direction, toY) == piece)
                            justMove(toX + direction, toY, toX, toY)
                        else if (pieceConfiguration.getPiece(toX + 2 * direction, toY) == piece)
                            justMove(toX + 2 * direction, toY, toX, toY)
                    } else {
                        //en passant
                        if (pieceConfiguration.getPiece(toX, toY) == ChessboardView.EMPTY
                            && pieceConfiguration.getPiece(toX + direction, toY) == oppositePawn
                        )
                            pieceConfiguration[toX + direction][toY] = ChessboardView.EMPTY

                        if (fromY >= 0) {
                            justMove(toX + direction, fromY, toX, toY)
                        } else if (pieceConfiguration.getPiece(toX + direction, toY - 1) == piece) {
                            justMove(toX + direction, toY - 1, toX, toY)
                        } else {
                            justMove(toX + direction, toY + 1, toX, toY)
                        }
                    }
                }

                ChessboardView.WHITE_KING, ChessboardView.BLACK_KING -> {
                    for (offX in -1..1) {
                        for (offY in -1..1) {
                            if (offX == offY && offX == 0)
                                continue
                            if (pieceConfiguration.getPiece(toX + offX, toY + offY) == piece) {
                                justMove(toX + offX, toY + offY, toX, toY)
                                return@run
                            }
                        }
                    }
                }

                ChessboardView.WHITE_KNIGHT, ChessboardView.BLACK_KNIGHT -> {
                    for ((offX, offY) in knightMoves) {
                        if (fromX >= 0 && toX + offX != fromX)
                            continue
                        if (fromY >= 0 && toY + offY != fromY)
                            continue

                        if (pieceConfiguration.getPiece(toX + offX, toY + offY) == piece) {
                            justMove(toX + offX, toY + offY, toX, toY)
                            return@run
                        }
                    }
                }

                ChessboardView.WHITE_ROOK, ChessboardView.BLACK_ROOK -> {
                    for ((offX, offY) in rookDirections) {
                        var currX = toX + offX
                        var currY = toY + offY
                        while (pieceConfiguration.isValidMove(currX, currY)) {
                            currX += offX
                            currY += offY
                        }
                        if (fromX >= 0 && currX != fromX)
                            continue
                        if (fromY >= 0 && currY != fromY)
                            continue

                        if (pieceConfiguration.getPiece(currX, currY) == piece) {
                            justMove(currX, currY, toX, toY)
                            return@run
                        }
                    }
                }

                ChessboardView.BLACK_BISHOP, ChessboardView.WHITE_BISHOP -> {
                    for ((offX, offY) in bishopDirections) {
                        var currX = toX + offX
                        var currY = toY + offY
                        while (pieceConfiguration.isValidMove(currX, currY)) {
                            currX += offX
                            currY += offY
                        }
                        if (fromX >= 0 && currX != fromX)
                            continue
                        if (fromY >= 0 && currY != fromY)
                            continue

                        if (pieceConfiguration.getPiece(currX, currY) == piece) {
                            justMove(currX, currY, toX, toY)
                            return@run
                        }
                    }
                }

                ChessboardView.BLACK_QUEEN, ChessboardView.WHITE_QUEEN -> {
                    for ((offX, offY) in queenDirections) {
                        var currX = toX + offX
                        var currY = toY + offY
                        while (pieceConfiguration.isValidMove(currX, currY)) {
                            currX += offX
                            currY += offY
                        }
                        if (fromX >= 0 && currX != fromX)
                            continue
                        if (fromY >= 0 && currY != fromY)
                            continue

                        if (pieceConfiguration.getPiece(currX, currY) == piece) {
                            justMove(currX, currY, toX, toY)
                            return@run
                        }
                    }
                }

                else -> {}
            }
        }
        if (move.specialState in 3..6)
            pieceConfiguration[move.toX][move.toY] =
                if (whiteTurn) move.specialState - 2 else move.specialState + 4

        whiteTurn = !whiteTurn
    }
}

private fun Array<IntArray>.getPiece(x: Int, y: Int): Int {
    if (!isInRange(x, y))
        return -2
    return get(x)[y]
}

private fun Array<IntArray>.isValidMove(x: Int, y: Int) = getPiece(x, y) == -1
private fun isInRange(x: Int, y: Int) = x in 0..7 && y in 0..7

private val knightMoves = listOf(
    Pair(-1, -2), Pair(1, -2),
    Pair(-1, 2), Pair(1, 2),
    Pair(-2, -1), Pair(2, -1),
    Pair(-2, 1), Pair(2, 1)
)

private val rookDirections = listOf(
    Pair(-1, 0), Pair(1, 0),
    Pair(0, -1), Pair(0, 1),
)

private val bishopDirections = listOf(
    Pair(-1, -1), Pair(1, -1),
    Pair(1, 1), Pair(-1, 1),
)

private val queenDirections = rookDirections + bishopDirections