package com.example.garapro.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.ui.appointments.adapter.AppointmentAdapter
import com.example.garapro.ui.appointments.model.Appointment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AppointmentsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_appointments, container, false)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerAppointments)
        val btnAdd = view.findViewById<FloatingActionButton>(R.id.btnAddAppointment)

        val sampleData = listOf(
            Appointment(1, "Gara Minh Long", "Thay nhớt", "20/10/2025 - 10:00", "51A-12345", "Chờ xác nhận"),
            Appointment(2, "Auto Việt Hàn", "Bảo dưỡng", "22/10/2025 - 14:30", "30H-88888", "Đã xác nhận"),
            Appointment(3, "Trung Tín Auto", "Sửa phanh", "25/10/2025 - 09:00", "60A-45678", "Hoàn thành")
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = AppointmentAdapter(sampleData) { appointment ->
            // TODO: Navigate to detail screen
        }

        btnAdd.setOnClickListener {
            // TODO: Navigate to booking screen
        }

        return view
    }
}
