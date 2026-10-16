package tss.t.tsiptv.feature.lan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanScanTest {
    @Test
    fun aHomeWifiIsItsOwn24WithoutThisPhone() {
        val hosts = LanScan.hosts(listOf(LanNetwork("192.168.1.23", 24)))
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.23" in hosts)
        assertFalse("192.168.1.0" in hosts)
        assertFalse("192.168.1.255" in hosts)
    }

    @Test
    fun aLargeNetworkIsCutToThePhones24() {
        val hosts = LanScan.hosts(listOf(LanNetwork("10.20.30.40", 16)))
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.20.30.") })
    }

    @Test
    fun aSmallNetworkStaysSmall() {
        assertEquals(listOf("192.168.5.1"), LanScan.hosts(listOf(LanNetwork("192.168.5.2", 30))))
        assertEquals(emptyList(), LanScan.hosts(listOf(LanNetwork("192.168.5.3", 31))))
    }

    @Test
    fun badInputIsSkipped() {
        assertEquals(emptyList(), LanScan.hosts(listOf(LanNetwork("fe80::1", 64), LanNetwork("192.168.1.300", 24), LanNetwork("192.168.1.2", 0))))
    }

    @Test
    fun twoInterfacesOnOneNetworkAreAskedOnce() {
        val hosts = LanScan.hosts(listOf(LanNetwork("192.168.1.23", 24), LanNetwork("192.168.1.24", 24)))
        assertEquals(252, hosts.size)
        assertEquals(hosts.distinct(), hosts)
    }
}
