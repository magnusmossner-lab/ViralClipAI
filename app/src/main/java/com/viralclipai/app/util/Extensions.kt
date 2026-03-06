package com.viralclipai.app.util

fun String.isYouTubeUrl(): Boolean {
    val trimmed = this.trim()
    return trimmed.contains("youtube.com/watch") ||
           trimmed.contains("youtu.be/") ||
           trimmed.contains("youtube.com/shorts/") ||
           trimmed.contains("youtube.com/live/") ||
           trimmed.contains("m.youtube.com/watch")
}

fun Int.formatViews(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}.${(this % 1_000_000) / 100_000}M"
    this >= 1_000 -> "${this / 1_000}.${(this % 1_000) / 100}K"
    else -> this.toString()
}

fun Float.toPercentString(): String = "${(this * 100).toInt()}%"
