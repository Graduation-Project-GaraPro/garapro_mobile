package com.example.garapro.ui.RepairProgress

import android.R.attr.orientation
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.RepairProgresses.Job
import com.example.garapro.data.model.RepairProgresses.Part
import com.example.garapro.data.model.RepairProgresses.Repair
import com.example.garapro.data.model.RepairProgresses.Technician
import com.example.garapro.databinding.ItemJobBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class JobAdapter : ListAdapter<Job, JobAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Job>() {
            override fun areItemsTheSame(oldItem: Job, newItem: Job): Boolean {
                return oldItem.jobId == newItem.jobId
            }

            override fun areContentsTheSame(oldItem: Job, newItem: Job): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemJobBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val job = getItem(position)
        holder.bind(job)
    }

    inner class ViewHolder(private val binding: ItemJobBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var isExpanded = false

        fun bind(job: Job) {
            binding.apply {
                jobName.text = job.jobName
                jobLevel.text = "Level ${job.level}"
                jobStatus.text = getStatusNameEnglish(job.status)
                jobAmount.text = formatCurrency(job.totalAmount)
                jobDeadline.text = formatDate(job.deadline)
                jobNote.text = job.note ?: "No note"

                // Set status background color
                setJobStatusColor(job.status)

                // Setup service details
                job.repair?.let { repair ->
                    serviceDetailsCard.visibility = View.VISIBLE
                    repairDescription.text = repair.description
                    estimatedTime.text = repair.estimatedTime ?: "N/A"

                    // Repair notes
                    repair.notes?.let { notes ->
                        repairNotes.text = notes
                        repairNotes.visibility = View.VISIBLE
                    } ?: run {
                        repairNotes.visibility = View.GONE
                    }

                    // Repair times
                    setupRepairTimes(repair)
                } ?: run {
                    serviceDetailsCard.visibility = View.GONE
                }

                // Setup technicians
                if (job.technicians.isNotEmpty()) {
                    techniciansLayout.visibility = View.VISIBLE
                    setupTechnicians(job.technicians)
                } else {
                    techniciansLayout.visibility = View.GONE
                }

                // Setup parts
                if (job.parts.isNotEmpty()) {
                    partsLayout.visibility = View.VISIBLE
                    setupParts(job.parts)
                } else {
                    partsLayout.visibility = View.GONE
                }

                // Expand/Collapse functionality
                expandButton.setOnClickListener {
                    isExpanded = !isExpanded
                    toggleExpansion()
                }

                // Set initial state
                toggleExpansion()
            }
        }

        private fun getStatusNameEnglish(statusName: String): String {
            return when (statusName) {
                "Pending" -> "Pending"
                "New" -> "New"
                "InProgress" -> "In Progress"
                "Completed" -> "Completed"
                "OnHold" -> "On Hold"
                else -> "Unknown"
            }
        }

        private fun setupRepairTimes(repair: Repair) {
            var hasTimeInfo = false

            // Reset all layouts
            binding.repairTimeLogsLayout.visibility = View.GONE
            binding.startTimeLayout.visibility = View.GONE
            binding.endTimeLayout.visibility = View.GONE
            binding.actualTimeLayout.visibility = View.GONE

            // Setup start time
            repair.startTime?.let { startTimeString ->
                parseTimeString(startTimeString)?.let { startTime ->
                    binding.startTimeLayout.visibility = View.VISIBLE
                    binding.tvStartTime.text = formatDateTime(startTime)
                    hasTimeInfo = true
                }
            }

            // Setup end time
            repair.endTime?.let { endTimeString ->
                parseTimeString(endTimeString)?.let { endTime ->
                    binding.endTimeLayout.visibility = View.VISIBLE
                    binding.tvEndTime.text = formatDateTime(endTime)
                    hasTimeInfo = true
                }
            }

            // Setup actual time
            repair.actualTime?.let { actualTime ->
                binding.actualTimeLayout.visibility = View.VISIBLE
                binding.tvActualTime.text = formatDuration(actualTime)
                hasTimeInfo = true
            }

            // Show time logs section if any time info exists
            binding.repairTimeLogsLayout.visibility = if (hasTimeInfo) View.VISIBLE else View.GONE
        }

        private fun formatDateTime(date: Date): String {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return "${dateFormat.format(date)} at ${timeFormat.format(date)}"
        }

        private fun formatDuration(durationString: String): String {
            // Split hours, minutes, seconds
            val timeParts = durationString.split(":")
            if (timeParts.size < 3) return durationString // fallback if wrong format

            val hours = timeParts[0].toIntOrNull() ?: 0
            val minutes = timeParts[1].toIntOrNull() ?: 0

            return if (hours > 0) {
                if (minutes > 0) {
                    "${hours}h${minutes}m"    // e.g., 1h30m
                } else {
                    "${hours} hour"           // e.g., 1 hour
                }
            } else {
                "$minutes minutes"           // e.g., 30 minutes
            }
        }

        private fun parseTimeString(timeString: String): Date? {
            return try {
                // Try common formats
                val formats = arrayOf(
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "dd/MM/yyyy HH:mm:ss",
                    "dd/MM/yyyy HH:mm",
                    "yyyy-MM-dd",
                    "HH:mm:ss",
                    "HH:mm"
                )

                for (format in formats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("UTC") // Use UTC for consistency
                        return sdf.parse(timeString)
                    } catch (e: Exception) {
                        continue
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }

        private fun getEnglishDay(dayString: String): String {
            return when (dayString.toLowerCase(Locale.getDefault())) {
                "thứ 2", "monday" -> "Monday"
                "thứ 3", "tuesday" -> "Tuesday"
                "thứ 4", "wednesday" -> "Wednesday"
                "thứ 5", "thursday" -> "Thursday"
                "thứ 6", "friday" -> "Friday"
                "thứ 7", "saturday" -> "Saturday"
                "chủ nhật", "sunday" -> "Sunday"
                else -> dayString
            }
        }

        private fun setupTechnicians(technicians: List<Technician>) {
            binding.techniciansChipGroup.removeAllViews()
            technicians.forEach { technician ->
                val chip = Chip(binding.root.context).apply {
                    text = technician.fullName
                    isCloseIconVisible = false
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.black)
                    )
                    setOnClickListener { showTechnicianInfo(technician) }
                }
                binding.techniciansChipGroup.addView(chip)
            }
        }

        private fun setupParts(parts: List<Part>) {
            binding.partsRecyclerView.layoutManager = LinearLayoutManager(
                binding.root.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            val adapter = PartAdapter()
            adapter.submitList(parts)
            binding.partsRecyclerView.adapter = adapter
        }

        private fun toggleExpansion() {
            binding.apply {
                serviceDetailsCard.visibility = if (isExpanded) View.VISIBLE else View.GONE
                techniciansLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
                partsLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE

                expandButton.text = if (isExpanded) "Hide Details" else "View Details"
                expandButton.setIconResource(
                    if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                )
            }
        }

        private fun setJobStatusColor(status: String) {
            val color = when (status) {
                "Completed" -> R.color.green
                "In Progress" -> R.color.blue
                "New" -> R.color.orange
                "Pending" -> R.color.orange
                "OnHold" -> R.color.red
                "Cancelled" -> R.color.red
                else -> R.color.gray
            }
            binding.jobStatus.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, color)
            )
        }

        private fun showTechnicianInfo(technician: Technician) {
            val spannableMessage = android.text.SpannableStringBuilder()

            if (!technician.email.isNullOrBlank()) {
                spannableMessage.append(" Email\n")
                spannableMessage.append("${technician.email}\n\n")
            }

            if (!technician.phoneNumber.isNullOrBlank()) {
                spannableMessage.append(" Phone\n")
                spannableMessage.append("${technician.phoneNumber}\n\n")
            }



            if (spannableMessage.isEmpty()) {
                spannableMessage.append("No additional information available")
            }

            // Apply styling to labels (make them bold)
            val labels = arrayOf(" Email", " Phone")
            labels.forEach { label ->
                val start = spannableMessage.indexOf(label)
                if (start != -1) {
                    val end = start + label.length
                    spannableMessage.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannableMessage.setSpan(
                        android.text.style.ForegroundColorSpan(ContextCompat.getColor(binding.root.context, R.color.primary_color)),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            MaterialAlertDialogBuilder(binding.root.context)
                .setTitle(technician.fullName)
                .setMessage(spannableMessage)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun formatCurrency(amount: Double): String {
            return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
        }

        private fun formatDate(dateString: String?): String {
            if (dateString == null) return "No deadline"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                dateString
            }
        }
    }
}