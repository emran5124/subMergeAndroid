package com.example.utils

import java.util.regex.Pattern

object SrtParser {
    data class SrtLine(
        val index: Int,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String
    )

    fun parse(content: String): List<SrtLine> {
        val lines = mutableListOf<SrtLine>()
        // Normalize line breaks
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split("\n\n")

        val timePattern = Pattern.compile("(\\d+):(\\d+):(\\d+)[,\\.](\\d+)")

        for (block in blocks) {
            val sBlock = block.trim()
            if (sBlock.isEmpty()) continue

            val blockLines = sBlock.split("\n")
            if (blockLines.size < 2) continue

            val indexStr = blockLines[0].trim().filter { it.isDigit() }
            val index = indexStr.toIntOrNull() ?: continue

            val timeLine = blockLines[1].trim()
            val timeParts = timeLine.split("-->")
            if (timeParts.size != 2) continue

            val startTimeMs = parseTimestamp(timeParts[0].trim(), timePattern) ?: continue
            val endTimeMs = parseTimestamp(timeParts[1].trim(), timePattern) ?: continue

            val subtitleText = blockLines.drop(2).joinToString("\n").trim()

            lines.add(SrtLine(index, startTimeMs, endTimeMs, subtitleText))
        }

        return lines
    }

    fun parseTimestamp(timestamp: String, pattern: Pattern): Long? {
        val matcher = pattern.matcher(timestamp)
        if (matcher.find()) {
            val hrs = matcher.group(1).toLongOrNull() ?: 0L
            val mins = matcher.group(2).toLongOrNull() ?: 0L
            val secs = matcher.group(3).toLongOrNull() ?: 0L
            var msecStr = matcher.group(4)
            // Pad or truncate milliseconds to 3 digits
            if (msecStr.length < 3) {
                msecStr = msecStr.padEnd(3, '0')
            } else if (msecStr.length > 3) {
                msecStr = msecStr.substring(0, 3)
            }
            val msecs = msecStr.toLongOrNull() ?: 0L

            return (hrs * 3600000) + (mins * 60000) + (secs * 1000) + msecs
        }
        return null
    }

    fun formatTime(ms: Long): String {
        val clampMs = if (ms < 0) 0 else ms
        val hrs = clampMs / 3600000
        val mins = (clampMs % 3600000) / 60000
        val secs = (clampMs % 60000) / 1000
        val msecs = clampMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hrs, mins, secs, msecs)
    }

    fun buildSrt(lines: List<SrtLine>): String {
        val builder = StringBuilder()
        for ((idx, line) in lines.withIndex()) {
            builder.append(idx + 1).append("\n")
            builder.append(formatTime(line.startTimeMs))
                .append(" --> ")
                .append(formatTime(line.endTimeMs))
                .append("\n")
            builder.append(line.text).append("\n\n")
        }
        return builder.toString()
    }
}
