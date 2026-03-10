package com.afex.explorer.presentation.permissions

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val needsManageStoragePermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun recheckPermission() {
        _hasPermission.value = checkPermission()
    }

    private fun checkPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()
        else ->
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }
}
