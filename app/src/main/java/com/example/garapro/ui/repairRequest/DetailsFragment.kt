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

class DetailsFragment : BaseBookingFragment() {

    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var imageAdapter: ImageAdapter

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
            validateForm()
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

                // Sau khi chọn ngày, mở hộp thoại chọn giờ
                val timePicker = TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        // Nếu chọn hôm nay, kiểm tra giờ có hợp lệ không
                        if (isToday(calendar, now) && (hour < now.get(Calendar.HOUR_OF_DAY) ||
                                    (hour == now.get(Calendar.HOUR_OF_DAY) && minute < now.get(Calendar.MINUTE)))
                        ) {
                            Toast.makeText(requireContext(), "Cannot select past time!", Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }

                        // Lưu giờ hợp lệ
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)

                        // Format yyyy-MM-dd HH:mm
                        val selectedDateTime = String.format(
                            "%04d-%02d-%02d %02d:%02d",
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH),
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE)
                        )

                        // Cập nhật UI và ViewModel
                        bookingViewModel.setRequestDate(selectedDateTime)
                        binding.etRequestDate.setText(selectedDateTime)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                )

                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Giới hạn: không cho chọn ngày trong quá khứ
        datePicker.datePicker.minDate = now.timeInMillis
        datePicker.show()
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
        val isValid = bookingViewModel.requestDate.value?.isNotBlank() == true &&
                bookingViewModel.description.value?.isNotBlank() == true

        binding.btnNext.isEnabled = isValid
        bookingViewModel.updateDetailsCompletion(isValid)
        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}