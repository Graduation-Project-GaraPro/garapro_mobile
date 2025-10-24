package com.example.garapro.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.material3.Button
import androidx.fragment.app.Fragment
import com.example.garapro.R
import com.example.garapro.ui.repairRequest.BookingActivity

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val btnGoToBooking = view.findViewById<Button>(R.id.btnGoToBooking)
        btnGoToBooking.setOnClickListener {
            val intent = Intent(requireContext(), BookingActivity::class.java)
            startActivity(intent)
        }

        return view
    }
}