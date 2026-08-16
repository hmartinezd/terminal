package com.venkoi.terminal.licensing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceIdentityPersistenceTest {
    @Test fun identityIsStableAndApiExposesNoPrivateKey() {
        val provider = DeviceIdentityProvider()
        val first = provider.get()
        val second = provider.get()
        assertEquals(first, second)
        assertFalse(first.deviceKeyId.isBlank())
        assertFalse(first.deviceCode.isBlank())
        // DeviceIdentity deliberately contains fingerprints only; no private key field exists.
        assertFalse(DeviceIdentity::class.java.declaredFields.any { it.name.contains("private", ignoreCase = true) })
    }
}
