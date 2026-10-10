package com.zvpn.ui.map

import com.zvpn.data.map.RegionResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewModelTest {

    @Test
    fun testRegionResolverMapping() {
        assertEquals("Americas", RegionResolver.resolve("US"))
        assertEquals("Americas", RegionResolver.resolve("ca"))
        assertEquals("Europe", RegionResolver.resolve("DE"))
        assertEquals("Europe", RegionResolver.resolve("uk"))
        assertEquals("Asia-Pacific", RegionResolver.resolve("JP"))
        assertEquals("Asia-Pacific", RegionResolver.resolve("sg"))
        assertEquals("ME/Africa", RegionResolver.resolve("AE"))
        assertEquals("ME/Africa", RegionResolver.resolve("za"))
        assertEquals("Other", RegionResolver.resolve("UNKNOWN"))
        assertEquals("Other", RegionResolver.resolve(null))
    }
}
