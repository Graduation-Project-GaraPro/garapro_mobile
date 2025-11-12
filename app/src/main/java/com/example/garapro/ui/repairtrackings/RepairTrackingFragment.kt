package com.example.garapro.ui.repairtrackings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.garapro.databinding.FragmentRepairTrackingBinding

class RepairTrackingFragment : Fragment() {

    private var _binding: FragmentRepairTrackingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepairTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Khởi tạo view và xử lý logic ở đây

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}