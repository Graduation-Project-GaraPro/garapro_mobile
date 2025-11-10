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

        // Set bottom sheet behavior
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet!!)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    private fun setupUI(detail: RepairRequestDetail) {
        // Map views
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

        // Basic info with null safety
        tvRequestId?.text = "ID: ${detail.repairRequestID.take(8)}..." // Shorten ID for display
        tvDescription?.text = detail.description ?: "No description provided"
        tvRequestDate?.text = formatDate(detail.requestDate)
        tvStatus?.text = getStatusText(detail.status)
        tvStatus?.setBackgroundResource(getStatusBackground(detail.status))
        tvEstimatedCost?.visibility = View.GONE

        // Vehicle info with null safety
        val brandName = detail.vehicle.brandName ?: "Unknown Brand"
        val modelName = detail.vehicle.modelName ?: "Unknown Model"
        tvVehicle?.text = "$brandName $modelName"
        tvLicensePlate?.text = detail.vehicle.licensePlate ?: "Not provided"
        tvVin?.text = detail.vehicle.vin ?: "Not provided"
        tvYear?.text = detail.vehicle.year?.toString() ?: "Not provided"
        tvOdometer?.text = if (detail.vehicle.odometer != null) "${detail.vehicle.odometer} km" else "Not provided"

        // Load images
        loadImages(detail.imageUrls, image1, image2, image3, image4)

        // Services and parts
        if (rvServices != null) {
            setupServicesList(rvServices, detail.requestServices)
        }

        // Total calculation with null safety
        val totalCost = detail.requestServices?.sumOf { it.price ?: 0.0 } ?: 0.0
        tvTotalCost?.text = MoneyUtils.formatVietnameseCurrency(totalCost)
    }

    private fun loadImages(
        imageUrls: List<String>?,
        image1: ShapeableImageView?,
        image2: ShapeableImageView?,
        image3: ShapeableImageView?,
        image4: ShapeableImageView?
    ) {
        val imageViews = listOf(image1, image2, image3, image4)

        // Hide all image views first
        imageViews.forEach { it?.visibility = View.GONE }

        // Show images if URLs are available
        imageUrls?.take(4)?.forEachIndexed { index, imageUrl ->
            imageViews.getOrNull(index)?.let { imageView ->
                imageView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_camera) // Add placeholder if needed
                    .error(R.drawable.ic_camera) // Add error image if needed
                    .centerCrop()
                    .into(imageView)
            }
        }

        // Handle case when there are more than 4 images
        if (imageUrls != null && imageUrls.size > 4) {
            image4?.let {
                // Could add badge or indicator for the last image
                it.setOnClickListener {
                    // Open fullscreen image viewer with all images
                    showFullScreenImages(imageUrls)
                }
            }
        }
    }

    private fun showFullScreenImages(imageUrls: List<String>) {
        // Implement fullscreen image viewer here
        // Could use DialogFragment or new Activity
    }

    private fun setupServicesList(recyclerView: RecyclerView, services: List<RequestServiceDetail>?) {
        val serviceList = services ?: emptyList()
        val adapter = RepairRequestServicesAdapter(serviceList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "Date not available"

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: "Invalid date"
        } catch (e: Exception) {
            "Date format error"
        }
    }

    private fun getStatusText(status: Int?): String {
        return when (status) {
            0 -> "Pending"
            1 -> "Accepted"
            2 -> "Arrived"
            3 -> "Cancelled"
            else -> "Unknown"
        }
    }

    private fun getStatusBackground(status: Int?): Int {
        return when (status) {
            0 -> R.drawable.bg_status_pending
            1 -> R.drawable.bg_status_accept
            2 -> R.drawable.bg_status_arrived
            3 -> R.drawable.bg_status_cancelled
            else -> R.drawable.bg_status_pending
        }
    }
}