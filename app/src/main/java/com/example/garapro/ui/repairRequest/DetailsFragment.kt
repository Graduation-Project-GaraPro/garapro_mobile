package com.example.garapro.ui.repairRequest

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import com.example.garapro.databinding.FragmentDetailsBinding
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garapro.R
import java.util.Calendar
import android.text.TextWatcher
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.garapro.data.model.repairRequest.ArrivalWindow
import kotlinx.coroutines.launch

class DetailsFragment : BaseBookingFragment() {

    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var imageAdapter: ImageAdapter

    private lateinit var timeSlotAdapter: TimeSlotAdapter
    private var selectedDateOnly: String? = null
    companion object {
        private const val PICK_IMAGES_REQUEST = 1001
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        setupDatePicker()
        setupTimeSlotRecycler()
        setupBaseObservers()
    }

    private fun setupTimeSlotRecycler() {
        timeSlotAdapter = TimeSlotAdapter(emptyList()) { slot ->
            // Lấy ngày từ selectedDateOnly; nếu null thì lấy luôn từ ISO windowStart
            val datePart = selectedDateOnly ?: slot.windowStart.substring(0, 10) // yyyy-MM-dd
            val startHm = slot.windowStart.substringAfter('T').substring(0, 5)   // HH:mm

            val selectedDateTime = "$datePart $startHm"
            bookingViewModel.setRequestDate(selectedDateTime)
            binding.etRequestDate.setText(selectedDateTime)
            validateForm()
        }

        binding.rvTimeSlots.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = timeSlotAdapter
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(mutableListOf(), { uri ->
            bookingViewModel.removeImageUri(uri) }, isReadOnly = false)


        binding.rvImages.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = imageAdapter
        }
    }

    private fun setupObservers() {
        bookingViewModel.imageUris.observe(viewLifecycleOwner) { uris ->
            imageAdapter.updateData(uris)
            validateForm()
        }

        bookingViewModel.requestDate.observe(viewLifecycleOwner) { date ->
            binding.etRequestDate.setText(date)

            // Đồng bộ selectedDateOnly từ requestDate nếu có
            if (!date.isNullOrBlank() && date.length >= 10) {
                selectedDateOnly = date.substring(0, 10) // yyyy-MM-dd
            }

            validateForm()
        }
        bookingViewModel.arrivalWindows.observe(viewLifecycleOwner) { slots ->
            val showList = slots.isNotEmpty()
            binding.tvTimeSlotsTitle.visibility = if (showList) View.VISIBLE else View.GONE
            binding.rvTimeSlots.visibility = if (showList) View.VISIBLE else View.GONE

            timeSlotAdapter.submitList(slots)
            val current = bookingViewModel.requestDate.value
            if (!current.isNullOrBlank() && current.length >= 16) { // "yyyy-MM-dd HH:mm"
                val date = current.substring(0, 10)
                val hm = current.substring(11, 16)
                timeSlotAdapter.setSelectedByDateHm(date, hm)
            }

            if (!showList && selectedDateOnly != null) {
                Toast.makeText(requireContext(), "Không còn khung giờ trống cho ngày này.", Toast.LENGTH_SHORT).show()
            }


        }
        bookingViewModel.requestDate.value?.let { dt ->
            if (dt.length >= 10) {
                selectedDateOnly = dt.substring(0, 10) // yyyy-MM-dd
                val branch = bookingViewModel.selectedBranch.value
                if (branch != null) {
                    bookingViewModel.loadArrivalAvailability(branch.branchId, selectedDateOnly!!)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                bookingViewModel.setDescription(s.toString())
                validateForm()
            }
        })

        binding.btnAddImage.setOnClickListener {
            openImagePicker()
        }

        binding.btnNext.setOnClickListener {
            if (validateForm()) {

                showNextFragment(R.id.action_details_to_confirmation)

            }
        }

        binding.btnPrevious.setOnClickListener {
            showPreviousFragment()
        }
    }

    private fun setupDatePicker() {
        binding.etRequestDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()

        // Hộp thoại chọn ngày
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)

                // Lưu lại yyyy-MM-dd để gọi API lấy khung giờ
                selectedDateOnly = String.format(
                    "%04d-%02d-%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                // Reset giờ hiển thị (vì sẽ chọn theo slot)
                binding.etRequestDate.setText(selectedDateOnly)
                fetchArrivalAvailability()

            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Giới hạn: không cho chọn ngày trong quá khứ
        datePicker.datePicker.minDate = now.timeInMillis
        datePicker.show()
    }

    private fun fetchArrivalAvailability() {
        val date = selectedDateOnly ?: return
        val branch = bookingViewModel.selectedBranch.value ?: return
        // ViewModel sẽ set _isLoading để BaseBookingFragment hiển thị loading
        bookingViewModel.loadArrivalAvailability(branch.branchId, date)


    }




    // Hàm phụ kiểm tra có phải là hôm nay không
    private fun isToday(selected: Calendar, now: Calendar): Boolean {
        return selected.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                selected.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGES_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val imageUri = data.clipData!!.getItemAt(i).uri
                    bookingViewModel.addImageUri(imageUri)
                }
            } else if (data?.data != null) {
                bookingViewModel.addImageUri(data.data!!)
            }
        }
    }

    private fun validateForm(): Boolean {
        val hasRequestDate = bookingViewModel.requestDate.value
            ?.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) == true
        val hasDescription = bookingViewModel.description.value?.isNotBlank() == true

        val isValid = hasRequestDate && hasDescription
        binding.btnNext.isEnabled = isValid

        if (isValid) {
            binding.btnNext.setBackgroundColor(android.graphics.Color.BLACK)
            binding.btnNext.setTextColor(android.graphics.Color.WHITE)
        } else {
            binding.btnNext.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            binding.btnNext.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        }

        bookingViewModel.updateDetailsCompletion(isValid)
        return isValid
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}