// File: ./app/src/main/java/com/example/autoresponder/ui/AboutActivity.kt
package com.example.autoresponder.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.autoresponder.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "About"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}