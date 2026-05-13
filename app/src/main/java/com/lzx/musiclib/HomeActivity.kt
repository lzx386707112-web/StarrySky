package com.lzx.musiclib

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.lzx.musiclib.home.MainActivity
import com.lzx.musiclib.home.TestActivity
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        findViewById<Button>(R.id.btn1).setOnClickListener { navigationTo<TestActivity>() }
        findViewById<Button>(R.id.btn2).setOnClickListener { navigationTo<MainActivity>() }
    }
}