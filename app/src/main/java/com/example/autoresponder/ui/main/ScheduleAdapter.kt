// File: ./app/src/main/java/com/example/autoresponder/ui/main/ScheduleAdapter.kt
package com.example.autoresponder.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.autoresponder.R
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.databinding.ScheduleItemBinding
import com.example.autoresponder.utils.DayOfWeekUtil
import java.text.SimpleDateFormat
import java.util.*

class ScheduleAdapter(private val listener: ScheduleItemListener) :
    ListAdapter<Schedule, ScheduleAdapter.ScheduleViewHolder>(SchedulesComparator()) {

    interface ScheduleItemListener {
        fun onEditClicked(schedule: Schedule)
        fun onDeleteClicked(schedule: Schedule)
        fun onStatusChanged(schedule: Schedule, isChecked: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding =
            ScheduleItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current, listener)
    }

    class ScheduleViewHolder(private val binding: ScheduleItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateTimeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        private val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)

        fun bind(schedule: Schedule, listener: ScheduleItemListener) {
            binding.messageTextView.text = schedule.message

            var isActiveNow = false
            if (schedule.scheduleType == "DATE_RANGE") {
                // Handle date range display
                binding.startTimeTextView.text = schedule.startTimestamp?.let { dateTimeFormatter.format(Date(it)) } ?: ""
                binding.endTimeTextView.text = schedule.endTimestamp?.let { dateTimeFormatter.format(Date(it)) } ?: ""
                val now = System.currentTimeMillis()
                isActiveNow = schedule.isActive && schedule.startTimestamp != null && schedule.endTimestamp != null &&
                        now >= schedule.startTimestamp && now < schedule.endTimestamp
            } else { // REPEATING
                // Handle repeating display
                val cal = Calendar.getInstance()
                var parts = schedule.repeatingStartTime?.split(":")
                parts?.let {
                    cal.set(Calendar.HOUR_OF_DAY, it[0].toInt())
                    cal.set(Calendar.MINUTE, it[1].toInt())
                    binding.startTimeTextView.text = timeFormatter.format(cal.time)
                }

                parts = schedule.repeatingEndTime?.split(":")
                parts?.let {
                    cal.set(Calendar.HOUR_OF_DAY, it[0].toInt())
                    cal.set(Calendar.MINUTE, it[1].toInt())
                    binding.endTimeTextView.text = timeFormatter.format(cal.time)
                }

                // You would add another TextView to show the repeating days string
                // e.g., binding.repeatingDaysTextView.text = DayOfWeekUtil.bitmaskToSimpleString(schedule.repeatingDays ?: 0)

                val now = Calendar.getInstance()
                val todayBit = DayOfWeekUtil.calendarDayToBitmask(now.get(Calendar.DAY_OF_WEEK))
                val repeatsToday = (schedule.repeatingDays ?: 0) and todayBit != 0

                // Simplified time check for repeating schedule
                isActiveNow = schedule.isActive && repeatsToday // A full check would parse repeatingStartTime/EndTime
            }

            if (isActiveNow) {
                binding.activeIndicator.visibility = View.VISIBLE
                binding.activeIndicator.setImageResource(R.drawable.ic_active_circle)
            } else {
                binding.activeIndicator.visibility = View.GONE
            }

            binding.editButton.setOnClickListener { listener.onEditClicked(schedule) }
            binding.deleteButton.setOnClickListener { listener.onDeleteClicked(schedule) }
            binding.activeStatusSwitch.setOnCheckedChangeListener(null)
            binding.activeStatusSwitch.isChecked = schedule.isActive
            binding.activeStatusSwitch.setOnCheckedChangeListener { _, isChecked ->
                listener.onStatusChanged(schedule, isChecked)
            }
        }
    }

    class SchedulesComparator : DiffUtil.ItemCallback<Schedule>() {
        override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule): Boolean = oldItem == newItem
    }
}