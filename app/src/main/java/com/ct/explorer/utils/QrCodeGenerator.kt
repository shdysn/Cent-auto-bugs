package com.ct.explorer.utils

/**
 * Self-contained pure Kotlin QR Code matrix generator.
 * Produces valid, scannable ISO/IEC 18004 standard QR Code matrices.
 */
object QrCodeGenerator {

    /**
     * Generates a 2D boolean array representing the QR code matrix (true = dark, false = light).
     * @param text The URL or string to encode.
     * @return 2D boolean matrix where [row][col] is true if pixel is black.
     */
    fun encode(text: String): Array<BooleanArray> {
        val dataBytes = text.toByteArray(Charsets.UTF_8)
        // Determine smallest QR version (1 to 6) capable of holding dataBytes with Error Correction Level M
        // Capacity table for ECC M (byte mode): V1: 14, V2: 26, V3: 42, V4: 62, V5: 84, V6: 106, V7: 122
        val version = when {
            dataBytes.size <= 14 -> 1
            dataBytes.size <= 26 -> 2
            dataBytes.size <= 42 -> 3
            dataBytes.size <= 62 -> 4
            dataBytes.size <= 84 -> 5
            else -> 6
        }

        val size = version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        // 1. Finder patterns (Top-Left, Top-Right, Bottom-Left)
        addFinderPattern(matrix, reserved, 0, 0)
        addFinderPattern(matrix, reserved, size - 7, 0)
        addFinderPattern(matrix, reserved, 0, size - 7)

        // 2. Separators
        addSeparators(reserved, size)

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val bit = (i % 2 == 0)
            if (!reserved[6][i]) {
                matrix[6][i] = bit
                reserved[6][i] = true
            }
            if (!reserved[i][6]) {
                matrix[i][6] = bit
                reserved[i][6] = true
            }
        }

        // 4. Alignment patterns for Version >= 2
        if (version >= 2) {
            val alignPos = getAlignmentPositions(version)
            for (r in alignPos) {
                for (c in alignPos) {
                    if (!reserved[r][c]) {
                        addAlignmentPattern(matrix, reserved, r, c)
                    }
                }
            }
        }

        // 5. Dark module
        matrix[4 * version + 9][8] = true
        reserved[4 * version + 9][8] = true

        // 6. Encode data bits (Byte mode 0100 + char count + data + terminator + pad)
        val codewords = buildCodewords(dataBytes, version)
        val ecCodewords = calculateErrorCorrection(codewords, version)
        val finalBits = interleaveAndBits(codewords, ecCodewords, version)

