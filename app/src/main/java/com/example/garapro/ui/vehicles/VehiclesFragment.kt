package com.example.garapro.ui.vehicles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast
import com.example.garapro.R

data class Vehicle(
    val id: String,
    var licensePlate: String,
    var brand: String,
    var model: String,
    var year: Int,
    var color: String,
    var vin: String = ""
)

class VehiclesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: VehicleAdapter
    private val vehicleList = mutableListOf<Vehicle>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vehicles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewVehicles)
        fabAdd = view.findViewById(R.id.fabAddVehicle)

        setupRecyclerView()
        loadVehicles()

        fabAdd.setOnClickListener {
            showAddVehicleDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = VehicleAdapter(
            vehicles = vehicleList,
            onItemClick = { vehicle -> showVehicleDetailDialog(vehicle) },
            onEditClick = { vehicle -> showEditVehicleDialog(vehicle) },
            onDeleteClick = { vehicle -> showDeleteConfirmation(vehicle) }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun loadVehicles() {
        // Dữ liệu mẫu - thay bằng API call thực tế
        vehicleList.addAll(listOf(
            Vehicle("1", "29A-12345", "Toyota", "Camry", 2020, "Trắng", "JT2BF28K0X0123456"),
            Vehicle("2", "30B-67890", "Honda", "Civic", 2019, "Đen", "19XFC2F59KE012345"),
            Vehicle("3", "51F-11111", "Mazda", "CX-5", 2021, "Đỏ", "JM3KFBCM5M0123456")
        ))
        adapter.notifyDataSetChanged()
    }

    private fun showAddVehicleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_edit_vehicle, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Thêm phương tiện mới")
            .setView(dialogView)
            .setPositiveButton("Thêm") { _, _ ->
                val licensePlate = dialogView.findViewById<TextInputEditText>(R.id.etLicensePlate).text.toString()
                val brand = dialogView.findViewById<TextInputEditText>(R.id.etBrand).text.toString()
                val model = dialogView.findViewById<TextInputEditText>(R.id.etModel).text.toString()
                val year = dialogView.findViewById<TextInputEditText>(R.id.etYear).text.toString().toIntOrNull() ?: 0
                val color = dialogView.findViewById<TextInputEditText>(R.id.etColor).text.toString()
                val vin = dialogView.findViewById<TextInputEditText>(R.id.etVin).text.toString()

                if (validateVehicleInput(licensePlate, brand, model, year)) {
                    addVehicle(licensePlate, brand, model, year, color, vin)
                }
            }
            .setNegativeButton("Hủy", null)
            .create()

        dialog.show()
    }

    private fun showEditVehicleDialog(vehicle: Vehicle) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_edit_vehicle, null)

        // Điền dữ liệu hiện tại
        dialogView.findViewById<TextInputEditText>(R.id.etLicensePlate).setText(vehicle.licensePlate)
        dialogView.findViewById<TextInputEditText>(R.id.etBrand).setText(vehicle.brand)
        dialogView.findViewById<TextInputEditText>(R.id.etModel).setText(vehicle.model)
        dialogView.findViewById<TextInputEditText>(R.id.etYear).setText(vehicle.year.toString())
        dialogView.findViewById<TextInputEditText>(R.id.etColor).setText(vehicle.color)
        dialogView.findViewById<TextInputEditText>(R.id.etVin).setText(vehicle.vin)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chỉnh sửa phương tiện")
            .setView(dialogView)
            .setPositiveButton("Lưu") { _, _ ->
                vehicle.licensePlate = dialogView.findViewById<TextInputEditText>(R.id.etLicensePlate).text.toString()
                vehicle.brand = dialogView.findViewById<TextInputEditText>(R.id.etBrand).text.toString()
                vehicle.model = dialogView.findViewById<TextInputEditText>(R.id.etModel).text.toString()
                vehicle.year = dialogView.findViewById<TextInputEditText>(R.id.etYear).text.toString().toIntOrNull() ?: 0
                vehicle.color = dialogView.findViewById<TextInputEditText>(R.id.etColor).text.toString()
                vehicle.vin = dialogView.findViewById<TextInputEditText>(R.id.etVin).text.toString()

                adapter.notifyDataSetChanged()
                Toast.makeText(context, "Đã cập nhật thông tin xe", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .create()

        dialog.show()
    }

    private fun showVehicleDetailDialog(vehicle: Vehicle) {
        val message = """
            Biển số: ${vehicle.licensePlate}
            Hãng xe: ${vehicle.brand}
            Dòng xe: ${vehicle.model}
            Năm sản xuất: ${vehicle.year}
            Màu sắc: ${vehicle.color}
            Số VIN: ${vehicle.vin}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chi tiết phương tiện")
            .setMessage(message)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Chỉnh sửa") { _, _ ->
                showEditVehicleDialog(vehicle)
            }
            .show()
    }

    private fun showDeleteConfirmation(vehicle: Vehicle) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa xe ${vehicle.licensePlate}?")
            .setPositiveButton("Xóa") { _, _ ->
                vehicleList.remove(vehicle)
                adapter.notifyDataSetChanged()
                Toast.makeText(context, "Đã xóa phương tiện", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun addVehicle(licensePlate: String, brand: String, model: String, year: Int, color: String, vin: String) {
        val newVehicle = Vehicle(
            id = System.currentTimeMillis().toString(),
            licensePlate = licensePlate,
            brand = brand,
            model = model,
            year = year,
            color = color,
            vin = vin
        )

        vehicleList.add(0, newVehicle)
        adapter.notifyItemInserted(0)
        recyclerView.smoothScrollToPosition(0)
        Toast.makeText(context, "Đã thêm phương tiện mới", Toast.LENGTH_SHORT).show()
    }

    private fun validateVehicleInput(licensePlate: String, brand: String, model: String, year: Int): Boolean {
        return when {
            licensePlate.isBlank() -> {
                Toast.makeText(context, "Vui lòng nhập biển số xe", Toast.LENGTH_SHORT).show()
                false
            }
            brand.isBlank() -> {
                Toast.makeText(context, "Vui lòng nhập hãng xe", Toast.LENGTH_SHORT).show()
                false
            }
            model.isBlank() -> {
                Toast.makeText(context, "Vui lòng nhập dòng xe", Toast.LENGTH_SHORT).show()
                false
            }
            year < 1900 || year > 2025 -> {
                Toast.makeText(context, "Năm sản xuất không hợp lệ", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }
}
