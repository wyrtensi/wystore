package dev.wystore.device

import dev.wystore.settings.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceProfilePolicyTest {

    private val phone = DeviceTraits(televisionUiMode = false, hasLeanback = false)

    @Test
    fun `auto picks TV from the television ui mode alone`() {
        assertEquals(
            DeviceProfile.TV,
            DeviceProfilePolicy.resolve(DeviceType.AUTO, DeviceTraits(televisionUiMode = true, hasLeanback = false))
        )
    }

    @Test
    fun `auto picks TV from the leanback feature alone`() {
        assertEquals(
            DeviceProfile.TV,
            DeviceProfilePolicy.resolve(DeviceType.AUTO, DeviceTraits(televisionUiMode = false, hasLeanback = true))
        )
    }

    @Test
    fun `auto without either signal is a phone`() {
        assertEquals(DeviceProfile.PHONE, DeviceProfilePolicy.resolve(DeviceType.AUTO, phone))
    }

    @Test
    fun `a manual choice wins over what the device says`() {
        val tv = DeviceTraits(televisionUiMode = true, hasLeanback = true)
        assertEquals(DeviceProfile.PHONE, DeviceProfilePolicy.resolve(DeviceType.PHONE, tv))
        assertEquals(DeviceProfile.TV, DeviceProfilePolicy.resolve(DeviceType.TV, phone))
    }
}
