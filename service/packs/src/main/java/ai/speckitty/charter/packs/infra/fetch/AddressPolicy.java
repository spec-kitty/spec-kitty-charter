package ai.speckitty.charter.packs.infra.fetch;

import java.net.InetAddress;
import java.util.List;

/**
 * Which addresses a guide may be fetched from: global unicast only (NFR-004). An allow-only rule over the raw address
 * bytes, because the {@code InetAddress} predicates miss unique-local {@code fc00::/7}, CGNAT, the IPv4-compatible
 * form and every address that embeds an IPv4 (adversary review §1).
 *
 * <p>IPv4 is refused in {@code 0/8}, {@code 10/8}, {@code 100.64/10}, {@code 127/8}, {@code 169.254/16},
 * {@code 172.16/12}, {@code 192.0.0/24}, {@code 192.0.2/24}, {@code 192.88.99/24}, {@code 192.168/16},
 * {@code 198.18/15}, {@code 198.51.100/24}, {@code 203.0.113/24} and {@code 224/3} (multicast, reserved,
 * broadcast). IPv6 is accepted only in {@code 2000::/3} outside {@code 2001::/23} (protocol assignments, Teredo,
 * benchmarking), {@code 2001:db8::/32} and {@code 3fff::/20} (documentation); an address that embeds an IPv4
 * (IPv4-mapped {@code ::ffff:0:0/96}, IPv4-compatible {@code ::/96}, NAT64 {@code 64:ff9b::/96}, 6to4
 * {@code 2002::/16}) is judged by the IPv4 it embeds. Everything else ({@code ::}, {@code ::1}, {@code fc00::/7},
 * {@code fe80::/10}, {@code fec0::/10}, {@code ff00::/8}, {@code 64:ff9b:1::/48}, {@code 100::/64}, ...) is refused.
 */
public final class AddressPolicy {

    /**
     * The IPv4 blocks a guide is never fetched from, as {@code a.b.c} prefixes of at most 24 bits: each block's
     * prefix packed as {@code a << 16 | b << 8 | c}, and its length.
     */
    private static final List<V4Block> REFUSED_V4 = List.of(
            new V4Block(0, 0, 0, 8), // this network
            new V4Block(10, 0, 0, 8), // private
            new V4Block(100, 64, 0, 10), // shared address space (CGNAT)
            new V4Block(127, 0, 0, 8), // loopback
            new V4Block(169, 254, 0, 16), // link-local
            new V4Block(172, 16, 0, 12), // private
            new V4Block(192, 0, 0, 24), // IETF protocol assignments
            new V4Block(192, 0, 2, 24), // TEST-NET-1
            new V4Block(192, 88, 99, 24), // 6to4 relay anycast
            new V4Block(192, 168, 0, 16), // private
            new V4Block(198, 18, 0, 15), // benchmarking
            new V4Block(198, 51, 100, 24), // TEST-NET-2
            new V4Block(203, 0, 113, 24), // TEST-NET-3
            new V4Block(224, 0, 0, 3)); // multicast, reserved and broadcast

    private AddressPolicy() {}

    /**
     * Whether a guide may be fetched from the address.
     *
     * @param address a resolved address
     * @return {@code true} only for a global unicast address
     */
    public static boolean isGlobalUnicast(InetAddress address) {
        byte[] bytes = address.getAddress();
        return switch (bytes.length) {
            case 4 -> isGlobalV4(u(bytes, 0), u(bytes, 1), u(bytes, 2));
            case 16 -> isGlobalV6(bytes);
            default -> false;
        };
    }

    private static int u(byte[] bytes, int index) {
        return bytes[index] & 0xff;
    }

    private static boolean isGlobalV4(int a, int b, int c) {
        int address = (a << 16) | (b << 8) | c;
        return REFUSED_V4.stream().noneMatch(block -> block.contains(address));
    }

    /** One refused IPv4 block: its first 24 bits packed into an int, and how many of them are the prefix. */
    private record V4Block(int prefix, int length) {

        V4Block(int a, int b, int c, int length) {
            this((a << 16) | (b << 8) | c, length);
        }

        boolean contains(int address) {
            int shift = 24 - length;
            return (address >>> shift) == (prefix >>> shift);
        }
    }

    private static boolean isGlobalV6(byte[] bytes) {
        int w0 = word(bytes, 0);
        int w1 = word(bytes, 2);
        if (allZero(bytes, 0, 10)) {
            int w5 = word(bytes, 10);
            // IPv4-mapped and IPv4-compatible; :: and ::1 read as 0.0.0.0 and 0.0.0.1, which 0/8 refuses.
            return (w5 == 0xffff || w5 == 0) && isGlobalV4(u(bytes, 12), u(bytes, 13), u(bytes, 14));
        }
        if (w0 == 0x0064 && w1 == 0xff9b) {
            // NAT64 well-known prefix 64:ff9b::/96 embeds an IPv4; 64:ff9b:1::/48 is local use.
            return allZero(bytes, 4, 12) && isGlobalV4(u(bytes, 12), u(bytes, 13), u(bytes, 14));
        }
        if ((w0 & 0xe000) != 0x2000) {
            return false; // outside global unicast 2000::/3: fc00::/7, fe80::/10, fec0::/10, ff00::/8, 100::/64, ...
        }
        if (w0 == 0x2002) {
            return isGlobalV4(u(bytes, 2), u(bytes, 3), u(bytes, 4)); // 6to4 embeds the IPv4 in bits 16..47
        }
        if (w0 == 0x2001 && (w1 < 0x0200 || w1 == 0x0db8)) {
            return false; // 2001::/23 (Teredo, benchmarking, ORCHID, ...) and documentation 2001:db8::/32
        }
        return (w0 & 0xfff0) != 0x3ff0; // documentation 3fff::/20
    }

    private static int word(byte[] bytes, int index) {
        return (u(bytes, index) << 8) | u(bytes, index + 1);
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
