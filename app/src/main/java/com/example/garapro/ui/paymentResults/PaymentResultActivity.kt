package com.example.garapro.ui.paymentResults

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.garapro.MainActivity
import com.example.garapro.R
import com.example.garapro.data.model.payments.PaymentStatus
import com.example.garapro.data.model.payments.PaymentStatusDto
import com.example.garapro.ui.payments.PaymentViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PaymentResultActivity : AppCompatActivity() {

    private val viewModel: PaymentViewModel by viewModels { PaymentViewModelFactory() }

    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnClose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_result)

        onBackPressedDispatcher.addCallback(this) {
            val i = Intent(this@PaymentResultActivity, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            finish()
        }
        initViews()
        handleIntentData()
        setupObservers()
        setupClickListeners()
    }


    private fun initViews() {
        tvResult = findViewById(R.id.tvResult)
        tvStatus = findViewById(R.id.tvStatus)
        btnRetry = findViewById(R.id.btnRetry)
        btnClose = findViewById(R.id.btnClose)
    }

    private fun handleIntentData() {
        val data: Uri? = intent?.data
        if (data == null) {
            finishWithMessage("Không nhận được dữ liệu thanh toán")
            return
        }

        val isSuccessDeeplink = data.pathSegments.firstOrNull()?.equals("success", true) == true
        val orderCode = data.getQueryParameter("orderCode")?.toLongOrNull()
        val fallbackMsg = data.getQueryParameter("message") ?: data.getQueryParameter("reason")

        if (orderCode == null) {
            showFallbackResult(isSuccessDeeplink)
            return
        }

        showLoadingState()
        startPolling(orderCode)
    }

    private fun setupObservers() {
        // Observe polling state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pollingState.collectLatest { result ->
                    result?.let { handlePollingResult(it) }
                }
            }
        }

        // Observe loading state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collectLatest { isLoading ->
                    btnRetry.isEnabled = !isLoading
                }
            }
        }
    }

    private fun setupClickListeners() {
        btnRetry.setOnClickListener {
            val orderCode = intent?.data?.getQueryParameter("orderCode")?.toLongOrNull()
            if (orderCode != null) {
                showLoadingState()
                viewModel.startPollingStatus(orderCode)
            }
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun startPolling(orderCode: Long) {
        viewModel.startPollingStatus(orderCode)
    }

    private fun handlePollingResult(result: Result<PaymentStatusDto>) {
        result.fold(
            onSuccess = { status ->
                showResult(status)
            },
            onFailure = { error ->
                // Fallback dựa trên deeplink
                val data = intent.data
                val isSuccessDeeplink = data?.pathSegments?.firstOrNull()?.equals("success", true) == true
                showFallbackResult(isSuccessDeeplink)
            }
        )
    }

    private fun showLoadingState() {
        findViewById<ImageView>(R.id.paymentIcon).visibility = View.GONE
        findViewById<ProgressBar>(R.id.progress).visibility = View.VISIBLE
        tvResult.text = "Processing Payment..."
        tvStatus.text = "Please wait while we verify your transaction"
        findViewById<MaterialButton>(R.id.btnRetry).visibility = View.GONE
        findViewById<MaterialButton>(R.id.btnClose).visibility = View.GONE
    }

    private fun showResult(status: PaymentStatusDto) {
        findViewById<ProgressBar>(R.id.progress).visibility = View.GONE
        findViewById<ImageView>(R.id.paymentIcon).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btnClose).visibility = View.VISIBLE

        when (status.status) {
            PaymentStatus.Paid -> showSuccessState()
            PaymentStatus.Cancelled -> showCancelledState()
            PaymentStatus.Failed -> showFailedState()
            PaymentStatus.Unpaid -> showPendingState()
        }
    }

    private fun showSuccessState() {
        val paymentIcon = findViewById<ImageView>(R.id.paymentIcon)
        paymentIcon.setImageResource(R.drawable.ic_payment_success)
        paymentIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))

        tvResult.text = "Payment Successful"
        tvStatus.text = "Your payment has been processed successfully"
        findViewById<MaterialButton>(R.id.btnRetry).visibility = View.GONE

        setResult(RESULT_OK)
    }

    private fun showFailedState() {
        val paymentIcon = findViewById<ImageView>(R.id.paymentIcon)
        paymentIcon.setImageResource(R.drawable.ic_payment_failed)
        paymentIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))

        tvResult.text = "Payment Failed"
        tvStatus.text = "We couldn't process your payment. Please try again."
        findViewById<MaterialButton>(R.id.btnRetry).visibility = View.VISIBLE
    }

    private fun showCancelledState() {
        val paymentIcon = findViewById<ImageView>(R.id.paymentIcon)
        paymentIcon.setImageResource(R.drawable.ic_payment_cancelled)
        paymentIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        tvResult.text = "Payment Cancelled"
        tvStatus.text = "Your payment was cancelled. You can try again anytime."
        findViewById<MaterialButton>(R.id.btnRetry).visibility = View.VISIBLE
    }

    private fun showPendingState() {
        val paymentIcon = findViewById<ImageView>(R.id.paymentIcon)
        paymentIcon.setImageResource(R.drawable.ic_payment_pending)
        paymentIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        tvResult.text = "Payment Pending"
        tvStatus.text = "Your payment is being processed. Please wait for confirmation."
        findViewById<MaterialButton>(R.id.btnRetry).visibility = View.VISIBLE
    }

    private fun showFallbackResult(isSuccessDeeplink: Boolean) {
        findViewById<ProgressBar>(R.id.progress).visibility = View.GONE
        findViewById<ImageView>(R.id.paymentIcon).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btnClose).visibility = View.VISIBLE

        if (isSuccessDeeplink) {
            showSuccessState()
        } else {
            showFailedState()
        }
    }






    private fun finishWithMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }
}