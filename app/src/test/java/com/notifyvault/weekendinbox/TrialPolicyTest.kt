package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.domain.TrialPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialPolicyTest {

    @Test
    fun trialWindowIsTimezoneIndependent() {
        val firstLaunch = 1_700_000_000_000L
        val inside = firstLaunch + (14L * 24L * 60L * 60L * 1000L) - 1
        val outside = firstLaunch + (14L * 24L * 60L * 60L * 1000L) + 1

        assertTrue(TrialPolicy.isTrialActive(firstLaunch, inside))
        assertFalse(TrialPolicy.isTrialActive(firstLaunch, outside))
    }

    @Test
    fun gatingDecisionMatrix() {
        val firstLaunch = 1_700_000_000_000L
        val afterExpiry = firstLaunch + (15L * 24L * 60L * 60L * 1000L)

        assertTrue(TrialPolicy.canCaptureNewNotifications(true, firstLaunch, afterExpiry))
        assertFalse(TrialPolicy.canCaptureNewNotifications(false, firstLaunch, afterExpiry))
        assertTrue(TrialPolicy.canCaptureNewNotifications(false, firstLaunch, firstLaunch))
    }
}
