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
        val rawLines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val timePattern = Pattern.compile("(\\d+):(\\d+):(\\d+)[,\\.](\\d+)")
        
        var i = 0
        while (i < rawLines.size) {
            val line = rawLines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }
            
            var index: Int? = null
            var startTimeMs: Long? = null
            var endTimeMs: Long? = null
            
            if (line.contains("-->")) {
                val parts = line.split("-->")
                if (parts.size == 2) {
                    startTimeMs = parseTimestamp(parts[0].trim(), timePattern)
                    endTimeMs = parseTimestamp(parts[1].trim(), timePattern)
                }
            } else {
                val nextIndex = findNextNonEmptyLineIndex(rawLines, i + 1)
                if (nextIndex != -1 && rawLines[nextIndex].contains("-->")) {
                    val possibleIndexStr = line.filter { it.isDigit() }
                    index = possibleIndexStr.toIntOrNull()
                    
                    val timeLine = rawLines[nextIndex].trim()
                    val parts = timeLine.split("-->")
                    if (parts.size == 2) {
                        startTimeMs = parseTimestamp(parts[0].trim(), timePattern)
                        endTimeMs = parseTimestamp(parts[1].trim(), timePattern)
                    }
                    i = nextIndex
                }
            }
            
            if (startTimeMs != null && endTimeMs != null) {
                i++
                val textLines = mutableListOf<String>()
                while (i < rawLines.size) {
                    val nextLine = rawLines[i].trim()
                    if (nextLine.isEmpty()) {
                        break
                    }
                    
                    if (nextLine.all { it.isDigit() }) {
                        val aheadIdx = findNextNonEmptyLineIndex(rawLines, i + 1)
                        if (aheadIdx != -1 && rawLines[aheadIdx].contains("-->")) {
                            break
                        }
                    } else if (nextLine.contains("-->")) {
                        break
                    }
                    
                    textLines.add(rawLines[i])
                    i++
                }
                
                val finalIndex = index ?: (lines.size + 1)
                val subtitleText = textLines.joinToString("\n").trim()
                lines.add(SrtLine(finalIndex, startTimeMs, endTimeMs, subtitleText))
            } else {
                i++
            }
        }
        
        return lines
    }

    private fun findNextNonEmptyLineIndex(rawLines: List<String>, startIndex: Int): Int {
        for (idx in startIndex until rawLines.size) {
            if (rawLines[idx].trim().isNotEmpty()) {
                return idx
            }
        }
        return -1
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
