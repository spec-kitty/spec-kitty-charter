package ai.speckitty.charter.packs.infra.fetch;

import java.net.InetAddress;

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
        if (a == 0 || a == 10 || a == 127 || a >= 224) {
            return false; // this network, private, loopback, multicast, reserved and broadcast
        }
        if (a == 100 && (b & 0xc0) == 64) {
            return false; // 100.64.0.0/10 shared address space (CGNAT)
        }
        if (a == 169 && b == 254) {
            return false; // link-local
        }
        if (a == 172 && (b & 0xf0) == 16) {
            return false; // 172.16.0.0/12 private
        }
        if (a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99))) {
            return false; // private, IETF protocol assignments, TEST-NET-1, 6to4 relay anycast
        }
        if (a == 198 && ((b & 0xfe) == 18 || (b == 51 && c == 100))) {
            return false; // benchmarking 198.18.0.0/15, TEST-NET-2
        }
        return !(a == 203 && b == 0 && c == 113); // TEST-NET-3
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
