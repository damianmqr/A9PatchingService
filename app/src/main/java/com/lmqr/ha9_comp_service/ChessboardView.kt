package com.lmqr.ha9_comp_service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class ChessboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
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

    fun updatePieces(newConfiguration: Array<IntArray>) {
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
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        if (width / 8f != cellSize) {
            cellSize = width / 8f
            preloadPieceImages()
        }
        drawBoard(canvas)
        drawPieces(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                boardPaint.color = if ((row + col) % 2 == 0) Color.WHITE else Color.LTGRAY
                canvas.drawRect(
                    col * cellSize,
                    row * cellSize,
                    (col + 1) * cellSize,
                    (row + 1) * cellSize,
                    boardPaint
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
                        canvas.drawBitmap(bitmap, col * cellSize, row * cellSize, null)
                    }
                }
            }
        }
    }

    companion object {
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
