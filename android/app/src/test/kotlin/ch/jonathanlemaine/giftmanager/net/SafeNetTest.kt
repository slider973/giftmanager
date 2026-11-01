package ch.jonathanlemaine.giftmanager.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde SSRF : port des cas de `safe_fetch.ts`. */
class SafeNetTest {
    @Test fun blocksLocalAndInternalNames() {
        listOf("localhost", "LOCALHOST.", "app.localhost", "printer.local", "db.internal", "intranet", "").forEach {
            assertTrue("$it doit être bloqué", SafeNet.isBlockedHostname(it))
        }
    }

    @Test fun blocksLiteralIps() {
        listOf("127.0.0.1", "10.0.0.1", "2130706433", "0x7f000001", "0x7f.0.0.1", "[::1]", "::1", "1.2.3.0377").forEach {
            assertTrue("$it doit être bloqué", SafeNet.isBlockedHostname(it))
        }
    }

    @Test fun allowsPublicNames() {
        listOf("www.galaxus.ch", "amazon.fr", "shop.example.co.uk", "xn--caf-dma.example.com", "a1.example").forEach {
            assertFalse("$it doit passer", SafeNet.isBlockedHostname(it))
        }
    }

    @Test fun privateV4() {
        listOf(
            "0.0.0.0", "10.1.2.3", "127.0.0.1", "100.64.0.1", "100.127.255.255", "169.254.169.254", "172.16.0.1",
            "172.31.255.255", "192.168.1.1", "192.0.0.1", "192.0.2.5", "198.18.0.1", "198.19.0.1", "224.0.0.1",
            "255.255.255.255",
        ).forEach { assertTrue("$it privée", SafeNet.isPrivateIp(it)) }
    }

    @Test fun publicV4() {
        listOf("8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1", "100.63.0.1", "198.17.0.1", "93.184.216.34")
            .forEach { assertFalse("$it publique", SafeNet.isPrivateIp(it)) }
    }

    @Test fun malformedAddressesAreTreatedAsPrivate() {
        listOf("", "abc", "256.1.1.1", "1.2.3", "1.2.3.4.5", "gggg::1", "1:2:3", "1::2::3").forEach {
            assertTrue("$it illisible", SafeNet.isPrivateIp(it))
        }
    }

    @Test fun privateV6() {
        listOf(
            "::", "::1", "fc00::1", "fd12:3456::1", "fe80::1", "fe80::1%eth0", "fec0::1", "ff02::1", "2001:db8::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:7f00:1", "::192.168.0.1", "64:ff9b::7f00:1",
            "64:ff9b::10.0.0.1", "2002:7f00:1::1", "2002:c0a8:101::",
        ).forEach { assertTrue("$it privée", SafeNet.isPrivateIp(it)) }
    }

    @Test fun publicV6() {
        listOf("2606:4700:4700::1111", "2a00:1450:4001:81b::200e", "::ffff:8.8.8.8", "64:ff9b::808:808", "2002:808:808::")
            .forEach { assertFalse("$it publique", SafeNet.isPrivateIp(it)) }
    }
}
