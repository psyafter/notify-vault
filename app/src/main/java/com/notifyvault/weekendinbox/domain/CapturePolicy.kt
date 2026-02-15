package com.notifyvault.weekendinbox.domain

import com.notifyvault.weekendinbox.data.CaptureMode

class CapturePolicy {
    fun shouldCapturePackage(mode: CaptureMode, isPackageSelected: Boolean): Boolean {
        return when (mode) {
            CaptureMode.ONLY_SELECTED_APPS -> isPackageSelected
            CaptureMode.ALL_APPS -> true
        }
    }
}
