package com.example.garapro.ui.emergencies

import com.example.garapro.ui.emergencies.EmergencyViewModel
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
import com.example.garapro.utils.formatDistance
import com.example.garapro.utils.formatPrice

class EmergencyBottomSheet(
    private val context: android.content.Context,
    private val viewModel: EmergencyViewModel
) {

    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var garageAdapter: GarageAdapter
    private var onConfirmClickListener: (() -> Unit)? = null
    private var onDismissClickListener: (() -> Unit)? = null
    private var lastGarage: Garage? = null
    private var onChooseAnotherListener: (() -> Unit)? = null
    private var onTrackClickListener: (() -> Unit)? = null
    private var trackingView: View? = null
    private var suppressDismiss: Boolean = false
    private var onViewMapClickListener: (() -> Unit)? = null
    private var onCloseClickListener: (() -> Unit)? = null

    fun show(
        garages: List<Garage>,
        selectedGarage: Garage? = null,
        onConfirm: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        this.onConfirmClickListener = onConfirm
        this.onDismissClickListener = onDismiss
        
        Log.d("BottomSheet", "show() called with ${garages.size} garages")

        dismissSilently()
        
        // Ensure UI operations are on Main Thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
             android.os.Handler(Looper.getMainLooper()).post {
                 show(garages, selectedGarage, onConfirm, onDismiss)
             }
             return
        }

        try {
            bottomSheetDialog = BottomSheetDialog(context).apply {
                val view = createBottomSheetView(garages, selectedGarage)
                setContentView(view)
                
                setCancelable(false)
                behavior.isDraggable = true
                behavior.isHideable = false
                
                // Force expanded state and full height
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = 600 // Minimum height
                
                setOnDismissListener {
                    if (!suppressDismiss) onDismissClickListener?.invoke()
                }
                show()
            }
        } catch (e: Exception) {
            Log.e("BottomSheet", "Error showing bottom sheet", e)
        }
    }

    fun showWaitingForGarage(garage: Garage) {
        dismissSilently()
        bottomSheetDialog = null
        Log.d("EmergencyState", "🟢 WaitingForGarage triggered fo")
        val dialog = BottomSheetDialog(context as Activity)
        dialog.setContentView(createWaitingView(garage))
        dialog.setCancelable(false)
        
        dialog.setOnDismissListener {
            if (!suppressDismiss) onDismissClickListener?.invoke()
        }
        
        dialog.show()

        bottomSheetDialog = dialog
        lastGarage = garage
        Log.d("EmergencyState", "✅ showWaitingForGarage displayed for ${garage.name}")
    }

    fun dismiss() {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    fun dismissSilently() {
        suppressDismiss = true
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
        suppressDismiss = false
    }

    fun updateGarages(garages: List<Garage>) {
        if (!::garageAdapter.isInitialized) return
        garageAdapter.submitList(garages)
        updateTitle(garages.size)
    }

    fun updateSelectedGarage(garage: Garage?) {
        updateConfirmButton(garage)
    }

    fun showAccepted(garage: Garage, etaMinutes: Int?, arrived: Boolean = false) {
        dismissSilently()
        lastGarage = garage
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_accepted, null)
            setContentView(view)
            setCancelable(true)
            val tvName = view.findViewById<TextView>(R.id.tvGarageNameAccepted)
            val tvAddr = view.findViewById<TextView>(R.id.tvGarageAddressAccepted)
            val tvDistance = view.findViewById<TextView>(R.id.tvDistanceAccepted)
            val tvEta = view.findViewById<TextView>(R.id.tvEtaAccepted)
            val btnTrack = view.findViewById<Button>(R.id.btnTrackTech)
            val btnCall = view.findViewById<Button>(R.id.btnCallGarage)
            tvName.text = garage.name
            tvAddr.text = garage.address
            tvDistance.text = "Distance: ${garage.distance.formatDistance()} km"
        val etaText = if (arrived) {
            "Technician has arrived"
        } else {
            etaMinutes?.let { "ETA ~ $it min" } ?: "ETA ~ ${((garage.distance ?: 0.0) / 30.0 * 60).toInt()} min"
        }
            tvEta.text = etaText
            btnTrack.setOnClickListener {
                dismiss()
                onTrackClickListener?.invoke()
            }
            btnCall.setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                    intent.data = android.net.Uri.parse("tel:" + (garage.phone ?: ""))
                    (context as Activity).startActivity(intent)
                } catch (_: Exception) {}
            }
            show()
        }
    }

