package com.lmqr.ha9_comp_service

import android.content.Context

class GameManager {

    private val chessboardState = ChessboardState()

    init {
        chessboardState.resetBoard()
    }

    var game: ChessGame? = null

    fun move(context: Context) {
        if (game == null) {
            game = context.getRandomGame()
        }
        game?.run {
            if (currentMove >= moves.size) {
                chessboardState.resetBoard()
                game = context.getRandomGame().apply {
                    currentMove = 0
                }
            }
        }
        game?.run {
            when (moves[currentMove].specialState) {
                7 -> {
                    currentWhiteResult = "WINNER"
                    currentBlackResult = ""
                }

                8 -> {
                    currentWhiteResult = ""
                    currentBlackResult = "WINNER"
                }

                9 -> {
                    currentWhiteResult = "DRAW"
                    currentBlackResult = "DRAW"
                }

                else -> {
                    currentWhiteResult = ""
                    currentBlackResult = ""
                }
            }
            chessboardState.performPGMMove(moves[currentMove])
            currentMove += 1
        }
    }

    fun getPieces() = chessboardState.pieceConfiguration
}