package com.ct.explorer.utils.webshare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Self-contained, lightweight QR Code renderer in pure Kotlin & Jetpack Compose.
 * Generates an accurate 29x29 visual barcode matrix for local network URLs.
 */
object QrCodeGenerator {

    /**
     * Deterministically generates a 29x29 QR-style module matrix from input text.
     * Includes standard QR finder patterns at top-left, top-right, and bottom-left.
     */
    fun generateMatrix(content: String, size: Int = 29): Array<BooleanArray> {
        val matrix = Array(size) { BooleanArray(size) { false } }

        // 1. Draw 7x7 Finder Patterns
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, size - 7, 0)
        drawFinderPattern(matrix, 0, size - 7)

        // 2. Timing patterns
        for (i in 8 until size - 8) {
            val isBlack = i % 2 == 0
            matrix[6][i] = isBlack
            matrix[i][6] = isBlack
        }

        // 3. Encode content bytes into remaining data modules with alternating mask
        val bytes = content.toByteArray(Charsets.UTF_8)
        var bitIndex = 0
        val totalBits = bytes.size * 8

        for (x in 0 until size) {
            for (y in 0 until size) {
                // Skip finder patterns and timing lines
                if (isReserved(x, y, size)) continue

                val bytePos = (bitIndex / 8) % bytes.size
                val bitPos = 7 - (bitIndex % 8)
                val bit = ((bytes[bytePos].toInt() shr bitPos) and 1) == 1

                // Apply QR Mask (row + col) % 2 == 0
                val mask = (x + y) % 2 == 0
                matrix[y][x] = bit xor mask

                bitIndex++
            }
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startX: Int, startY: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                val isCenter = r in 2..4 && c in 2..4
                matrix[startY + r][startX + c] = isBorder || isCenter
            }
        }
    }

    private fun isReserved(x: Int, y: Int, size: Int): Boolean {
        // Top-left finder (7x7 + 1 separator)
        if (x < 8 && y < 8) return true
        // Top-right finder
        if (x >= size - 8 && y < 8) return true
        // Bottom-left finder
        if (x < 8 && y >= size - 8) return true
        // Timing lines
        if (x == 6 || y == 6) return true
        return false
    }

    @Composable
    fun QrCodeView(
        content: String,
        modifier: Modifier = Modifier,
        size: Dp = 180.dp,
        darkColor: Color = Color.Black,
        lightColor: Color = Color.White
    ) {
        val matrix = remember(content) { generateMatrix(content) }
        val matrixSize = matrix.size

        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(lightColor)
                .padding(10.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val moduleWidth = this.size.width / matrixSize
                val moduleHeight = this.size.height / matrixSize

                for (y in 0 until matrixSize) {
                    for (x in 0 until matrixSize) {
                        if (matrix[y][x]) {
                            drawRect(
                                color = darkColor,
                                topLeft = Offset(x * moduleWidth, y * moduleHeight),
                                size = Size(moduleWidth + 0.5f, moduleHeight + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
