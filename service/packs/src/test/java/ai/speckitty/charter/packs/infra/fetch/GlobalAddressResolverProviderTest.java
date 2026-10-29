package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.GLOBAL;
import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The JEP 418 guard (T033): the pure address policy over every refused class with an accepted neighbour, the guarding
 * resolver over a stub delegate, and the provider as the JVM actually installed it. Nothing here opens a socket: the
 * stubs resolve nothing, and the one JVM lookup is of {@code localhost}, answered from the hosts file.
 */
class GlobalAddressResolverProviderTest {

    private static final LookupPolicy ANY = LookupPolicy.of(LookupPolicy.IPV4 | LookupPolicy.IPV6);
    private static final GuideAllowlist GUIDES = GuideAllowlist.of(List.of("guide.example"));

    @ParameterizedTest(name = "{0}: {1} refused, {2} accepted")
    @MethodSource("ai.speckitty.charter.packs.infra.fetch.AddressClasses#refusedWithAcceptedNeighbour")
    void everyNonGlobalClassIsRefusedAndItsGlobalNeighbourAccepted(
            String addressClass, InetAddress refused, InetAddress accepted) {
        assertThat(AddressPolicy.isGlobalUnicast(refused)).as(addressClass).isFalse();
        assertThat(AddressPolicy.isGlobalUnicast(accepted)).as(addressClass).isTrue();
    }

    @Test
    void theIpv4MappedFixtureReallyTakesTheSixteenByteBranch() {
        assertThat(AddressClasses.mapped(127, 0, 0, 1)).isInstanceOf(Inet6Address.class);
    }

