package dev.felippevaz.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class IpAllowListTest {

    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @Test
    void emptyListAllowsEverything() throws Exception {
        IpAllowList list = new IpAllowList();

        assertTrue(list.isEmpty());
        assertTrue(list.isAllowed(ip("8.8.8.8")));
    }

    @Test
    void exactIpv4() throws Exception {
        IpAllowList list = new IpAllowList();
        list.add("172.18.0.1");

        assertTrue(list.isAllowed(ip("172.18.0.1")));
        assertFalse(list.isAllowed(ip("172.18.0.2")));
        assertFalse(list.isAllowed(null));
    }

    @Test
    void ipv4Cidr() throws Exception {
        IpAllowList list = new IpAllowList();
        list.add("10.0.0.0/8");
        list.add("192.168.1.128/25");

        assertTrue(list.isAllowed(ip("10.255.3.4")));
        assertFalse(list.isAllowed(ip("11.0.0.1")));
        assertTrue(list.isAllowed(ip("192.168.1.200")));
        assertFalse(list.isAllowed(ip("192.168.1.127")));
    }

    @Test
    void ipv6AndMappedIpv4() throws Exception {
        IpAllowList list = new IpAllowList();
        list.add("::1");
        list.add("fd00::/8");
        list.add("127.0.0.1");

        assertTrue(list.isAllowed(ip("::1")));
        assertTrue(list.isAllowed(ip("fd12:3456::1")));
        assertFalse(list.isAllowed(ip("fe80::1")));
        // IPv4 mapeado em IPv6 casa com a regra IPv4.
        assertTrue(list.isAllowed(InetAddress.getByAddress(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 127, 0, 0, 1})));
    }

    @Test
    void zeroPrefixMatchesAllOfTheSameFamily() throws Exception {
        IpAllowList list = new IpAllowList();
        list.add("0.0.0.0/0");

        assertTrue(list.isAllowed(ip("1.2.3.4")));
        assertFalse(list.isAllowed(ip("::2")));
    }

    @Test
    void invalidRulesAreRejected() {
        IpAllowList list = new IpAllowList();

        assertThrows(IllegalArgumentException.class, () -> list.add("example.com"));
        assertThrows(IllegalArgumentException.class, () -> list.add("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> list.add("10.0.0.0/x"));
        assertThrows(IllegalArgumentException.class, () -> list.add(" "));
        assertTrue(list.isEmpty());
    }
}
