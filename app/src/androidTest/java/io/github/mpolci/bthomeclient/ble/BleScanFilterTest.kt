package io.github.mpolci.bthomeclient.ble

import android.os.ParcelUuid
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleScanFilterTest {
    @Test
    fun filtersMatchExactBTHomeServiceDataPrefixesWithoutServiceUuidConstraint() {
        val filters = bthomeScanFilters()
        val expected = ParcelUuid(UUID.fromString("0000fcd2-0000-1000-8000-00805f9b34fb"))
        val prefixes = filters.map { it.serviceData!![0].toInt() and 0xFF }
        assertEquals(
            listOf(0x40, 0x41, 0x44, 0x45, 0x20, 0x21, 0x24, 0x25),
            prefixes,
        )
        filters.forEach { filter ->
            assertEquals(expected, filter.serviceDataUuid)
            assertNull(filter.serviceDataMask)
            assertNull(filter.serviceUuid)
        }
    }
}
