package com.zj.handwritingcontinuation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    public fun test(view:View) {
        val pythonGenerator = PythonGenerator()
        pythonGenerator.main()
    }
}