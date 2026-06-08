package com.secura9.dashboard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secura9.dashboard.data.firebase.FirebaseRepository
import com.secura9.dashboard.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FirebaseRepository()

    val status: StateFlow<DeviceStatus> = repo.status
    val events: StateFlow<List<Event>> = repo.events
    val pendingApproval: StateFlow<ApprovalRequest?> = repo.pendingApproval
    val otp: StateFlow<OTPData?> = repo.otp
    val clips: StateFlow<List<ClipInfo>> = repo.clips

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        repo.startListening()
    }

    override fun onCleared() {
        repo.stopListening()
        super.onCleared()
    }

    fun unlockDoor() {
        viewModelScope.launch {
            repo.unlockDoor()
            _snackbar.tryEmit("Door unlocked")
        }
    }

    fun lockDoor() {
        viewModelScope.launch {
            repo.lockDoor()
            _snackbar.tryEmit("Door locked")
        }
    }

    fun setNobodyHome(active: Boolean) {
        viewModelScope.launch {
            repo.setNobodyHome(active)
            _snackbar.tryEmit(if (active) "Nobody Home ON" else "Nobody Home OFF")
        }
    }

    fun generateOTP() {
        viewModelScope.launch {
            repo.generateOTP()
            _snackbar.tryEmit("OTP sent to device")
        }
    }

    fun approveRequest(name: String) {
        viewModelScope.launch {
            repo.approveRequest(name)
            _snackbar.tryEmit("Approved: $name")
        }
    }

    fun denyRequest() {
        viewModelScope.launch {
            repo.denyRequest()
            _snackbar.tryEmit("Request denied")
        }
    }

    fun refreshClips() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.loadClips()
            _isLoading.value = false
        }
    }

    fun startWebRTCSession() {
        viewModelScope.launch {
            _snackbar.tryEmit("Connecting to camera...")
        }
    }
}
