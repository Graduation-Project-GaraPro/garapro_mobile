package com.example.garapro.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.RepairRequestDetail
import com.example.garapro.data.model.repairRequest.RequestServiceDetail
import com.example.garapro.databinding.BottomSheetRepairRequestDetailBinding
import com.example.garapro.utils.MoneyUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale

class RepairRequestDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_REPAIR_REQUEST_DETAIL = "repair_request_detail"

        fun newInstance(detail: RepairRequestDetail): RepairRequestDetailBottomSheet {
            val args = Bundle().apply {
                putSerializable(ARG_REPAIR_REQUEST_DETAIL, detail)
            }
            return RepairRequestDetailBottomSheet().apply {
                arguments = args
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_repair_request_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val detail = arguments?.getSerializable(ARG_REPAIR_REQUEST_DETAIL) as? RepairRequestDetail
        detail?.let { setupUI(it) }
    }

    override fun onStart() {
        super.onStart()

        // Set bottom sheet behavior đơn giản
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet!!)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    private fun setupUI(detail: RepairRequestDetail) {
        // Ánh xạ views
        val tvRequestId = view?.findViewById<TextView>(R.id.tvRequestId)
        val tvDescription = view?.findViewById<TextView>(R.id.tvDescription)
        val tvRequestDate = view?.findViewById<TextView>(R.id.tvRequestDate)
        val tvStatus = view?.findViewById<TextView>(R.id.tvStatus)
        val tvEstimatedCost = view?.findViewById<TextView>(R.id.tvEstimatedCost)
        val tvVehicle = view?.findViewById<TextView>(R.id.tvVehicle)
        val tvLicensePlate = view?.findViewById<TextView>(R.id.tvLicensePlate)
        val tvVin = view?.findViewById<TextView>(R.id.tvVin)
        val tvYear = view?.findViewById<TextView>(R.id.tvYear)
        val tvOdometer = view?.findViewById<TextView>(R.id.tvOdometer)
        val rvServices = view?.findViewById<RecyclerView>(R.id.rvServices)
        val tvTotalCost = view?.findViewById<TextView>(R.id.tvTotalCost)

        // Image views
        val image1 = view?.findViewById<ShapeableImageView>(R.id.ivImage1)
        val image2 = view?.findViewById<ShapeableImageView>(R.id.ivImage2)
        val image3 = view?.findViewById<ShapeableImageView>(R.id.ivImage3)
        val image4 = view?.findViewById<ShapeableImageView>(R.id.ivImage4)

        // Basic info
        tvRequestId?.text = "ID: ${detail.repairRequestID.take(8)}..." // Rút gọn ID cho đẹp
        tvDescription?.text = detail.description
        tvRequestDate?.text = formatDate(detail.requestDate)
        tvStatus?.text = getStatusText(detail.status)
        tvStatus?.setBackgroundResource(getStatusBackground(detail.status))
        tvEstimatedCost?.visibility = View.GONE

        // Vehicle info
        tvVehicle?.text = "${detail.vehicle.brandName ?: ""} ${detail.vehicle.modelName ?: ""}"
        tvLicensePlate?.text = detail.vehicle.licensePlate
        tvVin?.text = detail.vehicle.vin
        tvYear?.text = detail.vehicle.year.toString()
        tvOdometer?.text = "${detail.vehicle.odometer} km"

        // Load images
        loadImages(detail.imageUrls, image1, image2, image3, image4)

        // Services and parts
        if (rvServices != null) {
            setupServicesList(rvServices, detail.requestServices)
        }

        // Total calculation
        val totalCost = detail.requestServices.sumOf { it.price }
        tvTotalCost?.text = "${MoneyUtils.formatVietnameseCurrency(totalCost)}"
    }

    private fun loadImages(
        imageUrls: List<String>?,
        image1: ShapeableImageView?,
        image2: ShapeableImageView?,
        image3: ShapeableImageView?,
        image4: ShapeableImageView?
    ) {
        val imageViews = listOf(image1, image2, image3, image4)

        // Ẩn tất cả image views trước
        imageViews.forEach { it?.visibility = View.GONE }

        // Hiển thị ảnh nếu có URL
        imageUrls?.take(4)?.forEachIndexed { index, imageUrl ->
            imageViews.getOrNull(index)?.let { imageView ->
                imageView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_camera) // Thêm placeholder nếu cần
                    .error(R.drawable.ic_camera) // Thêm error image nếu cần
                    .centerCrop()
                    .into(imageView)
            }
        }

        // Xử lý trường hợp có nhiều hơn 4 ảnh
        if (imageUrls != null && imageUrls.size > 4) {
            image4?.let {
                // Có thể thêm badge hoặc indicator cho ảnh cuối
                it.setOnClickListener {
                    // Mở fullscreen image viewer với tất cả ảnh
                    showFullScreenImages(imageUrls)
                }
            }
        }
    }

    private fun showFullScreenImages(imageUrls: List<String>) {
        // Implement fullscreen image viewer ở đây
        // Có thể sử dụng DialogFragment hoặc Activity mới
    }

    private fun setupServicesList(recyclerView: RecyclerView, services: List<RequestServiceDetail>) {
        val adapter = RepairRequestServicesAdapter(services)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun getStatusText(status: Int): String {
        return when (status) {
            0 -> "Đang chờ xử lý"
            1 -> "Đã chấp nhận"
            2 -> "Đã đến nơi"
            3 -> "Đã hủy"
            else -> "Không xác định"
        }
    }

    private fun getStatusBackground(status: Int): Int {
        return when (status) {
            0 -> R.drawable.bg_status_pending
            1 -> R.drawable.bg_status_accept
            2 -> R.drawable.bg_status_arrived
            3 -> R.drawable.bg_status_cancelled
            else -> R.drawable.bg_status_pending
        }
    }
}