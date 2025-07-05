// File: ./app/src/main/java/com/example/autoresponder/ui/schedule/AddScheduleActivity.kt
package com.example.autoresponder.ui.schedule

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.example.autoresponder.R
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.databinding.ActivityAddScheduleBinding
import com.example.autoresponder.utils.DayOfWeekUtil
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class AddScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddScheduleBinding
    private val viewModel: AddScheduleViewModel by viewModels()

    private var scheduleId: Int = -1
    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()
    private var repeatingStartTime = "09:00"
    private var repeatingEndTime = "17:00"
    private val timePickerFormatter = SimpleDateFormat("h:mm a", Locale.US)
    private val datePickerFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val repeatingTimeFormatter = SimpleDateFormat("HH:mm", Locale.US)

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)

        setupSimSpinner()
        setupClickListeners()

        if (scheduleId != -1) {
            supportActionBar?.title = "Edit Schedule"
            loadScheduleDetails(scheduleId)
        } else {
            supportActionBar?.title = "Add Schedule"
            binding.scheduleTypeRadioGroup.check(R.id.radioDateRange)
            endCalendar.add(Calendar.HOUR_OF_DAY, 1)
            updateDateRangeFields()
            updateRepeatingFields()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupSimSpinner() {
        val simSlots = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        ) {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (!activeSubscriptionInfoList.isNullOrEmpty()) {
                activeSubscriptionInfoList.sortedBy { it.simSlotIndex }.forEach { subInfo ->
                    val phoneNumber = subInfo.number ?: "No number"
                    val simLabel = "SIM ${subInfo.simSlotIndex + 1}: $phoneNumber (${subInfo.carrierName})"
                    simSlots.add(simLabel)
                }
            }
        }

        if (simSlots.isEmpty()) {
            simSlots.add("Default SIM")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, simSlots)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.simSlotSpinner.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.scheduleTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioDateRange -> {
                    binding.dateRangeContainer.visibility = View.VISIBLE
                    binding.repeatingContainer.visibility = View.GONE
                }
                R.id.radioRepeating -> {
                    binding.dateRangeContainer.visibility = View.GONE
                    binding.repeatingContainer.visibility = View.VISIBLE
                }
            }
        }

        binding.startDateEditText.setOnClickListener { showDatePicker(isStart = true) }
        binding.startTimeEditText.setOnClickListener { showTimePicker(isStart = true) }
        binding.endDateEditText.setOnClickListener { showDatePicker(isStart = false) }
        binding.endTimeEditText.setOnClickListener { showTimePicker(isStart = false) }

        binding.repeatingStartTimeEditText.setOnClickListener { showRepeatingTimePicker(isStart = true) }
        binding.repeatingEndTimeEditText.setOnClickListener { showRepeatingTimePicker(isStart = false) }

        binding.chipEveryDay.setOnCheckedChangeListener { _, isChecked ->
            binding.weekDaysChipGroup.children.forEach { view ->
                if (view is Chip) {
                    view.isChecked = isChecked
                }
            }
        }

        binding.saveButton.setOnClickListener { validateAndSaveSchedule() }
    }

    private fun loadScheduleDetails(id: Int) {
        viewModel.getScheduleById(id).observe(this) { schedule ->
            if (schedule == null) return@observe

            // Populate common fields
            binding.messageEditText.setText(schedule.message)
            if (schedule.simSlot >= 0 && schedule.simSlot < binding.simSlotSpinner.adapter.count) {
                binding.simSlotSpinner.setSelection(schedule.simSlot)
            }
            binding.activeCheckBox.isChecked = schedule.isActive

            // Populate type-specific fields
            if (schedule.scheduleType == "DATE_RANGE") {
                binding.scheduleTypeRadioGroup.check(R.id.radioDateRange)
                schedule.startTimestamp?.let { startCalendar.timeInMillis = it }
                schedule.endTimestamp?.let { endCalendar.timeInMillis = it }
                updateDateRangeFields()
            } else { // REPEATING
                binding.scheduleTypeRadioGroup.check(R.id.radioRepeating)
                schedule.repeatingStartTime?.let { repeatingStartTime = it }
                schedule.repeatingEndTime?.let { repeatingEndTime = it }
                schedule.repeatingDays?.let { setSelectedDaysFromBitmask(it) }
                updateRepeatingFields()
            }

            // Disable changing schedule type when editing
            binding.radioDateRange.isEnabled = false
            binding.radioRepeating.isEnabled = false

            // Remove observer after loading once to prevent overwriting user changes
            viewModel.getScheduleById(id).removeObservers(this)
        }
    }

    private fun showDatePicker(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(calendar.timeInMillis)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val timeZone = TimeZone.getDefault()
            val offset = timeZone.getOffset(selection)
            val selectedCalendar = if (isStart) startCalendar else endCalendar
            selectedCalendar.timeInMillis = selection + offset
            updateDateRangeFields()
        }
        picker.show(supportFragmentManager, "datePicker")
    }

    private fun showTimePicker(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText("Select Time")
            .build()

        picker.addOnPositiveButtonClickListener {
            calendar.set(Calendar.HOUR_OF_DAY, picker.hour)
            calendar.set(Calendar.MINUTE, picker.minute)
            updateDateRangeFields()
        }
        picker.show(supportFragmentManager, "timePicker")
    }

    private fun showRepeatingTimePicker(isStart: Boolean) {
        val timeParts = (if (isStart) repeatingStartTime else repeatingEndTime).split(":")
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(timeParts[0].toInt())
            .setMinute(timeParts[1].toInt())
            .setTitleText("Select Time")
            .build()

        picker.addOnPositiveButtonClickListener {
            val tempCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, picker.hour)
                set(Calendar.MINUTE, picker.minute)
            }
            if (isStart) {
                repeatingStartTime = repeatingTimeFormatter.format(tempCal.time)
            } else {
                repeatingEndTime = repeatingTimeFormatter.format(tempCal.time)
            }
            updateRepeatingFields()
        }
        picker.show(supportFragmentManager, "repeatingTimePicker")
    }

    private fun updateDateRangeFields() {
        binding.startDateEditText.setText(datePickerFormatter.format(startCalendar.time))
        binding.startTimeEditText.setText(timePickerFormatter.format(startCalendar.time))
        binding.endDateEditText.setText(datePickerFormatter.format(endCalendar.time))
        binding.endTimeEditText.setText(timePickerFormatter.format(endCalendar.time))
    }

    private fun updateRepeatingFields() {
        val tempCal = Calendar.getInstance()
        var parts = repeatingStartTime.split(":")
        tempCal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        tempCal.set(Calendar.MINUTE, parts[1].toInt())
        binding.repeatingStartTimeEditText.setText(timePickerFormatter.format(tempCal.time))

        parts = repeatingEndTime.split(":")
        tempCal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        tempCal.set(Calendar.MINUTE, parts[1].toInt())
        binding.repeatingEndTimeEditText.setText(timePickerFormatter.format(tempCal.time))
    }

    private fun getSelectedDaysBitmask(): Int {
        if (binding.chipEveryDay.isChecked) return DayOfWeekUtil.EVERY_DAY

        var days = 0
        if (binding.chipMon.isChecked) days = days or DayOfWeekUtil.MONDAY
        if (binding.chipTue.isChecked) days = days or DayOfWeekUtil.TUESDAY
        if (binding.chipWed.isChecked) days = days or DayOfWeekUtil.WEDNESDAY
        if (binding.chipThu.isChecked) days = days or DayOfWeekUtil.THURSDAY
        if (binding.chipFri.isChecked) days = days or DayOfWeekUtil.FRIDAY
        if (binding.chipSat.isChecked) days = days or DayOfWeekUtil.SATURDAY
        if (binding.chipSun.isChecked) days = days or DayOfWeekUtil.SUNDAY
        return days
    }

    private fun setSelectedDaysFromBitmask(days: Int) {
        if (days == DayOfWeekUtil.EVERY_DAY) {
            binding.chipEveryDay.isChecked = true
            return
        }
        binding.chipMon.isChecked = (days and DayOfWeekUtil.MONDAY) != 0
        binding.chipTue.isChecked = (days and DayOfWeekUtil.TUESDAY) != 0
        binding.chipWed.isChecked = (days and DayOfWeekUtil.WEDNESDAY) != 0
        binding.chipThu.isChecked = (days and DayOfWeekUtil.THURSDAY) != 0
        binding.chipFri.isChecked = (days and DayOfWeekUtil.FRIDAY) != 0
        binding.chipSat.isChecked = (days and DayOfWeekUtil.SATURDAY) != 0
        binding.chipSun.isChecked = (days and DayOfWeekUtil.SUNDAY) != 0
    }

    private fun validateAndSaveSchedule() {
        val message = binding.messageEditText.text.toString().trim()
        val simSlotIndex = binding.simSlotSpinner.selectedItemPosition
        val isActive = binding.activeCheckBox.isChecked

        if (message.isBlank()) {
            Toast.makeText(this, "Please enter a message.", Toast.LENGTH_SHORT).show()
            return
        }

        val scheduleToSave: Schedule? = when (binding.scheduleTypeRadioGroup.checkedRadioButtonId) {
            R.id.radioDateRange -> {
                if (endCalendar.timeInMillis <= startCalendar.timeInMillis) {
                    Toast.makeText(this, "End date and time must be after the start.", Toast.LENGTH_SHORT).show()
                    null
                } else {
                    Schedule(
                        id = if (scheduleId != -1) scheduleId else 0,
                        scheduleType = "DATE_RANGE",
                        startTimestamp = startCalendar.timeInMillis,
                        endTimestamp = endCalendar.timeInMillis,
                        repeatingStartTime = null,
                        repeatingEndTime = null,
                        repeatingDays = null,
                        message = message,
                        simSlot = simSlotIndex,
                        isActive = isActive
                    )
                }
            }
            R.id.radioRepeating -> {
                val selectedDays = getSelectedDaysBitmask()
                if (selectedDays == 0) {
                    Toast.makeText(this, "Please select at least one day for a repeating schedule.", Toast.LENGTH_SHORT).show()
                    null
                } else {
                    Schedule(
                        id = if (scheduleId != -1) scheduleId else 0,
                        scheduleType = "REPEATING",
                        startTimestamp = null,
                        endTimestamp = null,
                        repeatingStartTime = repeatingStartTime,
                        repeatingEndTime = repeatingEndTime,
                        repeatingDays = selectedDays,
                        message = message,
                        simSlot = simSlotIndex,
                        isActive = isActive
                    )
                }
            }
            else -> null
        }

        if (scheduleToSave != null) {
            lifecycleScope.launch {
                val isOverlapping = viewModel.isScheduleOverlapping(scheduleToSave)
                if (isOverlapping) {
                    Toast.makeText(this@AddScheduleActivity, "This schedule overlaps with an existing one.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.saveSchedule(scheduleToSave)
                    val confirmationText = if (scheduleId == -1) "Schedule created." else "Schedule updated."
                    Toast.makeText(this@AddScheduleActivity, confirmationText, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}