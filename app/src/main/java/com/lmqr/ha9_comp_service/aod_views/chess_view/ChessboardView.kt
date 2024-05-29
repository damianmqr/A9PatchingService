package com.lmqr.ha9_comp_service.aod_views.chess_view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Parcelable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.lmqr.ha9_comp_service.aod_views.AODExtraView
import kotlinx.parcelize.Parcelize
import kotlin.math.min

class ChessboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), AODExtraView {

    private var chessboardState = ChessboardState()

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState, chessboardState, game)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            chessboardState = state.chessboardState
            game = state.chessGame
            invalidate()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    @Parcelize
    class SavedState(
        private val superState: Parcelable?,
        val chessboardState: ChessboardState,
        val chessGame: ChessGame?
    ) : BaseSavedState(superState), Parcelable


    init {
        chessboardState.resetBoard()
    }

    private var game: ChessGame? = null

    override fun performAction(context: Context) {
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
            updatePieces(chessboardState.pieceConfiguration)
        }
    }

    private val boardPaintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val boardPaintBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
    }
    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14F,
            context.resources.displayMetrics)
    }

    private val largeTextPaint = TextPaint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            18F,
            context.resources.displayMetrics)
    }
    private var cellSize = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f
    private val pieceImages = arrayOfNulls<Bitmap>(12)
    private val pieceConfiguration = Array(8) { IntArray(8) { -1 } }

    private fun preloadPieceImages() {
        val pieceNames = listOf(
            "white_pawn", "white_knight", "white_bishop", "white_rook", "white_queen", "white_king",
            "black_pawn", "black_knight", "black_bishop", "black_rook", "black_queen", "black_king"
        )
        pieceNames.forEachIndexed { index, pieceName ->
            val resourceId = resources.getIdentifier(pieceName, "drawable", context.packageName)
            if (resourceId != 0) {
                pieceImages[index] = drawableToBitmap(resourceId)
            }
        }
    }

    private fun drawableToBitmap(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        val bitmap =
            Bitmap.createBitmap(cellSize.toInt(), cellSize.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bitmap
    }

    private fun updatePieces(newConfiguration: Array<IntArray>) {
        if (newConfiguration.size == 8 && newConfiguration.all { it.size == 8 }) {
            newConfiguration.forEachIndexed { x, arr ->
                arr.forEachIndexed { y, piece -> pieceConfiguration[x][y] = piece }
            }
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val totalTextHeight = ALL_LINES_OFFSET * (textPaint.descent() - textPaint.ascent()) + (largeTextPaint.descent() - largeTextPaint.ascent())
        val heightWithoutText = (height - totalTextHeight).toInt()
        val boardWidth = min(width, heightWithoutText)
        setMeasuredDimension(boardWidth, (boardWidth + totalTextHeight).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val newCellSize = width / 8f
        boardOffsetY = (textPaint.descent() - textPaint.ascent()) * TEXT_LINE_OFFSET
        if (newCellSize != cellSize) {
            cellSize = newCellSize
            preloadPieceImages()
        }
        drawBoard(canvas)
        drawPieces(canvas)
        drawText(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                canvas.drawRect(
                     boardOffsetX + col * cellSize,
                    boardOffsetY + row * cellSize,
                    boardOffsetX + (col + 1) * cellSize,
                    boardOffsetY + (row + 1) * cellSize,
                    if ((row + col) % 2 == 0) boardPaintWhite else boardPaintBlack
                )
            }
        }
    }

    private fun drawPieces(canvas: Canvas) {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val pieceIndex = pieceConfiguration[row][col]
                if (pieceIndex in pieceImages.indices) {
                    pieceImages[pieceIndex]?.let { bitmap ->
                        canvas.drawBitmap(
                            bitmap,
                            boardOffsetX + col * cellSize,
                            boardOffsetY + row * cellSize,
                            null)
                    }
                }
            }
        }
    }

    private fun drawText(canvas: Canvas) {
        game?.run {
            canvas.drawText(currentBlackResult, 0f, (textPaint.descent() - textPaint.ascent()), textPaint)
            canvas.drawText(
                blackPlayer,
                canvas.width-textPaint.measureText(blackPlayer),
                (textPaint.descent() - textPaint.ascent()),
                textPaint)
            val whiteOffset = boardOffsetY + 8f * cellSize + (textPaint.descent() - textPaint.ascent()) * TEXT_LINE_OFFSET
            canvas.drawText(whitePlayer, 0f, whiteOffset, textPaint)
            canvas.drawText(
                currentWhiteResult,
                canvas.width-textPaint.measureText(whitePlayer),
                whiteOffset,
                textPaint)
            val venueOffset = whiteOffset + (largeTextPaint.descent() - largeTextPaint.ascent()) * TEXT_LINE_OFFSET
            canvas.drawText(event, (canvas.width-largeTextPaint.measureText(event))/2f, venueOffset, largeTextPaint)
        }
    }

    companion object {

        private const val TEXT_LINES = 3
        const val TEXT_LINE_OFFSET = 1.4f
        const val ALL_LINES_OFFSET = TEXT_LINES * TEXT_LINE_OFFSET + 0.25f

        const val EMPTY = -1
        const val WHITE_PAWN = 0
        const val WHITE_KNIGHT = 1
        const val WHITE_BISHOP = 2
        const val WHITE_ROOK = 3
        const val WHITE_QUEEN = 4
        const val WHITE_KING = 5
        const val BLACK_PAWN = 6
        const val BLACK_KNIGHT = 7
        const val BLACK_BISHOP = 8
        const val BLACK_ROOK = 9
        const val BLACK_QUEEN = 10
        const val BLACK_KING = 11

        val DEFAULT_CHESSBOARD = arrayOf(
            intArrayOf(
                BLACK_ROOK,
                BLACK_KNIGHT,
                BLACK_BISHOP,
                BLACK_QUEEN,
                BLACK_KING,
                BLACK_BISHOP,
                BLACK_KNIGHT,
                BLACK_ROOK
            ),
            intArrayOf(
                BLACK_PAWN,
                BLACK_PAWN,
                BLACK_PAWN,
                BLACK_PAWN,
                BLACK_PAWN,
                BLACK_PAWN,
                BLACK_PAWN,
                BLACK_PAWN
            ),
            intArrayOf(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY),
            intArrayOf(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY),
            intArrayOf(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY),
            intArrayOf(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY),
            intArrayOf(
                WHITE_PAWN,
                WHITE_PAWN,
                WHITE_PAWN,
                WHITE_PAWN,
                WHITE_PAWN,
                WHITE_PAWN,
                WHITE_PAWN,
                WHITE_PAWN
            ),
            intArrayOf(
                WHITE_ROOK,
                WHITE_KNIGHT,
                WHITE_BISHOP,
                WHITE_QUEEN,
                WHITE_KING,
                WHITE_BISHOP,
                WHITE_KNIGHT,
                WHITE_ROOK
            )
        )


    }
}
