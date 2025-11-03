package com.example.garapro.ui.repairRequest

import android.app.ProgressDialog
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.example.garapro.R

abstract class BaseBookingFragment : Fragment() {

    // Lấy ViewModel từ Activity
    protected val bookingViewModel: BookingViewModel by lazy {
        (requireActivity() as BookingActivity).viewModel
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Set context cho ViewModel
        bookingViewModel.setContext(requireContext())
    }

    protected fun findNavController(): NavController {
        return Navigation.findNavController(requireView())
    }

    protected fun showNextFragment(destinationId: Int) {
        findNavController().navigate(destinationId)
    }

    protected fun showPreviousFragment() {
        findNavController().popBackStack()
    }

    protected fun showLoading() {
        (requireActivity() as BookingActivity).showLoading()
    }

    protected fun hideLoading() {
        (requireActivity() as BookingActivity).hideLoading()
    }

    protected fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    protected fun setupBaseObservers() {
        bookingViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showLoading()
            } else {
                hideLoading()
            }
        }

        bookingViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                showError(it)
                bookingViewModel.clearErrorMessage()
            }
        }
    }

}