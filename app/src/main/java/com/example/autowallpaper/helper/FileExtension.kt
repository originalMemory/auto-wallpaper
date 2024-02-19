package com.example.autowallpaper.helper

import android.annotation.SuppressLint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

fun File.getImageInfoList(): List<ImageInfo> {
    val result = mutableListOf<ImageInfo>()
    listFiles()?.forEach {
        if (it.isFile) {
            if (it.path.isImage()) {
                result.add(getImageInfo(it.absolutePath))
            }
        } else {
            result.addAll(it.getImageInfoList())
        }
    }
    return result
}

@SuppressLint("SimpleDateFormat")
private fun getImageInfo(path: String): ImageInfo {
    val regex = Regex("""(\w+)_(\d+)_(\d+)_(\d{4}-\d{2}-\d{2})\.\w+""")
    val matchResult =
        regex.find(path) ?: return ImageInfo(path, path.split('/').last(), null, null, null)
    val (title, type, level, date) = matchResult.destructured
    return ImageInfo(
        path,
        title.split('/').last(),
        type.toInt(),
        level.toInt(),
        SimpleDateFormat("yyyy-MM-dd").parse(date)
    )
}

data class ImageInfo(
    val path: String,
    val title: String,
    val type: Int?,
    val level: Int?,
    val createDate: Date?
)
