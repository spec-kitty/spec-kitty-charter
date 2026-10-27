package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The styleguide read use case over in-memory ports (FR-013): what is returned (relationships always empty for a
 * styleguide), and which refusal an absence becomes. A faithful copy of {@code DirectiveReadServiceTest}, with the
 * relationships assertion flipped: a styleguide mints none.
 */
class StyleguideReadServiceTest {

    private static final PackIdentifier PACK_A = new PackIdentifier("pack-a");
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    private static final StyleguideIdentifier JAVA_STYLE = new StyleguideIdentifier("java-style");

    private final Styleguide javaStyle = styleguideWithFields(PACK_A, "java-style");
    private final Styleguide docsStyle = styleguide(PACK_A, "docs-style");
    private final Styleguide other = styleguide(PACK_B, "other-style");
    private final Map<PackIdentifier, List<Styleguide>> stored =
            Map.of(PACK_A, List.of(javaStyle, docsStyle), PACK_B, List.of(other));

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final AtomicInteger digests = new AtomicInteger();
    private final StyleguideReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private StyleguideReadService serviceDigesting(PackSources sources) {
        return new StyleguideReadService(
                new InMemoryPacks(),
                new InMemoryStyleguides(),
                new ConsistentRead(sources),
                ignored -> PackRevision.compute(List.of()));
    }

    private StyleguideReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    @Test
    void listsOnlyTheAskedPacksStyleguides() {
        assertThat(service.styleguidesOf(PACK_A).value()).containsExactly(javaStyle, docsStyle);
        assertThat(service.styleguidesOf(PACK_B).value()).containsExactly(other).doesNotContain(javaStyle, docsStyle);
    }

    @Test
    void readsOneStyleguideOfTheAskedPackValueEqual() {
        Styleguide read = service.styleguide(PACK_A, JAVA_STYLE).value();
        assertThat(read).isEqualTo(javaStyle);
        // Beyond identity equality, the projected fields round-trip value-equal (SC-001).
        assertThat(read.title()).isEqualTo(javaStyle.title());
        assertThat(read.scope()).isEqualTo(javaStyle.scope());
        assertThat(read.principles()).isEqualTo(javaStyle.principles());
        assertThat(read.tooling()).isEqualTo(javaStyle.tooling());
        assertThat(read.references()).isEqualTo(javaStyle.references());
    }

    @Test
    void everyReturnedStyleguideCarriesAnEmptyRelationshipsList() {
        // FR-013: a styleguide mints no relationships (bare citations), so the read returns an empty list, never null.
        Styleguide listed = service.styleguidesOf(PACK_A).value().stream()
                .filter(s -> s.id().equals(JAVA_STYLE))
                .findFirst()
                .orElseThrow();
        assertThat(listed.relationships()).isEmpty();
        assertThat(service.styleguide(PACK_A, JAVA_STYLE).value().relationships())
                .isEmpty();
    }

    @Test
    void anAbsentPackIsAClientSideNotFoundNotAnInternalError() {
        // Control: the present pack answers, so the refusal is the absence's, not a broken setup.
        assertThat(service.styleguidesOf(PACK_A).value()).isNotEmpty();

        assertThatThrownBy(() -> service.styleguidesOf(ABSENT)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
            assertThat(r.getCause())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        });
        assertThatThrownBy(() -> service.styleguide(ABSENT, JAVA_STYLE))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void anAbsentStyleguideIsNotFoundAndDoesNotFallBackToAnotherPack() {
        assertThatThrownBy(() -> service.styleguide(PACK_A, new StyleguideIdentifier("other-style")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STYLEGUIDE_NOT_FOUND));
    }

    @Test
    void everyReadDigestsTwiceAndAnswersTheDigestItVerified() {
        assertThat(service.styleguidesOf(PACK_A).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(2);
        assertThat(service.styleguide(PACK_A, JAVA_STYLE).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(4);
    }

    @Test
    void changedInputsBeatAnAbsentPackAndAnAbsentStyleguide() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().styleguidesOf(ABSENT))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the styleguides were read; read them again.");
        assertThatThrownBy(() -> serviceWhoseInputsChange().styleguide(PACK_A, new StyleguideIdentifier("missing")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void changedInputsRefuseASuccessfulReadToo() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().styleguidesOf(PACK_A))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    private static PackDescriptor descriptor(PackIdentifier pack) {
        return new PackDescriptor(
                pack.value(), "1.0.0", Optional.empty(), pack.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Styleguide styleguide(PackIdentifier pack, String id) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .scope(StyleguideScope.CODE)
                .principle("Prefer clarity")
                .build();
    }

    private static Styleguide styleguideWithFields(PackIdentifier pack, String id) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .scope(StyleguideScope.CODE)
                .principle("Prefer immutability")
                .principle("Fail fast")
                .tooling(Map.of("spotless", "google-java-format"))
                .reference("Clean Code ch.3")
                .appliesToLanguage("java")
                .build();
    }

    private Pack packOf(PackIdentifier pack) {
        Pack.Builder builder = Pack.builder().id(pack).descriptor(descriptor(pack));
        stored.getOrDefault(pack, List.of()).forEach(builder::styleguide);
        return builder.build();
    }

    private final class InMemoryPacks implements PackRepository {
        @Override
        public Pack load(PackIdentifier pack) {
            if (!stored.containsKey(pack)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return packOf(pack);
        }
    }

    private final class InMemoryStyleguides implements StyleguideRepository {
        @Override
        public void save(Styleguide styleguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier styleguide) {
            List<Styleguide> found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found.stream().filter(s -> s.id().equals(styleguide)).findFirst();
        }
    }
}
