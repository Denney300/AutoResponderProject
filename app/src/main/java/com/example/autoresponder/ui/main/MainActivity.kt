// File: ./app/src/main/java/com/example/autoresponder/ui/main/MainActivity.kt
package com.example.autoresponder.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autoresponder.R
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.databinding.ActivityMainBinding
import com.example.autoresponder.ui.AboutActivity
import com.example.autoresponder.ui.PrivacyPolicyActivity
import com.example.autoresponder.ui.SimSettingsActivity
import com.example.autoresponder.ui.schedule.AddScheduleActivity
import com.example.autoresponder.utils.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ScheduleAdapter.ScheduleItemListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                Toast.makeText(this, "Some permissions were denied. App may not function.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val adapter = ScheduleAdapter(this)
        binding.scheduleRecyclerView.adapter = adapter
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.allSchedules.observe(this) { schedules ->
            schedules?.let {
                adapter.submitList(it)
            }
        }

        binding.fabAddSchedule.setOnClickListener {
            val intent = Intent(this, AddScheduleActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_NUMBERS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_NUMBERS
            )
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme_settings -> {
                showThemeDialog()
                true
            }
            R.id.action_sim_settings -> {
                startActivity(Intent(this, SimSettingsActivity::class.java))
                true
            }
            R.id.action_notification_settings -> {
                openNotificationSettings()
                true
            }
            R.id.action_privacy_policy -> {
                startActivity(Intent(this, PrivacyPolicyActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System Default")
        val themeValues = intArrayOf(ThemeHelper.THEME_LIGHT, ThemeHelper.THEME_DARK, ThemeHelper.THEME_SYSTEM)

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setItems(themes) { dialog, which ->
                val selectedTheme = themeValues[which]
                ThemeHelper.setTheme(selectedTheme)
                ThemeHelper.saveTheme(this, selectedTheme)
                dialog.dismiss()
            }
            .show()
    }

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    override fun onEditClicked(schedule: Schedule) {
        val intent = Intent(this, AddScheduleActivity::class.java).apply {
            putExtra(AddScheduleActivity.EXTRA_SCHEDULE_ID, schedule.id)
        }
        startActivity(intent)
    }

    override fun onDeleteClicked(schedule: Schedule) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete this schedule?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSchedule(schedule)
                Toast.makeText(this, "Schedule deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onStatusChanged(schedule: Schedule, isChecked: Boolean) {
        val updatedSchedule = schedule.copy(isActive = isChecked)
        viewModel.updateSchedule(updatedSchedule)
    }
}