//    fun showArrived(garage: Garage, techName: String? = null, techPhone: String? = null) {
//        dismissSilently()
//        bottomSheetDialog = BottomSheetDialog(context).apply {
//            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_arrived, null)
//            setContentView(view)
//            setCancelable(true)
//            view.findViewById<TextView>(R.id.tvArrivedGarageName)?.text = garage.name
//            view.findViewById<TextView>(R.id.tvArrivedGarageAddress)?.text = garage.address
//            view.findViewById<TextView>(R.id.tvTechNameArrived)?.text = techName ?: "Technician"
//            view.findViewById<TextView>(R.id.tvTechPhoneArrived)?.text = techPhone ?: ""
//            view.findViewById<Button>(R.id.btnCloseArrived)?.setOnClickListener {
//                dismiss()
//                onCloseClickListener?.invoke()
//            }
//            show()
//        }
//    }
    fun showArrived(garage: Garage, techName: String? = null, techPhone: String? = null) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                showArrived(garage, techName, techPhone)
            }
            return
        }

        dismissSilently()
        
        try {
            bottomSheetDialog = BottomSheetDialog(context).apply {
                val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_arrived, null)
                setContentView(view)
                setCancelable(true)

                view.findViewById<TextView>(R.id.tvArrivedGarageName)?.text = garage.name
                view.findViewById<TextView>(R.id.tvArrivedGarageAddress)?.text = garage.address
                view.findViewById<TextView>(R.id.tvTechNameArrived)?.text = techName ?: "Technician"
                view.findViewById<TextView>(R.id.tvTechPhoneArrived)?.text = techPhone ?: ""
                view.findViewById<Button>(R.id.btnCloseArrived)?.setOnClickListener {
                    dismiss()
                    onCloseClickListener?.invoke()
                }
                show()
            }
        } catch (e: Exception) {
            Log.e("BottomSheet", "Error showing arrived dialog", e)
        }
    }


    fun setOnCloseClickListener(listener: (() -> Unit)?) {
        onCloseClickListener = listener
    }
    fun showTechnicianAssigned(garage: Garage, techName: String?, techPhone: String?) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_accepted, null)
            setContentView(view)
            setCancelable(false)
            val tvTitle = view.findViewById<TextView>(R.id.tvAcceptedTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvAcceptedSubtitle)
            val tvName = view.findViewById<TextView>(R.id.tvGarageNameAccepted)
            val tvAddr = view.findViewById<TextView>(R.id.tvGarageAddressAccepted)
            val tvDistance = view.findViewById<TextView>(R.id.tvDistanceAccepted)
            val tvEta = view.findViewById<TextView>(R.id.tvEtaAccepted)
            val btnTrack = view.findViewById<Button>(R.id.btnTrackTech)
            val btnCall = view.findViewById<Button>(R.id.btnCallGarage)

            tvTitle.text = "Technician Assigned!"
            tvSubtitle.text = "Technician ${techName ?: ""} is preparing to depart."
            tvName.text = garage.name
            tvAddr.text = garage.address
            tvDistance.text = "Distance: ${garage.distance.formatDistance()} km"
            
            // Show Tech Info in ETA field for now, or customize layout
            tvEta.text = "Tech: ${techName ?: "Unknown"}\nPhone: ${techPhone ?: "N/A"}"
            
            // HIDE Track Button initially
            btnTrack.visibility = View.GONE
            btnTrack.isEnabled = false
            btnTrack.text = "Waiting for departure..."
            
            // Call Garage Button
            btnCall.text = "Call Garage"
            btnCall.setOnClickListener { dialNumber(garage.phone) }
            
            show()
        }
    }

    fun showTechnicianEnRoute(
        garage: Garage, 
        techName: String?, 
        techPhone: String? = null, 
        distanceMeters: Double? = null, 
        etaMinutes: Int? = null
    ) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_tracking, null)
            setContentView(view)
            setCancelable(false)
            trackingView = view

            // Force expanded state
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true

            val tvTitle = view.findViewById<TextView>(R.id.tvTrackingTitle)
            val tvEta = view.findViewById<TextView>(R.id.tvTrackingEta)
            val tvDist = view.findViewById<TextView>(R.id.tvTrackingDistance)
            val tvTechName = view.findViewById<TextView>(R.id.tvTechNameTracking)
            val tvTechPhone = view.findViewById<TextView>(R.id.tvTechPhoneTracking)
            val btnCall = view.findViewById<Button>(R.id.btnCallTech)
            val btnViewMap = view.findViewById<Button>(R.id.btnViewMap) // Make sure this is VISIBLE

            tvTitle.text = "TECHNICIAN IS ARRIVING"
            
            // Update Real-time Data
            if (etaMinutes != null) {
                tvEta.text = "$etaMinutes min"
            } else {
                tvEta.text = "-- min"
            }
            
            if (distanceMeters != null) {
                tvDist.text = (distanceMeters / 1000.0).formatDistance() + " km"
            } else {
                tvDist.text = "-- km"
            }

            tvTechName.text = techName ?: "Technician"
            tvTechPhone.text = techPhone ?: "No phone number"
            
            if (techPhone.isNullOrBlank()) {
                btnCall.isEnabled = false
                btnCall.alpha = 0.5f
            } else {
                btnCall.setOnClickListener { dialNumber(techPhone) }
            }
            
            // Fix "View Map" visibility if needed (currently hidden in XML)
            // If user wants View Map button, we should unhide it or add it to XML
            // Based on user feedback: "nuts view map bi laasp" -> It implies it exists but is covered or small.
            // But in XML it is GONE. Let's fix XML first.

            show()
        }
    }

    fun showAcceptedWaitingForTechnician(garage: Garage) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_waiting_technician, null)
            setContentView(view)
            setCancelable(false)
            val tvTitle = view.findViewById<TextView>(R.id.tvAcceptedTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvAcceptedSubtitle)
            val tvName = view.findViewById<TextView>(R.id.tvGarageNameWaiting)
            val tvAddr = view.findViewById<TextView>(R.id.tvGarageAddressWaiting)
            val tvDistance = view.findViewById<TextView>(R.id.tvGarageDistanceWaiting)
            tvTitle.text = "Garage accepted your request"
            tvSubtitle.text = "Waiting for the garage to assign a technician"
            tvName.text = garage.name
            tvAddr.text = garage.address
            tvDistance.text = "Distance: ${garage.distance.formatDistance()} km"
            show()
        }
    }

    fun setOnTrackClickListener(listener: (() -> Unit)?) {
        onTrackClickListener = listener
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
        tvGarageInfo.text = "${garage.distance.formatDistance()} km "
        tvWaitingText.text = "Waiting for ${garage.name} to confirm..."

        // Setup nút hủy
        btnCancel.setOnClickListener {
            viewModel.cancelEmergencyRequest()
            dismiss() // This will trigger onDismiss listener if set
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
        setupCloseButton(view) // Ensure close button is set up
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

    private fun setupCloseButton(view: View) {
        val btnClose = view.findViewById<Button>(R.id.btnClose)
        btnClose?.setOnClickListener {
            // Allow explicit close button to dismiss
            suppressDismiss = true // Don't trigger onDismiss listener if it's just closing view
            bottomSheetDialog?.dismiss() 
            bottomSheetDialog = null
            suppressDismiss = false
            
            onDismissClickListener?.invoke()
        }
    }

    private fun updateTitle(garageCount: Int) {
        bottomSheetDialog?.findViewById<TextView>(R.id.tvSheetTitle)?.text =
            if (garageCount == 0) "No available garages"
            else "Choose a rescue garage ($garageCount results)"
    }

    private fun updateConfirmButton(garage: Garage?) {
        bottomSheetDialog?.findViewById<Button>(R.id.btnConfirm)?.apply {
            isEnabled = garage != null
            text = if (garage != null) "Confirm - ${garage.price.formatPrice()}"

            else "Select a garage to confirm"
        }
    }

    fun isShowing(): Boolean {
        return bottomSheetDialog?.isShowing == true
    }

    fun lastSelectedGarage(): Garage? {
        return lastGarage
    }

    fun setOnChooseAnotherListener(listener: (() -> Unit)?) {
        onChooseAnotherListener = listener
    }

    fun showRejected(garage: Garage, reason: String?) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_rejected, null)
            setContentView(view)
            setCancelable(true)
            val tvName = view.findViewById<TextView>(R.id.tvGarageNameRejected)
            val tvAddr = view.findViewById<TextView>(R.id.tvGarageAddressRejected)
            val tvReason = view.findViewById<TextView>(R.id.tvRejectedReason)
            val btnChoose = view.findViewById<Button>(R.id.btnChooseAnother)
            val btnCall = view.findViewById<Button>(R.id.btnCallGarageRejected)
            tvName.text = garage.name
            tvAddr.text = garage.address
            tvReason.text = reason ?: ""
            btnChoose.setOnClickListener {
                dismiss()
                onChooseAnotherListener?.invoke()
            }
            btnCall.setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                    intent.data = android.net.Uri.parse("tel:" + (garage.phone ?: ""))
                    (context as Activity).startActivity(intent)
                } catch (_: Exception) {}
            }
            show()
        }
    }

    fun showExpired(garage: Garage) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_expired, null)
            setContentView(view)
            setCancelable(false)

            // FIX: Force expand full height immediately
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true

            val tvGarageName = view.findViewById<TextView>(R.id.tvGarageNameExpired)
            val tvGarageAddress = view.findViewById<TextView>(R.id.tvGarageAddressExpired)
            val btnChoose = view.findViewById<Button>(R.id.btnChooseAnother)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelRequest)

            tvGarageName.text = garage.name
            tvGarageAddress.text = garage.address

            btnChoose.setOnClickListener {
                dismiss()
                onChooseAnotherListener?.invoke()
            }
            btnCancel.setOnClickListener {
                viewModel.cancelEmergencyRequest()
                dismiss()
                onDismissClickListener?.invoke()
            }
            show()
        }
    }

    fun showTracking(garage: Garage, etaMinutes: Int?) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_tracking, null)
            setContentView(view)
            setCancelable(true)
            trackingView = view
            val tvTitle = view.findViewById<TextView>(R.id.tvTrackingTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvTrackingSubtitle)
            val tvTechName = view.findViewById<TextView>(R.id.tvTechNameTracking)
            val tvTechPhone = view.findViewById<TextView>(R.id.tvTechPhoneTracking)
            val btnViewMap = view.findViewById<Button>(R.id.btnViewMap)
            val btnBack = view.findViewById<Button>(R.id.btnBackTracking)
            tvTitle.text = "On the way"
            tvSubtitle.text = etaMinutes?.let { "Technician en route, ETA ~ ${it} min" } ?: "Technician is en route, please track on the map."
            tvTechName.text = "Technician"
            tvTechPhone.text = ""
            
            btnViewMap.setOnClickListener { dismiss(); onViewMapClickListener?.invoke() }
            btnBack.visibility = View.GONE
            
            show()
        }
    }

    fun showTowing(garage: Garage, techName: String? = null, techPhone: String? = null) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_tracking, null)
            setContentView(view)
            setCancelable(true)
            trackingView = view
            
            val tvTitle = view.findViewById<TextView>(R.id.tvTrackingTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvTrackingSubtitle)
            val tvTechNameView = view.findViewById<TextView>(R.id.tvTechNameTracking)
            val tvTechPhoneView = view.findViewById<TextView>(R.id.tvTechPhoneTracking)
            val btnViewMap = view.findViewById<Button>(R.id.btnViewMap)
            val btnBack = view.findViewById<Button>(R.id.btnBackTracking)
            
            tvTitle.text = "Towing in progress"
            tvSubtitle.text = "Technician arrived, towing vehicle to ${garage.name}"
            tvTechNameView.text = techName ?: "Technician"
            tvTechPhoneView.text = techPhone ?: ""
            tvTechPhoneView.setOnClickListener { dialNumber(techPhone) }
            
            // Re-purpose the View Map button to be a "Got it" or similar if desired, 
            // or just hide it if we want the user to dismiss by dragging or back.
            // But per requirement, onDismiss triggers the next step, so a button to dismiss is good.
            btnViewMap.text = "View Map"
            btnViewMap.setOnClickListener { dismiss() }
            
            // Add SOS/Report Button
            val btnReport = view.findViewById<Button>(R.id.btnBackTracking) // Reusing existing button ID for now, or creating new
            if (btnReport != null) {
                btnReport.visibility = View.VISIBLE
                btnReport.text = "Report / SOS"
                btnReport.setBackgroundColor(android.graphics.Color.RED)
                btnReport.setTextColor(android.graphics.Color.WHITE)
                btnReport.setOnClickListener {
                     // Trigger Report Dialog
                     androidx.appcompat.app.AlertDialog.Builder(context)
                         .setTitle("Report Emergency Issue")
                         .setMessage("Are you in danger or is the technician behaving suspiciously?")
                         .setPositiveButton("Call Police (113)") { _, _ -> dialNumber("113") }
                         .setNegativeButton("Report to Platform") { _, _ -> 
                              // Call ViewModel to report issue
                              // For now just show a toast
                              android.widget.Toast.makeText(context, "Report sent to Support Center", android.widget.Toast.LENGTH_LONG).show()
                         }
                         .setNeutralButton("Cancel", null)
                         .show()
                }
            } else {
                 btnBack.visibility = View.GONE
            }
            
            setOnDismissListener {
                if (!suppressDismiss) onDismissClickListener?.invoke()
            }
            
            show()
        }
    }

    fun updateTrackingInfo(distanceMeters: Double?, etaMinutes: Int?) {
        if (bottomSheetDialog?.isShowing == true && trackingView != null) {
             val tvEta = trackingView?.findViewById<TextView>(R.id.tvTrackingEta)
             val tvDist = trackingView?.findViewById<TextView>(R.id.tvTrackingDistance)
             
             if (tvEta != null && etaMinutes != null) {
                 tvEta.text = "$etaMinutes min"
             }
             if (tvDist != null && distanceMeters != null) {
                 tvDist.text = (distanceMeters / 1000.0).formatDistance() + " km"
             }
        }
    }

    fun updateTrackingEta(minutes: Int) {
        val v = trackingView ?: return
        // Update new UI elements
        val tvEta = v.findViewById<TextView>(R.id.tvTrackingEta)
        if (tvEta != null) {
            tvEta.text = "$minutes min"
        } else {
            // Fallback for old layout if needed, though we replaced it
            v.findViewById<TextView>(R.id.tvTrackingSubtitle)?.text = "Technician en route, ETA ~ ${minutes} min"
        }
    }

    fun updateTrackingDistance(km: Double) {
        val v = trackingView ?: return
        val tvDist = v.findViewById<TextView>(R.id.tvTrackingDistance)
        tvDist?.text = String.format("%.1f km", km)
    }

    fun updateTrackingSkeleton(show: Boolean) {
        // No skeleton in new UI
    }

    fun updateTrackingTechnician(name: String?, phone: String?) {
        val v = trackingView ?: return
        v.findViewById<TextView>(R.id.tvTechNameTracking)?.text = name ?: "Technician"
        val phoneView = v.findViewById<TextView>(R.id.tvTechPhoneTracking)
        phoneView?.text = phone ?: ""
        
        // Setup Call Button
        v.findViewById<Button>(R.id.btnCallTech)?.setOnClickListener {
            dialNumber(phone)
        }
    }



    fun showCompleted(garage: Garage, techName: String?, techPhone: String?) {
        dismissSilently()
        bottomSheetDialog = BottomSheetDialog(context).apply {
            val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_emergency_completed, null)
            setContentView(view)
            setCancelable(false)

            // Force expanded state
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true

            val tvTitle = view.findViewById<TextView>(R.id.tvCompletedTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvCompletedSubtitle)
            val tvTechName = view.findViewById<TextView>(R.id.tvTechNameCompleted)
            val tvTechPhone = view.findViewById<TextView>(R.id.tvTechPhoneCompleted)
            val btnCall = view.findViewById<Button>(R.id.btnCallTechCompleted)
            val btnFinish = view.findViewById<Button>(R.id.btnFinish)

            tvTitle.text = "SERVICE COMPLETED"
            tvSubtitle.text = "Your vehicle has been towed to ${garage.name}."
            tvTechName.text = techName ?: "Technician"
            tvTechPhone.text = techPhone ?: ""
            
            btnCall.setOnClickListener { dialNumber(techPhone) }

            btnFinish.setOnClickListener {
                dismiss()
                onCloseClickListener?.invoke()
            }

            show()
        }
    }

    fun setOnDismissClickListener(listener: (() -> Unit)?) {
        onDismissClickListener = listener
    }

    fun setOnViewMapClickListener(listener: (() -> Unit)?) {
        onViewMapClickListener = listener
    }

    private fun dialNumber(raw: String?) {
        val number = raw?.filter { it.isDigit() || it == '+' } ?: ""
        if (number.isBlank()) {
            return
        }
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$number")
            (context as Activity).startActivity(intent)
        } catch (_: Exception) {}
    }
}
