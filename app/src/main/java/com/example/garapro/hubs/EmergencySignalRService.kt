package com.example.garapro.hubs

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import android.util.Log

class EmergencySignalRService(
    hubUrl: String
) {
    private var currentUrl: String = hubUrl
    private var hub: HubConnection = HubConnectionBuilder.create(hubUrl).build()

    private val _events = MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Pair<String, String>> = _events

    fun setupListeners() {
        hub.on("EmergencyRequestTowing", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestTowing: $json")
            _events.tryEmit("EmergencyRequestTowing" to json.toString())
        }, JsonObject::class.java)
        hub.onClosed { error ->
            Log.e("EmergencyHub", "onClosed: ${error?.message}")
            _events.tryEmit("Closed" to (error?.message ?: "unknown"))
        }
        hub.on("Connected", { connId: String ->
            Log.d("SignalR_RAW", "Received Connected: $connId")
            _events.tryEmit("Connected" to connId)
        }, String::class.java)
        hub.on("EmergencyRequestCreated", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestCreated: $json")
            _events.tryEmit("EmergencyRequestCreated" to json.toString())
        }, JsonObject::class.java)
        hub.on("EmergencyRequestApproved", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestApproved: $json")
            _events.tryEmit("EmergencyRequestApproved" to json.toString())
        }, JsonObject::class.java)
        hub.on("EmergencyRequestRejected", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestRejected: $json")
            _events.tryEmit("EmergencyRequestRejected" to json.toString())
        }, JsonObject::class.java)
        hub.on("EmergencyRequestInProgress", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestInProgress: $json")
            _events.tryEmit("EmergencyRequestInProgress" to json.toString())
        }, JsonObject::class.java)
        
        hub.on("EmergencyRequestCanceled", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestCanceled: $json")
            _events.tryEmit("EmergencyRequestCanceled" to json.toString())
        }, JsonObject::class.java)
        hub.on("EmergencyRequestExpired", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestExpired: $json")
            _events.tryEmit("EmergencyRequestExpired" to json.toString())
        }, JsonObject::class.java)
        hub.on("TechnicianAssigned", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received TechnicianAssigned: $json")
            _events.tryEmit("TechnicianAssigned" to json.toString())
        }, JsonObject::class.java)
        hub.on("TechnicianLocationUpdated", { json: JsonObject ->
            // Log.d("SignalR_RAW", "Received TechnicianLocationUpdated") // Comment out to avoid spam
            _events.tryEmit("TechnicianLocationUpdated" to json.toString())
        }, JsonObject::class.java)
        hub.on("EmergencyRequestArrived", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestArrived: $json")
            _events.tryEmit("EmergencyRequestArrived" to json.toString())
        }, JsonObject::class.java)
        hub.on("EmergencyRequestCompleted", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received EmergencyRequestCompleted: $json")
            _events.tryEmit("EmergencyRequestCompleted" to json.toString())
        }, JsonObject::class.java)
        hub.on("TechnicianArrived", { json: JsonObject ->
            Log.d("SignalR_RAW", "Received TechnicianArrived: $json")
            _events.tryEmit("TechnicianArrived" to json.toString())
        }, JsonObject::class.java)
        hub.on("JoinedCustomerGroup", { grp: String ->
            Log.d("SignalR_RAW", "Received JoinedCustomerGroup: $grp")
            _events.tryEmit("JoinedCustomerGroup" to grp)
        }, String::class.java)
        hub.on("JoinedBranchGroup", { grp: String ->
            Log.d("SignalR_RAW", "Received JoinedBranchGroup: $grp")
            _events.tryEmit("JoinedBranchGroup" to grp)
        }, String::class.java)
    }

    fun start(onConnected: (() -> Unit)? = null) {
        when (hub.connectionState) {
            HubConnectionState.CONNECTED -> onConnected?.invoke()
            HubConnectionState.DISCONNECTED -> {
                Log.d("EmergencyHub", "start(): url=$currentUrl")
                hub.start().subscribe({
                    Log.d("EmergencyHub", "connected: ${hub.connectionState}")
                    onConnected?.invoke()
                }, { e ->
                    Log.e("EmergencyHub", "start error", e)
                })
            }
            else -> {}
        }
    }

    fun joinCustomerGroup(customerId: String) {
        if (hub.connectionState == HubConnectionState.CONNECTED) {
            Log.d("EmergencyHub", "Joining Customer Groups for ID: $customerId")
            // Try joining standard group
            hub.send("JoinCustomerGroup", customerId)
            // Try joining with prefix just in case Backend expects raw ID but sends to prefixed group
            // and the Hub method doesn't add the prefix automatically.
            hub.send("JoinCustomerGroup", "customer-$customerId")
        } else {
            Log.w("EmergencyHub", "joinCustomerGroup while not CONNECTED")
        }
    }

    fun joinEmergencyGroup(emergencyId: String) {
        if (hub.connectionState == HubConnectionState.CONNECTED) {
            hub.send("JoinEmergencyGroup", emergencyId)
        } else {
            Log.w("EmergencyHub", "joinEmergencyGroup while not CONNECTED")
        }
    }

    fun joinBranchGroup(branchId: String) {
        if (hub.connectionState == HubConnectionState.CONNECTED) {
            hub.send("JoinBranchGroup", branchId)
        } else {
            Log.w("EmergencyHub", "joinBranchGroup while not CONNECTED")
        }
    }

    fun stop() {
        if (hub.connectionState == HubConnectionState.CONNECTED) hub.stop()
    }

    fun isConnected(): Boolean = hub.connectionState == HubConnectionState.CONNECTED

    fun reconnectWithUrl(url: String, onConnected: (() -> Unit)? = null) {
        try {
            stop()
        } catch (_: Exception) {}
        currentUrl = url
        Log.d("EmergencyHub", "reconnectWithUrl: url=$url")
        hub = HubConnectionBuilder.create(url).build()
        setupListeners()
        start(onConnected)
    }
}
