package com.example.autowallpaper.helper

private val imageExtensions by lazy {
    listOf("jpg", "jpeg", "gif", "bmp", "png")
}

fun String.isImage(): Boolean {
    val extension = substringAfterLast('.').toLowerCase()
    return extension in imageExtensions
}