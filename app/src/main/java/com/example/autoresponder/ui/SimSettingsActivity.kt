// File: ./app/src/main/java/com/example/autoresponder/ui/SimSettingsActivity.kt
package com.example.autoresponder.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autoresponder.databinding.ActivitySimSettingsBinding

class SimSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Customize SIM Names"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadSimNames()

        binding.saveSimNamesButton.setOnClickListener {
            saveSimNames()
        }
    }

    private fun loadSimNames() {
        val prefs = getSharedPreferences("SimNamePrefs", Context.MODE_PRIVATE)
        binding.sim1NameEditText.setText(prefs.getString("SIM_0_NAME", ""))
        binding.sim2NameEditText.setText(prefs.getString("SIM_1_NAME", ""))
    }

    private fun saveSimNames() {
        val prefs = getSharedPreferences("SimNamePrefs", Context.MODE_PRIVATE).edit()
        prefs.putString("SIM_0_NAME", binding.sim1NameEditText.text.toString().trim())
        prefs.putString("SIM_1_NAME", binding.sim2NameEditText.text.toString().trim())
        prefs.apply()

        Toast.makeText(this, "SIM names saved!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}