package com.example.autowallpaper.helper

import java.io.File

fun File.getImagePathList(): List<String> {
    return listFiles { _, name ->
        name.isImage()
    }?.map { it.absolutePath } ?: listOf()
}
