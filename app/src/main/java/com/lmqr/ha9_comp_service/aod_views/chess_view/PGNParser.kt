package com.lmqr.ha9_comp_service.aod_views.chess_view

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.random.Random


@Parcelize
data class PGNMove(
    val figure: Int,
    val captures: Boolean,
    val toX: Int,
    val toY: Int,
    val fromX: Int,
    val fromY: Int,
    val specialState: Int = 0
): Parcelable

@Parcelize
data class ChessGame(
    val whitePlayer: String,
    val blackPlayer: String,
    val site: String,
    val date: String,
    val opening: String,
    val event: String,
    val moves: List<PGNMove>,
    var currentMove: Int = 0,
    var currentBlackResult: String = "",
    var currentWhiteResult: String = "",
) : Parcelable

const val PAWN = 0
const val KNIGHT = 1
const val BISHOP = 2
const val ROOK = 3
const val QUEEN = 4
const val KING = 5


fun String.getValue(tag: String): String? {
    val pattern = "\\[$tag\\s*\"(.*?)\"]".toRegex()
    return pattern.find(this)?.groups?.get(1)?.value
}

fun String.extractChessMoves(): List<String> =
    replace(Regex("\\[.*?\\]\\s*"), "")
        .replace(Regex("\\{[^}]*\\}"), "")
        .replace(Regex("\\d+\\.{1,3}"), "")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .fold(mutableListOf()) { acc, move ->
            when (move) {
                "1-0" -> if (acc.isNotEmpty()) acc[acc.size - 1] += "W"
                "0-1" -> if (acc.isNotEmpty()) acc[acc.size - 1] += "L"
                "1/2-1/2" -> if (acc.isNotEmpty()) acc[acc.size - 1] += "D"
                else -> acc.add(move)
            }
            acc
        }

fun String.toDataMove() = PGNMove(
    figure = when (get(0)) {
        'K' -> KING
        'Q' -> QUEEN
        'R' -> ROOK
        'B' -> BISHOP
        'N' -> KNIGHT
        else -> PAWN
    },
    captures = 'x' in this,
    toX = 7 - ((lastOrNull { it in '1'..'8' } ?: '1') - '1'),
    toY = (lastOrNull { it in 'a'..'h' } ?: 'a') - 'a',
    fromX = if (count { it in '1'..'8' } > 1) 7 - (first { it in '1'..'8' } - '1') else -1,
    fromY = if (count { it in 'a'..'h' } > 1) first { it in 'a'..'h' } - 'a' else -1,
    specialState = when {
        equals("O-O") -> 1
        equals("O-O-O") -> 2
        endsWith("W") -> 7
        endsWith("L") -> 8
        endsWith("D") -> 9
        contains("=Q") -> 6
        contains("=R") -> 5
        contains("=B") -> 4
        contains("=N") -> 3
        else -> 0
    }
)

private fun Context.createCacheFile(filename: String): File {
    val cacheFile = File(cacheDir, filename)
    if (cacheFile.exists()) {
        return cacheFile
    }
    val inputStream = assets.open(filename)
    val fileOutputStream = FileOutputStream(cacheFile)
    val bufferSize = 1024
    val buffer = ByteArray(bufferSize)
    var length = -1
    while (inputStream.read(buffer).also { length = it } > 0) {
        fileOutputStream.write(buffer, 0, length)
    }
    fileOutputStream.close()
    inputStream.close()
    return cacheFile
}

fun Context.getRandomGameFromAsset(assetFileName: String): ChessGame? {
    val cacheFile = createCacheFile(assetFileName)
    RandomAccessFile(cacheFile, "r").use { raf ->
        val fileLength = cacheFile.length()
        val randomPosition = Random.nextLong(fileLength)
        raf.seek(randomPosition)

        repeat(2) {
            var rafChar: Int = -1
            while (raf.filePointer > 0 && raf.read().apply { rafChar = it }
                    .let { it != -1 && it.toChar() != '\n' });
            if (rafChar == -1)
                raf.seek(0)

            if (raf.filePointer == 0L) raf.readLine()

            var line = raf.readLine()
            val tagPattern = "\\[([a-zA-Z]+)\\s*\"(.*?)\"]".toRegex()
            while (line != null && line.trim().isNotEmpty())
                line = raf.readLine()

            while (line != null) {
                if (tagPattern.containsMatchIn(line)) {
                    val metadataBuilder = StringBuilder(line).append("\n")
                    while (raf.readLine().also { line = it } != null && line.trim().isNotEmpty()) {
                        metadataBuilder.append(line).append("\n")
                    }
                    while (line != null && line.trim().isEmpty()) {
                        line = raf.readLine()
                    }

                    val moveDataBuilder = StringBuilder(line).append("\n")
                    while (raf.readLine().also { line = it } != null && line.trim().isNotEmpty()) {
                        moveDataBuilder.append(line).append("\n")
                    }

                    val metadata = metadataBuilder.toString()
                    val game = ChessGame(
                        whitePlayer = metadata.getValue("White") ?: "",
                        blackPlayer = metadata.getValue("Black") ?: "",
                        site = metadata.getValue("Site") ?: "",
                        date = metadata.getValue("Date") ?: "",
                        opening = metadata.getValue("Opening") ?: "",
                        event = metadata.getValue("Event") ?: "",
                        moves = moveDataBuilder.toString().extractChessMoves()
                            .map(String::toDataMove)
                    )
                    if(!(game.moves.size <= 2 || game.whitePlayer.isEmpty() || game.blackPlayer.isEmpty()))
                        return game
                }
                line = raf.readLine()
            }
            raf.seek(0)
        }
    }
    return null
}

fun Context.getRandomGame(): ChessGame {
    return getRandomGameFromAsset("chessgames.txt")
        ?: getRandomGameFromAsset("chessgames.txt")
        ?: getRandomGameFromAsset("chessgames.txt")
        ?: ChessGame(
            whitePlayer = "White",
            blackPlayer = "Black",
            site = "",
            date = "",
            opening = "",
            event = "",
            moves = listOf(
                PGNMove(
                    0, false, 0, 0, -1, -1, 9
                )
            )
        )
}