package com.example.garapro.ui.TechEmergencies

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import kotlin.jvm.java

class ReportsFragment : Fragment() {
    private lateinit var viewModel: TechEmergenciesViewModel
    private lateinit var adapter: EmergencyForTechnicianAdapter

    private val mapLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val data = result.data ?: return@registerForActivityResult

            val completed = data.getBooleanExtra("completed", false)
            val cancelled = data.getBooleanExtra("cancelled", false)

            if (completed || cancelled) {
                viewModel.loadData()
            }
        }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reports, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[TechEmergenciesViewModel::class.java]

        val rvEmergencies = view.findViewById<RecyclerView>(R.id.rvEmergencies)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val emptyState = view.findViewById<View>(R.id.layoutEmptyState)

        adapter = EmergencyForTechnicianAdapter { item ->
            viewModel.loadDetail(item.emergencyRequestId)
        }
        rvEmergencies.adapter = adapter

        // Observers
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.detail.observe(viewLifecycleOwner) { detail ->
            if (detail != null) {
                EmergencyDetailBottomSheet(detail)
                    .show(parentFragmentManager, "EmergencyDetailBottomSheet")
            }
        }
        viewModel.current.observe(viewLifecycleOwner) { current ->
            if (current != null) {

                val ctx = requireContext()
                val intent = Intent(ctx, MapDirectionDemoActivity::class.java).apply {
                    putExtra("emergencyId", current.emergencyRequestId)
                    putExtra("latitude", current.latitude)
                    putExtra("longitude", current.longitude)
                    putExtra("branchName", current.branchName)
                    putExtra("branchLatitude", current.branchLatitude)
                    putExtra("branchLongitude", current.branchLongitude)
                    putExtra("status", current.status)
                    putExtra("customerPhone", current.phoneNumber)
                }
                mapLauncher.launch(intent)


            }
        }

        viewModel.list.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isNullOrEmpty()) {
                rvEmergencies.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                rvEmergencies.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        // Gọi load
        viewModel.loadData()
    }
}