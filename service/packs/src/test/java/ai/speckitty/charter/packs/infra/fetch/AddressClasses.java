package ai.speckitty.charter.packs.infra.fetch;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Every address class the policy refuses, each with a global address just outside it (the accepted value next to
 * the refusal). Built from literals and raw bytes only: nothing here performs a lookup.
 */
final class AddressClasses {

    private AddressClasses() {}

    /** A global unicast address the stub resolvers hand out (TEST-NET is refused, so a public one is used). */
    static final InetAddress GLOBAL = literal("93.184.215.14");

    /** A private address. */
    static final InetAddress PRIVATE = literal("10.0.0.1");

    /** (class, refused address, accepted neighbour). */
    static Stream<Arguments> refusedWithAcceptedNeighbour() {
        return Stream.of(
                row("this network 0/8", "0.0.0.0", "1.0.0.1"),
                row("this network 0/8", "0.255.255.255", "1.0.0.1"),
                row("private 10/8", "10.0.0.1", "11.0.0.1"),
                row("CGNAT 100.64/10 low", "100.64.0.1", "100.63.255.255"),
                row("CGNAT 100.64/10 high", "100.127.255.255", "100.128.0.0"),
                row("loopback 127/8", "127.0.0.1", "128.0.0.1"),
                row("loopback 127/8", "127.255.255.254", "126.255.255.255"),
                row("link-local 169.254/16", "169.254.169.254", "169.253.0.1"),
                row("private 172.16/12 low", "172.16.0.1", "172.15.255.255"),
                row("private 172.16/12 high", "172.31.255.255", "172.32.0.0"),
                row("IETF protocol assignments 192.0.0/24", "192.0.0.8", "192.0.1.1"),
                row("documentation TEST-NET-1", "192.0.2.1", "192.0.3.1"),
                row("6to4 relay anycast 192.88.99/24", "192.88.99.1", "192.88.98.1"),
                row("private 192.168/16", "192.168.1.1", "192.169.0.1"),
                row("benchmarking 198.18/15 low", "198.18.0.1", "198.17.255.255"),
                row("benchmarking 198.18/15 high", "198.19.255.255", "198.20.0.0"),
                row("documentation TEST-NET-2", "198.51.100.1", "198.51.101.1"),
                row("documentation TEST-NET-3", "203.0.113.1", "203.0.114.1"),
                row("multicast 224/4", "224.0.0.1", "223.255.255.255"),
                row("multicast 224/4", "239.255.255.255", "223.255.255.255"),
                row("reserved 240/4", "240.0.0.1", "223.255.255.255"),
                row("broadcast", "255.255.255.255", "223.255.255.255"),
                row("IPv6 unspecified ::", "::", "2606:4700::1111"),
                row("IPv6 loopback ::1", "::1", "2606:4700::1111"),
                row("IPv6 unique-local fc00::/7", "fc00::1", "2a00:1450::1"),
                row("IPv6 unique-local fc00::/7", "fdff:ffff::1", "2a00:1450::1"),
                row("IPv6 link-local fe80::/10", "fe80::1", "2a00:1450::1"),
                row("IPv6 link-local fe80::/10", "febf::1", "2a00:1450::1"),
                row("IPv6 site-local fec0::/10", "fec0::1", "2a00:1450::1"),
                row("IPv6 multicast ff00::/8", "ff02::1", "2a00:1450::1"),
                row("IPv6 discard-only 100::/64", "100::1", "2a00:1450::1"),
                row("IPv6 documentation 2001:db8::/32", "2001:db8::1", "2001:db9::1"),
                row("IPv6 documentation 3fff::/20", "3fff::1", "2c0f:f000::1"),
                row("IPv6 Teredo 2001::/32", "2001::1", "2001:200::1"),
                row("IPv6 benchmarking 2001:2::/48", "2001:2::1", "2001:200::1"),
                row("NAT64 64:ff9b::/96 embedding 127.0.0.1", "64:ff9b::7f00:1", "64:ff9b::808:808"),
                row("NAT64 64:ff9b::/96 embedding 10.0.0.1", "64:ff9b::a00:1", "64:ff9b::808:808"),
                row("NAT64 local-use 64:ff9b:1::/48", "64:ff9b:1::808:808", "64:ff9b::808:808"),
                row("6to4 2002::/16 embedding 127.0.0.1", "2002:7f00:1::1", "2002:808:808::1"),
                row("6to4 2002::/16 embedding 192.168.0.1", "2002:c0a8:1::1", "2002:808:808::1"),
                row("IPv4-compatible ::127.0.0.1", "::7f00:1", "::808:808"),
                row("IPv4-compatible ::10.0.0.1", "::a00:1", "::808:808"),
                Arguments.of("IPv4-mapped ::ffff:127.0.0.1", mapped(127, 0, 0, 1), mapped(8, 8, 8, 8)),
                Arguments.of("IPv4-mapped ::ffff:169.254.169.254", mapped(169, 254, 169, 254), mapped(8, 8, 8, 8)),
                Arguments.of("IPv4-mapped ::ffff:100.64.0.1", mapped(100, 64, 0, 1), mapped(8, 8, 8, 8)));
    }

    private static Arguments row(String name, String refused, String accepted) {
        return Arguments.of(name, literal(refused), literal(accepted));
    }

    static InetAddress literal(String text) {
        return InetAddress.ofLiteral(text);
    }

    /** {@code ::ffff:a.b.c.d} kept as a 16-byte address (a literal would fold it to IPv4). */
    static InetAddress mapped(int a, int b, int c, int d) {
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xff;
        bytes[12] = (byte) a;
        bytes[13] = (byte) b;
        bytes[14] = (byte) c;
        bytes[15] = (byte) d;
        try {
            return Inet6Address.getByAddress(null, bytes, -1);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
