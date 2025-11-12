package com.example.garapro.data.repository

import com.example.garapro.data.model.payments.CreatePaymentRequest
import com.example.garapro.data.model.payments.CreatePaymentResponse
import com.example.garapro.data.model.payments.PaymentStatus
import com.example.garapro.data.model.payments.PaymentStatusDto
import com.example.garapro.data.remote.PaymentService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

class PaymentRepository(
    private val api: PaymentService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private val FINAL_STATUSES = setOf(
            PaymentStatus.Paid,
            PaymentStatus.Cancelled,
            PaymentStatus.Failed
        )

        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val DEFAULT_INTERVAL_MS = 2_000L
        private const val DEFAULT_MAX_RETRIES = 3
    }

    suspend fun createPaymentLink(req: CreatePaymentRequest): Result<CreatePaymentResponse> =
        withContext(dispatcher) {
            try {
                Timber.d("Creating payment link for order: ${req.orderCode ?: "new"}")
                val response = api.createLink(req)
                Timber.d("Payment link created successfully: ${response.orderCode}")
                Result.success(response)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create payment link")
                Result.failure(e)
            }
        }

    suspend fun getStatus(orderCode: Long): Result<PaymentStatusDto> =
        withContext(dispatcher) {
            try {
                Timber.d("Fetching payment status for order: $orderCode")
                val status = api.getStatus(orderCode)
                Timber.d("Payment status for $orderCode: ${status.status}")
                Result.success(status)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get payment status for order: $orderCode")
                Result.failure(e)
            }
        }

    /**
     * Poll payment status until final state or timeout
     */
    suspend fun pollStatus(
        orderCode: Long,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        maxRetries: Int = DEFAULT_MAX_RETRIES
    ): Result<PaymentStatusDto> = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()
        var consecutiveErrors = 0
        var lastKnownStatus: PaymentStatusDto? = null
        var pollCount = 0

        Timber.d("Starting polling for order: $orderCode (timeout: ${timeoutMs}ms, interval: ${intervalMs}ms)")

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            pollCount++
            Timber.d("Poll attempt #$pollCount for order: $orderCode")

            // Sử dụng fold hoặc getOrNull/exceptionOrNull
            val result = getStatus(orderCode)

            result.fold(
                onSuccess = { status ->
                    consecutiveErrors = 0 // Reset error count on success
                    lastKnownStatus = status

                    Timber.d("Poll #$pollCount - Status: ${status.status}")

                    if (status.status in FINAL_STATUSES) {
                        Timber.d("Final status reached: ${status.status} after $pollCount polls")
                        return@withContext Result.success(status)
                    }

                    // Continue polling for non-final statuses
                    Timber.d("Non-final status ${status.status}, continuing polling...")
                },
                onFailure = { error ->
                    consecutiveErrors++
                    lastKnownStatus = null

                    Timber.w("Poll #$pollCount failed (consecutive errors: $consecutiveErrors): ${error.message}")

                    // If we hit max consecutive errors, fail fast
                    if (consecutiveErrors >= maxRetries) {
                        Timber.e("Max retries ($maxRetries) exceeded for order: $orderCode")
                        return@withContext Result.failure(
                            Exception("Too many consecutive errors: ${error.message ?: "Unknown error"}")
                        )
                    }
                }
            )

            delay(intervalMs)
        }

        // Timeout handling
        Timber.w("Polling timeout for order: $orderCode after ${System.currentTimeMillis() - startTime}ms")

        // Return last known status if available, otherwise timeout error
        return@withContext lastKnownStatus?.let { status ->
            Timber.d("Returning last known status on timeout: ${status.status}")
            Result.success(status)
        } ?: Result.failure(
            Exception("Timeout waiting for payment status after ${System.currentTimeMillis() - startTime}ms")
        )
    }

    /**
     * Quick status check without polling - for one-time verification
     */
    suspend fun quickStatusCheck(orderCode: Long): Result<PaymentStatusDto> =
        withContext(dispatcher) {
            try {
                Timber.d("Quick status check for order: $orderCode")
                val status = api.getStatus(orderCode)
                Result.success(status)
            } catch (e: Exception) {
                Timber.e(e, "Quick status check failed for order: $orderCode")
                Result.failure(e)
            }
        }

    /**
     * Enhanced polling with progress callback
     */
    suspend fun pollStatusWithProgress(
        orderCode: Long,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        onProgress: ((PaymentStatusDto?) -> Unit)? = null
    ): Result<PaymentStatusDto> = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()
        var consecutiveErrors = 0
        var lastKnownStatus: PaymentStatusDto? = null

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val result = getStatus(orderCode)

            result.fold(
                onSuccess = { status ->
                    consecutiveErrors = 0
                    lastKnownStatus = status

                    // Notify progress
                    onProgress?.invoke(status)

                    if (status.status in FINAL_STATUSES) {
                        return@withContext Result.success(status)
                    }
                },
                onFailure = { error ->
                    consecutiveErrors++
                    lastKnownStatus = null

                    // Notify progress even on error
                    onProgress?.invoke(null)

                    if (consecutiveErrors >= maxRetries) {
                        return@withContext Result.failure(error)
                    }
                }
            )

            delay(intervalMs)
        }

        // Timeout handling
        lastKnownStatus?.let { status ->
            onProgress?.invoke(status)
            Result.success(status)
        } ?: Result.failure(
            Exception("Polling timeout after ${System.currentTimeMillis() - startTime}ms")
        )
    }
}