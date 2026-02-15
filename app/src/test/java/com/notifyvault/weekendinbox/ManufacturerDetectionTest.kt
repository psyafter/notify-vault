package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.util.OemFamily
import com.notifyvault.weekendinbox.util.detectOemFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class ManufacturerDetectionTest {

    @Test
    fun mapsXiaomiFamily() {
        assertEquals(OemFamily.XIAOMI, detectOemFamily("Xiaomi"))
        assertEquals(OemFamily.XIAOMI, detectOemFamily("Redmi"))
        assertEquals(OemFamily.XIAOMI, detectOemFamily("POCO"))
    }

    @Test
    fun mapsSamsungFamily() {
        assertEquals(OemFamily.SAMSUNG, detectOemFamily("samsung"))
    }

    @Test
    fun mapsHuaweiHonorFamily() {
        assertEquals(OemFamily.HUAWEI_HONOR, detectOemFamily("Huawei"))
        assertEquals(OemFamily.HUAWEI_HONOR, detectOemFamily("HONOR"))
    }

    @Test
    fun mapsBbkAndOneplusFamily() {
        assertEquals(OemFamily.BBK_PLUS_ONEPLUS, detectOemFamily("OnePlus"))
        assertEquals(OemFamily.BBK_PLUS_ONEPLUS, detectOemFamily("OPPO"))
        assertEquals(OemFamily.BBK_PLUS_ONEPLUS, detectOemFamily("realme"))
        assertEquals(OemFamily.BBK_PLUS_ONEPLUS, detectOemFamily("vivo"))
    }

    @Test
    fun mapsUnknownToOther() {
        assertEquals(OemFamily.OTHER, detectOemFamily("Google"))
    }
}
