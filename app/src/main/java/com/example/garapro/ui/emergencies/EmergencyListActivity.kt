package com.example.garapro.ui.emergencies

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.garapro.R
import com.example.garapro.data.model.emergencies.EmergencyStatus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

class EmergencyListActivity : AppCompatActivity() {

    private val viewModel: EmergencyListViewModel by viewModels()
    private lateinit var adapter: EmergencyAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var rvEmergencies: RecyclerView
    private var allItems: List<EmergencySummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_list)

        setupToolbar()
        setupViews()
        setupViewModel()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        tabLayout = findViewById(R.id.tabLayout)
        emptyState = findViewById(R.id.emptyState)
        rvEmergencies = findViewById(R.id.rvEmergencies)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val btnCreateNew = findViewById<MaterialButton>(R.id.btnCreateNew)

        adapter = EmergencyAdapter(
            onTrackClick = { item ->
                // Navigate to MapActivity to track
                // We need to fetch full emergency details or pass enough info
                val intent = Intent(this, MapActivity::class.java).apply {
                    putExtra("emergency_id", item.id)
                }
                startActivity(intent)
            },
            onDetailClick = { item ->
                // Show details (maybe bottom sheet or dialog?)
                // For now, no-op or toast
            }
        )

        rvEmergencies.layoutManager = LinearLayoutManager(this)
        rvEmergencies.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            loadData()
            swipeRefresh.isRefreshing = false
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterList(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnCreateNew.setOnClickListener {
            // Force new request
            val intent = Intent(this, MapActivity::class.java).apply {
                putExtra("force_new", true)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupViewModel() {
        viewModel.emergencies.observe(this) { items ->
            allItems = items
            filterList(tabLayout.selectedTabPosition)
        }
        
        loadData()
    }

    private fun loadData() {
        val userPrefs = getSharedPreferences(com.example.garapro.utils.Constants.USER_PREFERENCES, android.content.Context.MODE_PRIVATE)
        val authPrefs = getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
        val userId = userPrefs.getString("user_id", null) ?: authPrefs.getString("user_id", null)
        
        viewModel.loadEmergencies(userId)
    }

    private fun filterList(tabIndex: Int) {
        val filtered = if (tabIndex == 0) {
            // Current: Pending, Accepted, Assigned, InProgress, Towing
            allItems.filter { 
                val s = it.status.lowercase()
                s != "completed" && s != "cancelled" && s != "canceled" && s != "expired"
            }
        } else {
            // Past: Completed, Cancelled, Expired
            allItems.filter { 
                val s = it.status.lowercase()
                s == "completed" || s == "cancelled" || s == "canceled" || s == "expired"
            }
        }

        adapter.submitList(filtered, tabIndex == 0)

        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvEmergencies.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            rvEmergencies.visibility = View.VISIBLE
        }
    }
}
