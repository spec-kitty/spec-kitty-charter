package ai.speckitty.charter.packs.infra.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The JVM-wide name-resolution guard that closes DNS rebinding for guide fetches (JEP 418, research D8a). The JDK's
 * {@code HttpClient} resolves the host itself when it connects and offers no way to pin the address the fetcher
 * checked, so the check is made where every lookup passes: this provider delegates to the built-in resolver and, for
 * a host on the guide allowlist only, refuses the whole answer when any address in it is not global unicast
 * ({@link AddressPolicy}). Every other lookup passes through untouched.
 *
 * <p>The JDK, not Spring, instantiates this class (through
 * {@code META-INF/services/java.net.spi.InetAddressResolverProvider}, on the first name lookup of the JVM), so the
 * allowlist reaches it through a static holder that the fetcher's Spring configuration fills at startup
 * ({@link #install}). The holder starts empty: until something installs an allowlist the guard acts on no host, and
 * resolution behaves exactly as without it.
 *
 * <p>The guard fails closed: the JDK picks its resolver once, on the JVM's first lookup, through the context class
 * loader of that moment, so a packaging in which this provider is not visible then leaves the built-in resolver in
 * place for good. {@link #installRequiringGuard} therefore refuses a non-empty allowlist unless the JDK actually
 * installed this provider, and the service does not start rather than fetch without the rebinding guard.
 */
public final class GlobalAddressResolverProvider extends InetAddressResolverProvider {

    private static final AtomicReference<GuideAllowlist> ALLOWLIST = new AtomicReference<>(GuideAllowlist.EMPTY);
    private static final GuardRefusals REFUSALS = new GuardRefusals();
    private static final AtomicBoolean LOADED_BY_JDK = new AtomicBoolean();

    /** For the JDK's {@code ServiceLoader}, which instantiates the provider. */
    public GlobalAddressResolverProvider() {}

    /**
     * Installs the allowlist the guard acts on, replacing the previous one.
     *
     * @param allowlist the hosts whose lookups must answer global unicast addresses only
     */
    public static void install(GuideAllowlist allowlist) {
        ALLOWLIST.set(allowlist);
    }

    /**
     * Installs the allowlist for fetching, insisting that the guard is the JVM's resolver when the allowlist names any
     * host.
     *
     * @param allowlist the configured allowlist
     * @throws IllegalStateException when the allowlist names a host and the JDK did not install this provider
     */
    public static void installRequiringGuard(GuideAllowlist allowlist) {
        installRequiringGuard(allowlist, GlobalAddressResolverProvider::isTheJvmResolver);
    }

    static void installRequiringGuard(GuideAllowlist allowlist, BooleanSupplier guardIsTheJvmResolver) {
        if (!allowlist.hosts().isEmpty() && !guardIsTheJvmResolver.getAsBoolean()) {
            throw new IllegalStateException("charter.guide-fetch.allowed-hosts is set, but the JVM did not install the"
                    + " guide-fetch resolver guard (META-INF/services/java.net.spi.InetAddressResolverProvider);"
                    + " refusing to fetch guides without DNS-rebinding protection");
        }
        install(allowlist);
    }

    /**
     * Whether the JDK installed this provider as the JVM's resolver. A lookup of {@code localhost} (answered from the
     * hosts file or the JDK's own loopback fallback, never the network) makes the JDK choose its resolver if it has
     * not yet.
     */
    static boolean isTheJvmResolver() {
        try {
            InetAddress.getAllByName("localhost");
        } catch (UnknownHostException e) {
            // the JDK answers localhost itself when resolution fails; nothing to learn from the failure
        }
        return LOADED_BY_JDK.get();
    }

    /**
     * The allowlist the guard acts on now.
     *
     * @return the installed allowlist, {@link GuideAllowlist#EMPTY} until one is installed
     */
    public static GuideAllowlist installed() {
        return ALLOWLIST.get();
    }

    /** The refusals of the guard the JDK installed; the production fetcher reads them to type a connect failure. */
    static GuardRefusals refusals() {
        return REFUSALS;
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        LOADED_BY_JDK.set(true);
        return new Guard(configuration.builtinResolver(), ALLOWLIST::get, REFUSALS);
    }

    @Override
    public String name() {
        return "spec-kitty-charter guide-fetch global-address guard";
    }

    /** The guarding resolver; package-visible so a test drives it over a stub delegate and allowlist. */
    record Guard(InetAddressResolver delegate, Supplier<GuideAllowlist> allowlist, GuardRefusals refusals)
            implements InetAddressResolver {

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
            Stream<InetAddress> answer = delegate.lookupByName(host, lookupPolicy);
            if (!allowlist.get().allows(host)) {
                return answer;
            }
            List<InetAddress> addresses = answer.toList();
            if (!addresses.stream().allMatch(AddressPolicy::isGlobalUnicast)) {
                refusals.record(host);
                throw new NonGlobalAddressException(host);
            }
            return addresses.stream();
        }

        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            return delegate.lookupByAddress(addr);
        }
    }
}
