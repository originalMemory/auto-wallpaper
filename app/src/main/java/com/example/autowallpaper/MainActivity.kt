package com.example.autowallpaper

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autowallpaper.helper.PrefHelper
import com.example.autowallpaper.helper.getImagePathList
import com.example.autowallpaper.helper.hideSoftKeyboard
import kotlinx.android.synthetic.main.activity_main.*
import me.rosuh.filepicker.bean.FileItemBeanImpl
import me.rosuh.filepicker.config.AbstractFileFilter
import me.rosuh.filepicker.config.FilePickerManager
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {

        private const val KEY_TIME_INTERVAL = "timeInterval"
        private const val KEY_IMAGE_FOLDER_PATH = "imageFolderPath"
        const val KEY_CURRENT_IMAGE_INDEX = "currentImageIndex"
    }

    private val prefHelper by lazy { PrefHelper() }

    private var imagePaths = listOf<String>()
    private var timeInterval = 0
    private var currentImageIndex = 0
    private var imageFolderPath = ""
    private var imageBinder: AutoWallpaperService.ImageBinder? = null
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            imageBinder = service as? AutoWallpaperService.ImageBinder
            imageBinder?.listener = {
                currentImageIndex = it
                renderImage(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            imageBinder = null
        }
    }

    private fun renderImage(systemIndex: Int) {
        if (systemIndex >= imagePaths.size) {
            return
        }
        currentImageIndex = systemIndex
        val systemPath = imagePaths[systemIndex]
        val systemFileName = systemPath.substringAfterLast('/')
        systemIndexTextView.text = "[${systemIndex + 1}/${imagePaths.size}]\n$systemFileName"
        val systemBitmap = BitmapFactory.decodeFile(systemPath)
        systemImageView.setImageBitmap(systemBitmap)

        val lockIndex = imagePaths.size - systemIndex - 1
        val lockPath = imagePaths[lockIndex]
        val lockFileName = lockPath.substringAfterLast('/')
        lockIndexTextView.text = "[${lockIndex + 1}/${imagePaths.size}]\n$lockFileName"
        val lockBitmap = BitmapFactory.decodeFile(lockPath)
        lockImageView.setImageBitmap(lockBitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupInterface()
        loadConfig()
        if (imageFolderPath.isNotEmpty()) {
            val intent = Intent(this, AutoWallpaperService::class.java)
            startForegroundService(intent)
        }
    }

    private fun loadConfig() {
        timeInterval = prefHelper.getInt(KEY_TIME_INTERVAL)
        imageFolderPath = prefHelper.getString(KEY_IMAGE_FOLDER_PATH)
        currentImageIndex = prefHelper.getInt(KEY_CURRENT_IMAGE_INDEX)
        imageFolderPathTextView.text =
            if (imageFolderPath.isNotEmpty()) imageFolderPath else "未选择文件夹"
        if (imageFolderPath.isNotEmpty()) {
            imagePaths = File(imageFolderPath).getImagePathList()
        }
        timeIntervalEditText.setText(timeInterval.toString())
        renderImage(currentImageIndex)
    }

    private fun bindAutoWallpaperService() {
        val intent = Intent(this, AutoWallpaperService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun unBindAutoWallpaperService() {
        unbindService(connection)
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
            if (imageFolderPath != this.imageFolderPath) {
                currentImageIndex = 0
            }
            this.imageFolderPath = imageFolderPath
            imagePaths = File(imageFolderPath).getImagePathList()
            if (imagePaths.isEmpty()) {
                toast("选择的文件夹没有图片")
                return@setOnClickListener
            }
            prefHelper.put(KEY_IMAGE_FOLDER_PATH, imageFolderPath)

            val timeInterval = timeIntervalEditText.text.toString().toInt()
            if (timeInterval == 0) {
                toast("时间不能为 0 ")
                return@setOnClickListener
            }
            this.timeInterval = timeInterval
            prefHelper.put(KEY_TIME_INTERVAL, timeInterval)

            imageBinder?.startChangeWallpaper(imageFolderPath, timeInterval, currentImageIndex)
        }
        stopButton.setOnClickListener {
            val intent = Intent(this, AutoWallpaperService::class.java)
            stopService(intent)
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

    override fun onStart() {
        super.onStart()
        if (imageBinder == null) {
            bindAutoWallpaperService()
        }
    }

    override fun onResume() {
        super.onResume()
        imageBinder?.let {
            if (it.index != currentImageIndex && imagePaths.isNotEmpty()) {
                renderImage(it.index)
            }
        }
    }

    override fun onStop() {
        super.onStop()
    }
}
