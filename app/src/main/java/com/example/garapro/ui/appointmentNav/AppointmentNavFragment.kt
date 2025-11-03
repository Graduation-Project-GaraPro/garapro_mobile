package com.example.garapro.ui.appointmentNav

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.garapro.databinding.FragmentAppointmentNavBinding
import com.example.garapro.ui.appointments.AppointmentsFragment
import com.example.garapro.ui.quotations.QuotationsFragment
import com.google.android.material.tabs.TabLayoutMediator

class AppointmentNavFragment : Fragment() {

    private var _binding: FragmentAppointmentNavBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppointmentNavBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)

        // Thêm AppointmentsFragment và QuotationsFragment
        adapter.addFragment(com.example.garapro.ui.appointments.AppointmentsFragment(), "Lịch hẹn")
        adapter.addFragment(com.example.garapro.ui.quotations.QuotationsFragment(), "Báo giá")

        binding.viewPager.adapter = adapter

        // Kết nối TabLayout với ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.getPageTitle(position)
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Adapter cho ViewPager2
class ViewPagerAdapter(fragment: Fragment) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

    private val fragments = mutableListOf<Fragment>()
    private val titles = mutableListOf<String>()

    fun addFragment(fragment: Fragment, title: String) {
        fragments.add(fragment)
        titles.add(title)
    }

    fun getPageTitle(position: Int): String {
        return titles[position]
    }

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]
}