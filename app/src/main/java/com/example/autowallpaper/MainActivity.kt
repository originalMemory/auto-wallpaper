package com.example.autowallpaper

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autowallpaper.helper.Const.KEY_IMAGE_FOLDER_PATH
import com.example.autowallpaper.helper.Const.KEY_TIME_INTERVAL
import com.example.autowallpaper.helper.PrefHelper
import com.example.autowallpaper.helper.hideSoftKeyboard
import com.example.autowallpaper.helper.isImage
import kotlinx.android.synthetic.main.activity_main.*
import me.rosuh.filepicker.bean.FileItemBeanImpl
import me.rosuh.filepicker.config.AbstractFileFilter
import me.rosuh.filepicker.config.FilePickerManager
import java.io.File

class MainActivity : AppCompatActivity() {

    private val prefHelper by lazy { PrefHelper() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupInterface()
        loadConfig()
    }

    private fun loadConfig() {
        val timeInterval = prefHelper.getInt(KEY_TIME_INTERVAL)
        val imageFolderPath = prefHelper.getString(KEY_IMAGE_FOLDER_PATH)
        if (timeInterval > 0 && imageFolderPath.isNotEmpty()) {
            startReplaceWallpaper(imageFolderPath, timeInterval)
        }
        imageFolderPathTextView.text = if (imageFolderPath.isNotEmpty()) imageFolderPath else "未选择文件夹"
        timeIntervalEditText.setText(timeInterval.toString())
    }

    private fun startReplaceWallpaper(imageFolderPath: String, timeInterval: Int) {
        val intent = Intent(this, AutoWallpaperService::class.java)
        intent.putExtra(KEY_IMAGE_FOLDER_PATH, imageFolderPath)
        intent.putExtra(KEY_TIME_INTERVAL, timeInterval)
        startService(intent)
    }

    private fun stopReplaceWallpaper() {
        val intent = Intent(this, AutoWallpaperService::class.java)
        stopService(intent)
    }

    private fun setupInterface() {
        imageFolderPathTextView.setOnClickListener {
            val filter = object : AbstractFileFilter() {

                override fun doFilter(listData: ArrayList<FileItemBeanImpl>): ArrayList<FileItemBeanImpl> {
                    return ArrayList(listData.filter {
                        it.isDir
                    })
                }
            }
            FilePickerManager
                .from(this)
                .maxSelectable(1)
                .filter(filter)
                .skipDirWhenSelect(false)
                .setTheme(R.style.FilePickerThemeCrane)
                .forResult(FilePickerManager.REQUEST_CODE)
        }
        container.setOnTouchListener { _, _ ->
            this.hideSoftKeyboard(timeIntervalEditText)
            container.requestFocus()
            true
        }
        saveButton.setOnClickListener {
            val imageFolderPath = imageFolderPathTextView.text.toString()
            val imagePaths = File(imageFolderPath).listFiles()?.map { it.absolutePath } ?: listOf()
            if (!imagePaths.any { it.isImage() }) {
                toast("选择的文件夹没有图片")
                return@setOnClickListener
            }
            prefHelper.put(KEY_IMAGE_FOLDER_PATH, imageFolderPath)

            val timeInterval = timeIntervalEditText.text.toString().toInt()
            if (timeInterval == 0) {
                toast("时间不能为 0 ")
                return@setOnClickListener
            }
            prefHelper.put(KEY_TIME_INTERVAL, timeInterval)

            startReplaceWallpaper(imageFolderPath, timeInterval)
        }
    }

    private fun toast(content: String) {
        Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FilePickerManager.REQUEST_CODE && resultCode == RESULT_OK) {
            val list = FilePickerManager.obtainData()
            list.firstOrNull()?.let {
                imageFolderPathTextView.text = it
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        stopReplaceWallpaper()
        super.onDestroy()
    }
}