    @Test
    void anAllowlistedHostAnsweringGlobalAddressesPassesUnchanged() throws UnknownHostException {
        Recording delegate = new Recording(GLOBAL, AddressClasses.literal("2606:4700::1111"));

        assertThat(guard(delegate).lookupByName("guide.example", ANY))
                .containsExactly(GLOBAL, AddressClasses.literal("2606:4700::1111"));
        assertThat(delegate.lookups).containsExactly("guide.example");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ai.speckitty.charter.packs.infra.fetch.AddressClasses#refusedWithAcceptedNeighbour")
    void anAllowlistedHostAnsweringAnyNonGlobalClassIsRefused(
            String addressClass, InetAddress refused, InetAddress accepted) throws UnknownHostException {
        assertThatThrownBy(() -> guard(new Recording(refused)).lookupByName("guide.example", ANY))
                .as(addressClass)
                .isInstanceOf(NonGlobalAddressException.class)
                .hasMessageContaining("guide.example");
        assertThat(guard(new Recording(accepted)).lookupByName("guide.example", ANY))
                .containsExactly(accepted);
    }

    @Test
    void aMixedAnswerWithOneGlobalAndOnePrivateAddressIsRefusedAsAWhole() {
        assertThatThrownBy(() -> guard(new Recording(GLOBAL, PRIVATE)).lookupByName("guide.example", ANY))
                .isInstanceOf(NonGlobalAddressException.class);
    }

    @Test
    void theAllowlistMatchIgnoresCaseAndATrailingDot() {
        assertThatThrownBy(() -> guard(new Recording(PRIVATE)).lookupByName("GUIDE.Example.", ANY))
                .isInstanceOf(NonGlobalAddressException.class);
    }

    @Test
    void aHostOffTheAllowlistPassesThroughUntouchedEvenWhenPrivate() throws UnknownHostException {
        assertThat(guard(new Recording(PRIVATE)).lookupByName("intranet.example", ANY))
                .containsExactly(PRIVATE);
        assertThat(guard(new Recording(PRIVATE)).lookupByName("guide.example.evil.example", ANY))
                .containsExactly(PRIVATE);
    }

    @Test
    void theEmptyAllowlistGuardsNothing() throws UnknownHostException {
        GlobalAddressResolverProvider.Guard empty = new GlobalAddressResolverProvider.Guard(
                new Recording(PRIVATE), () -> GuideAllowlist.EMPTY, new GuardRefusals());

        assertThat(empty.lookupByName("guide.example", ANY)).containsExactly(PRIVATE);
    }

    @Test
    void reverseLookupsAreDelegated() throws UnknownHostException {
        assertThat(guard(new Recording(GLOBAL)).lookupByAddress(new byte[] {10, 0, 0, 1}))
                .isEqualTo("reverse.example");
    }

    @Test
    void theProviderIsTheOneTheJdkServiceLoaderFinds() {
        InetAddressResolverProvider provider = ServiceLoader.load(InetAddressResolverProvider.class)
                .findFirst()
                .orElseThrow();

        assertThat(provider).isInstanceOf(GlobalAddressResolverProvider.class);
        assertThat(provider.name()).contains("guide-fetch");
    }

    /**
     * End to end through the JVM: the provider the JDK loaded guards {@code localhost} once it is allowlisted and
     * leaves it alone when it is not. The JDK answers a refused {@code localhost} lookup with the loopback address
     * anyway ({@code InetAddress.getAddressesFromNameService}), so the guard's own refusal record is what shows it
     * acted. Each lookup uses a fresh random-case spelling, which the hosts file matches case-insensitively, so the
     * JVM's address cache cannot answer in the guard's place. {@code localhost} is answered from the hosts file: no
     * socket is opened.
     */
    @Test
    void theInstalledProviderGuardsAllowlistedLookupsOfTheWholeJvm() throws UnknownHostException {
        GuideAllowlist before = GlobalAddressResolverProvider.installed();
        String guarded = randomCaseLocalhost("");
        String unguarded = randomCaseLocalhost(guarded);
        long started = System.nanoTime();
        try {
            GlobalAddressResolverProvider.install(GuideAllowlist.of(List.of("localhost")));
            InetAddress.getAllByName(guarded);
            assertThat(GlobalAddressResolverProvider.refusals().refusedSince("localhost", started))
                    .isTrue();

            GlobalAddressResolverProvider.install(GuideAllowlist.EMPTY);
            long unguardedStarted = System.nanoTime();
            assertThat(InetAddress.getAllByName(unguarded)).isNotEmpty().allMatch(InetAddress::isLoopbackAddress);
            assertThat(GlobalAddressResolverProvider.refusals().refusedSince("localhost", unguardedStarted))
                    .isFalse();
        } finally {
            GlobalAddressResolverProvider.install(before);
        }
    }

    @Test
    void aRefusalIsRecordedForItsNormalisedHost() {
        GuardRefusals refusals = new GuardRefusals();
        long started = System.nanoTime();

        assertThatThrownBy(() -> new GlobalAddressResolverProvider.Guard(new Recording(PRIVATE), () -> GUIDES, refusals)
                        .lookupByName("Guide.Example.", ANY))
                .isInstanceOf(NonGlobalAddressException.class);
        assertThat(refusals.refusedSince("guide.example", started)).isTrue();
        assertThat(refusals.refusedSince("other.example", started)).isFalse();
        assertThat(refusals.refusedSince("guide.example", System.nanoTime() + 1_000_000_000L))
                .isFalse();
    }

    @Test
    void theJdkInstalledThisProviderAsTheJvmResolver() {
        assertThat(GlobalAddressResolverProvider.isTheJvmResolver()).isTrue();
    }

    @Test
    void aNonEmptyAllowlistIsRefusedWhenTheGuardIsNotTheJvmResolverAndTheEmptyOneIsNot() {
        GuideAllowlist before = GlobalAddressResolverProvider.installed();
        try {
            assertThatThrownBy(() -> GlobalAddressResolverProvider.installRequiringGuard(GUIDES, () -> false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rebinding");
            assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(before);

            GlobalAddressResolverProvider.installRequiringGuard(GuideAllowlist.EMPTY, () -> false);
            assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(GuideAllowlist.EMPTY);
            GlobalAddressResolverProvider.installRequiringGuard(GUIDES, () -> true);
            assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(GUIDES);
        } finally {
            GlobalAddressResolverProvider.install(before);
        }
    }

    private static GlobalAddressResolverProvider.Guard guard(InetAddressResolver delegate) {
        return new GlobalAddressResolverProvider.Guard(delegate, () -> GUIDES, new GuardRefusals());
    }

    private static String randomCaseLocalhost(String other) {
        StringBuilder spelling = new StringBuilder();
        String plain;
        do {
            spelling.setLength(0);
            for (char ch : "localhost".toCharArray()) {
                spelling.append(ThreadLocalRandom.current().nextBoolean() ? Character.toUpperCase(ch) : ch);
            }
            plain = spelling.toString();
        } while (plain.equals(plain.toLowerCase(Locale.ROOT)) || plain.equals(other));
        return plain;
    }

    /** A delegate that answers every name with fixed addresses and records the names asked. */
    static final class Recording implements InetAddressResolver {
        private final List<InetAddress> answer;
        final List<String> lookups = new ArrayList<>();

        Recording(InetAddress... answer) {
            this.answer = List.of(answer);
        }

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) {
            lookups.add(host);
            return answer.stream();
        }

        @Override
        public String lookupByAddress(byte[] addr) {
            return "reverse.example";
        }
    }
}
