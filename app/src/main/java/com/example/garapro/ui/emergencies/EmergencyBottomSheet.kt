package com.example.garapro.ui.emergencies

import EmergencyViewModel
import android.animation.ObjectAnimator
import android.app.Activity
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.emergencies.Garage
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.logging.Handler

class EmergencyBottomSheet(
    private val context: android.content.Context,
    private val viewModel: EmergencyViewModel
) {

    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var garageAdapter: GarageAdapter
    private var onConfirmClickListener: (() -> Unit)? = null
    private var onDismissClickListener: (() -> Unit)? = null

    fun show(
        garages: List<Garage>,
        selectedGarage: Garage? = null,
        onConfirm: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        this.onConfirmClickListener = onConfirm
        this.onDismissClickListener = onDismiss

        bottomSheetDialog = BottomSheetDialog(context).apply {
            setContentView(createBottomSheetView(garages, selectedGarage))
            setCancelable(true)
            setOnDismissListener {
                onDismissClickListener?.invoke()
            }
            show()
        }
    }

    fun showWaitingForGarage(garage: Garage) {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
        Log.d("EmergencyState", "🟢 WaitingForGarage triggered fo")
        android.os.Handler(Looper.getMainLooper()).post {
            val dialog = BottomSheetDialog(context as Activity)
            dialog.setContentView(createWaitingView(garage))
            dialog.setCancelable(false)
            dialog.show()

            bottomSheetDialog = dialog
            Log.d("EmergencyState", "✅ showWaitingForGarage displayed for ${garage.name}")
        }
    }

    fun dismiss() {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    fun updateGarages(garages: List<Garage>) {
        garageAdapter.submitList(garages)
        updateTitle(garages.size)
    }

    fun updateSelectedGarage(garage: Garage?) {
        updateConfirmButton(garage)
    }

    fun showConfirmationMode() {
        bottomSheetDialog?.findViewById<TextView>(R.id.tvSheetTitle)?.text = "Đã xác nhận"
        bottomSheetDialog?.findViewById<Button>(R.id.btnConfirm)?.apply {
            text = "Đóng"
            setOnClickListener {
                dismiss()
            }
        }
    }

    private fun createWaitingView(garage: Garage): View {
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_waiting_garage, null)

        val tvGarageName = view.findViewById<TextView>(R.id.tvGarageName)
        val tvGarageInfo = view.findViewById<TextView>(R.id.tvGarageInfo)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvWaitingText = view.findViewById<TextView>(R.id.tvWaitingText)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        // Hiển thị thông tin gara
        tvGarageName.text = garage.name
        tvGarageInfo.text = "${garage.distance.formatDistance()} km • ${garage.rating} ⭐"
        tvWaitingText.text = "Đang chờ ${garage.name} xác nhận..."

        // Setup nút hủy
        btnCancel.setOnClickListener {
            dismiss()
            onDismissClickListener?.invoke()
        }

        // Animation loading
        setupLoadingAnimation(progressBar)
        Log.d("EmergencyState", "🟢 WaitingForGarage done view")

        return view
    }
    private fun setupLoadingAnimation(progressBar: ProgressBar) {
        // Có thể thêm animation cho progress bar nếu muốn
        val rotateAnimation = ObjectAnimator.ofFloat(progressBar, "rotation", 0f, 360f)
        rotateAnimation.duration = 1000
        rotateAnimation.repeatCount = ObjectAnimator.INFINITE
        rotateAnimation.start()
    }
    private fun createBottomSheetView(garages: List<Garage>, selectedGarage: Garage?): View {
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_emergency_choose_garage, null)

        setupRecyclerView(view, garages)
        setupConfirmButton(view, selectedGarage)
//        setupCloseButton(view)
        updateTitle(garages.size)

        return view
    }

    private fun setupRecyclerView(view: View, garages: List<Garage>) {
        val rvGarages = view.findViewById<RecyclerView>(R.id.rvGarages)
        garageAdapter = GarageAdapter { garage ->
            viewModel.selectGarage(garage)
            updateConfirmButton(garage)
        }
        rvGarages.layoutManager = LinearLayoutManager(context)
        rvGarages.adapter = garageAdapter
        garageAdapter.submitList(garages)
    }

    private fun setupConfirmButton(view: View, selectedGarage: Garage?) {
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirm)
        updateConfirmButton(selectedGarage)
        btnConfirm.setOnClickListener {
            onConfirmClickListener?.invoke()
        }
    }

//    private fun setupCloseButton(view: View) {
//        val btnClose = view.findViewById<Button>(R.id.btnClose)
//        btnClose?.setOnClickListener {
//            dismiss()
//            onDismissClickListener?.invoke()
//        }
//    }

    private fun updateTitle(garageCount: Int) {
        bottomSheetDialog?.findViewById<TextView>(R.id.tvSheetTitle)?.text =
            if (garageCount == 0) "Không có gara nào khả dụng"
            else "Chọn gara cứu hộ ($garageCount kết quả)"
    }

    private fun updateConfirmButton(garage: Garage?) {
        bottomSheetDialog?.findViewById<Button>(R.id.btnConfirm)?.apply {
            isEnabled = garage != null
            text = if (garage != null) "Xác nhận - ${garage.price.formatPrice()}"
            else "Chọn gara để xác nhận"
        }
    }

    fun isShowing(): Boolean {
        return bottomSheetDialog?.isShowing == true
    }
}