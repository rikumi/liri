package io.github.rikumi.lyrichelper

internal data class ParsedLyricLine(val timeMs: Int, val text: String)

internal fun lyricOffsetMs(lyrics: String): Int =
    Regex("(?im)^\\[offset:([+-]?\\d+)\\]").find(lyrics)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(-30000, 30000) ?: 0

internal fun parseLyricText(lyrics: String, offsetMs: Int = 0): List<ParsedLyricLine> {
    val offset = (lyricOffsetMs(lyrics).takeIf { it != 0 } ?: offsetMs).coerceIn(-30000, 30000)
    val timeTagRegex = Regex("\\[(\\d+):(\\d{1,2})(?:[.:](\\d+))?\\]")
    return Regex("(\\[[\\d.:]+])+([^\\[\\n]*)").findAll(lyrics).flatMap { line ->
        val content = line.groupValues[2].trim()
        timeTagRegex.findAll(line.value).mapNotNull { tag ->
            val minutes = tag.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = tag.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fraction = tag.groupValues[3]
            val milliseconds = when {
                fraction.isEmpty() -> 0L
                fraction.length == 1 -> fraction.toLong() * 100L
                fraction.length == 2 -> fraction.toLong() * 10L
                else -> fraction.take(3).toLong()
            }
            ParsedLyricLine(
                (((minutes * 60L + seconds) * 1000L) + milliseconds + offset).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                content,
            )
        }
    }.sortedBy { it.timeMs }.toList()
}