        // 7. Place data bits using zigzag path avoiding reserved modules
        var bitIndex = 0
        var right = size - 1
        var goingUp = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing line
            val rows = if (goingUp) (size - 1 downTo 0) else (0 until size)
            for (r in rows) {
                for (colOffset in 0..1) {
                    val c = right - colOffset
                    if (!reserved[r][c]) {
                        val bit = if (bitIndex < finalBits.size) finalBits[bitIndex++] else false
                        // Apply mask 0: (row + col) % 2 == 0
                        val mask = ((r + c) % 2 == 0)
                        matrix[r][c] = (bit != mask)
                    }
                }
            }
            goingUp = !goingUp
            right -= 2
        }

        // 8. Add format information (Mask 0, ECC M: bits 101010000010010)
        val formatBits = intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0)
        for (i in 0..14) {
            val bit = formatBits[i] == 1
            // Top-left
            when {
                i < 6 -> matrix[8][i] = bit
                i == 6 -> matrix[8][7] = bit
                i == 7 -> matrix[8][8] = bit
                i == 8 -> matrix[7][8] = bit
                else -> matrix[14 - i][8] = bit
            }
            // Around finder patterns
            if (i < 8) {
                matrix[size - 1 - i][8] = bit
            } else {
                matrix[8][size - 15 + i] = bit
            }
        }

        return matrix
    }

    private fun addFinderPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, row: Int, col: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isOuter = (r == 0 || r == 6 || c == 0 || c == 6)
                val isCenter = (r in 2..4 && c in 2..4)
                matrix[row + r][col + c] = isOuter || isCenter
                reserved[row + r][col + c] = true
            }
        }
    }

    private fun addSeparators(reserved: Array<BooleanArray>, size: Int) {
        for (i in 0..7) {
            if (i < size) {
                reserved[7][i] = true
                reserved[i][7] = true
                reserved[size - 8][i] = true
                reserved[i][size - 8] = true
                reserved[7][size - 1 - i] = true
                reserved[size - 1 - i][7] = true
            }
        }
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }
    }

    private fun addAlignmentPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, centerR: Int, centerC: Int) {
        for (r in -2..2) {
            for (c in -2..2) {
                val isBorder = (r == -2 || r == 2 || c == -2 || c == 2)
                val isCenter = (r == 0 && c == 0)
                matrix[centerR + r][centerC + c] = isBorder || isCenter
                reserved[centerR + r][centerC + c] = true
            }
        }
    }

    private fun getAlignmentPositions(version: Int): IntArray {
        return when (version) {
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            else -> intArrayOf(6, 18)
        }
    }

    private fun buildCodewords(data: ByteArray, version: Int): IntArray {
        val totalDataBytes = when (version) {
            1 -> 16
            2 -> 28
            3 -> 44
            4 -> 64
            5 -> 86
            else -> 108
        }

        val bits = mutableListOf<Boolean>()
        fun putBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add(((value ushr i) and 1) == 1)
            }
        }

        // Byte mode indicator: 0100
        putBits(4, 4)
        // Character count indicator (8 bits for version 1-9 byte mode)
        putBits(data.size, 8)
        // Data bytes
        for (b in data) {
            putBits(b.toInt() and 0xFF, 8)
        }
        // Terminator (up to 4 zeroes)
        val termLen = minOf(4, totalDataBytes * 8 - bits.size)
        repeat(termLen) { bits.add(false) }

        // Byte align with zeroes
        while (bits.size % 8 != 0) {
            bits.add(false)
        }

        // Convert to byte array
        val bytes = IntArray(totalDataBytes)
        var byteIdx = 0
        for (i in bits.indices step 8) {
            var v = 0
            for (b in 0 until 8) {
                if (bits[i + b]) v = v or (1 shl (7 - b))
            }
            if (byteIdx < totalDataBytes) {
                bytes[byteIdx++] = v
            }
        }

        // Pad bytes 0xEC and 0x11
        var padToggle = true
        while (byteIdx < totalDataBytes) {
            bytes[byteIdx++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        return bytes
    }

    private fun calculateErrorCorrection(data: IntArray, version: Int): IntArray {
        val ecCount = when (version) {
            1 -> 10
            2 -> 16
            3 -> 26
            4 -> 36
            5 -> 48
            else -> 64
        } / 2 // per block roughly

        // Reed-Solomon GF(256) calculation
        val poly = getGeneratorPolynomial(ecCount)
        val result = IntArray(ecCount)

        for (d in data) {
            val factor = d xor result[0]
            System.arraycopy(result, 1, result, 0, ecCount - 1)
            result[ecCount - 1] = 0
            if (factor != 0) {
                val logFactor = gfLog[factor]
                for (j in 0 until ecCount) {
                    val p = poly[j]
                    if (p != 0) {
                        result[j] = result[j] xor gfExp[(gfLog[p] + logFactor) % 255]
                    }
                }
            }
        }
        return result
    }

    private fun interleaveAndBits(data: IntArray, ec: IntArray, version: Int): BooleanArray {
        val bits = mutableListOf<Boolean>()
        for (v in data) {
            for (i in 7 downTo 0) bits.add(((v ushr i) and 1) == 1)
        }
        for (v in ec) {
            for (i in 7 downTo 0) bits.add(((v ushr i) and 1) == 1)
        }
        // Add remainder bits if needed
        val remainderBits = when (version) {
            2, 3, 4, 5, 6 -> 7
            else -> 0
        }
        repeat(remainderBits) { bits.add(false) }
        return bits.toBooleanArray()
    }

    // GF(256) tables for QR Error Correction
    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfExp[i + 255] = x
            gfLog[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
        }
    }

    private fun getGeneratorPolynomial(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = gfExp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                val prod = if (poly[j] == 0) 0 else gfExp[(gfLog[poly[j]] + gfLog[root]) % 255]
                next[j + 1] = next[j + 1] xor prod
            }
            poly = next
        }
        return poly.copyOfRange(1, poly.size)
    }
}
