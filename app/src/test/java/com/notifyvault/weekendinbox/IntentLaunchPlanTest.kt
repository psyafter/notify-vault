package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.util.IntentLaunchPlan
import com.notifyvault.weekendinbox.util.resolveLaunchPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentLaunchPlanTest {

    @Test
    fun returnsFirstLaunchablePrimaryPlan() {
        val first = IntentLaunchPlan("action.first")
        val second = IntentLaunchPlan("action.second")
        val fallback = IntentLaunchPlan("action.fallback")

        val resolved = resolveLaunchPlan(
            plans = listOf(first, second),
            canLaunch = { it.action == "action.second" },
            fallback = fallback
        )

        assertEquals(second, resolved)
    }

    @Test
    fun fallsBackWhenNoPrimaryCanLaunch() {
        val fallback = IntentLaunchPlan("action.fallback", packageData = true)

        val resolved = resolveLaunchPlan(
            plans = listOf(IntentLaunchPlan("action.first")),
            canLaunch = { false },
            fallback = fallback
        )

        assertEquals(fallback, resolved)
    }
}
