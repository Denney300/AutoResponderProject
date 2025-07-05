// File: ./app/src/main/java/com/example/autoresponder/ui/PrivacyPolicyActivity.kt
package com.example.autoresponder.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.autoresponder.databinding.ActivityPrivacyPolicyBinding

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Privacy Policy"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}