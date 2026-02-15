package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.data.CaptureMode
import com.notifyvault.weekendinbox.domain.CapturePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePolicyTest {
    private val policy = CapturePolicy()

    @Test
    fun only_selected_apps_requires_selection() {
        assertTrue(policy.shouldCapturePackage(CaptureMode.ONLY_SELECTED_APPS, isPackageSelected = true))
        assertFalse(policy.shouldCapturePackage(CaptureMode.ONLY_SELECTED_APPS, isPackageSelected = false))
    }

    @Test
    fun all_apps_always_allows_capture() {
        assertTrue(policy.shouldCapturePackage(CaptureMode.ALL_APPS, isPackageSelected = false))
    }
}
