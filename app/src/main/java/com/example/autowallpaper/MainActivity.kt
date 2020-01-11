package com.example.autowallpaper

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import me.rosuh.filepicker.bean.FileItemBeanImpl
import me.rosuh.filepicker.config.AbstractFileFilter
import me.rosuh.filepicker.config.FilePickerManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
}
