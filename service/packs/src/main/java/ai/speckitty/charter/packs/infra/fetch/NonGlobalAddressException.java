package ai.speckitty.charter.packs.infra.fetch;

import java.io.Serial;
import java.net.UnknownHostException;

/** An allowlisted host resolved to an address that is not global unicast; the lookup is refused as a whole. */
final class NonGlobalAddressException extends UnknownHostException {

    @Serial
    private static final long serialVersionUID = 1L;

    NonGlobalAddressException(String host) {
        super(host + ": resolves to an address that is not global unicast");
    }
}
