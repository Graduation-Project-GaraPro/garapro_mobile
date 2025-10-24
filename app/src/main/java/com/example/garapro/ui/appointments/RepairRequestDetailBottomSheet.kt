package com.example.garapro.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.RepairRequestDetail
import com.example.garapro.data.model.repairRequest.RequestServiceDetail
import com.example.garapro.databinding.BottomSheetRepairRequestDetailBinding
import com.example.garapro.utils.MoneyUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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

        // Basic info
        tvRequestId?.text = "ID: ${detail.repairRequestID.take(8)}..." // Rút gọn ID cho đẹp
        tvDescription?.text = detail.description
        tvRequestDate?.text = formatDate(detail.requestDate)
        tvStatus?.text = getStatusText(detail.status)
        tvStatus?.setBackgroundResource(getStatusBackground(detail.status))
        tvEstimatedCost?.text = "${MoneyUtils.formatVietnameseCurrency(detail.estimatedCost)}"

        // Vehicle info
        tvVehicle?.text = "${detail.vehicle.brandName ?: ""} ${detail.vehicle.modelName ?: ""}"
        tvLicensePlate?.text = detail.vehicle.licensePlate
        tvVin?.text = detail.vehicle.vin
        tvYear?.text = detail.vehicle.year.toString()
        tvOdometer?.text = "${detail.vehicle.odometer} km"

        // Services and parts
        if (rvServices != null) {
            setupServicesList(rvServices, detail.requestServices)
        }

        // Total calculation
        val totalCost = detail.requestServices.sumOf { it.price } +
                detail.requestServices.flatMap { it.parts }.sumOf { it.price}
        tvTotalCost?.text = "${MoneyUtils.formatVietnameseCurrency(totalCost)}"
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
            0 -> "PENDING"
            1 -> "ACCEPTED"
            2 -> "ARRIVED"
            3 -> "CANCELLED"
            else -> "UNKNOWN"
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