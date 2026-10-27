package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveReference;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The directive read use case over in-memory ports: what is returned (relationships populated), and which refusal an absence becomes. */
class DirectiveReadServiceTest {

    private static final PackIdentifier PACK_A = new PackIdentifier("pack-a");
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    private static final DirectiveIdentifier ALPHA = new DirectiveIdentifier("ALPHA");

    private final Directive alpha = directiveWithRelationships(PACK_A, "ALPHA");
    private final Directive beta = directive(PACK_A, "BETA");
    private final Directive other = directive(PACK_B, "OTHER");
    private final Map<PackIdentifier, List<Directive>> stored =
            Map.of(PACK_A, List.of(alpha, beta), PACK_B, List.of(other));

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final AtomicInteger digests = new AtomicInteger();
    private final DirectiveReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private DirectiveReadService serviceDigesting(PackSources sources) {
        return new DirectiveReadService(
                new InMemoryPacks(),
                new InMemoryDirectives(),
                new ConsistentRead(sources),
                ignored -> PackRevision.compute(List.of()));
    }

    private DirectiveReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    @Test
    void listsOnlyTheAskedPacksDirectives() {
        assertThat(service.directivesOf(PACK_A).value()).containsExactly(alpha, beta);
        assertThat(service.directivesOf(PACK_B).value()).containsExactly(other).doesNotContain(alpha, beta);
    }

    @Test
    void readsOneDirectiveOfTheAskedPack() {
        assertThat(service.directive(PACK_A, ALPHA).value()).isEqualTo(alpha);
    }

    @Test
    void everyReturnedDirectiveCarriesItsRelationshipsPopulated() {
        // FR-006: the list read returns each directive with its originating relationships derived from its fields.
        Directive read = service.directivesOf(PACK_A).value().stream()
                .filter(d -> d.id().equals(ALPHA))
                .findFirst()
                .orElseThrow();
        assertThat(read.relationships()).isNotEmpty();
        assertThat(read.relationships()).isEqualTo(alpha.relationships());
        // The single read populates them the same way.
        assertThat(service.directive(PACK_A, ALPHA).value().relationships()).isEqualTo(alpha.relationships());
    }

    @Test
    void anAbsentPackIsAClientSideNotFoundNotAnInternalError() {
        // Control: the present pack answers, so the refusal is the absence's, not a broken setup.
        assertThat(service.directivesOf(PACK_A).value()).isNotEmpty();

        assertThatThrownBy(() -> service.directivesOf(ABSENT)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
            assertThat(r.getCause())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        });
        assertThatThrownBy(() -> service.directive(ABSENT, ALPHA))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void anAbsentDirectiveIsNotFoundAndDoesNotFallBackToAnotherPack() {
        assertThatThrownBy(() -> service.directive(PACK_A, new DirectiveIdentifier("OTHER")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.DIRECTIVE_NOT_FOUND));
    }

    @Test
    void everyReadDigestsTwiceAndAnswersTheDigestItVerified() {
        assertThat(service.directivesOf(PACK_A).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(2);
        assertThat(service.directive(PACK_A, ALPHA).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(4);
    }

    @Test
    void changedInputsBeatAnAbsentPackAndAnAbsentDirective() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().directivesOf(ABSENT))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the directives were read; read them again.");
        assertThatThrownBy(() -> serviceWhoseInputsChange().directive(PACK_A, new DirectiveIdentifier("MISSING")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void changedInputsRefuseASuccessfulReadToo() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().directivesOf(PACK_A))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    private static PackDescriptor descriptor(PackIdentifier pack) {
        return new PackDescriptor(
                pack.value(), "1.0.0", Optional.empty(), pack.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Directive directive(PackIdentifier pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Directive directiveWithRelationships(PackIdentifier pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .procedure("some-procedure")
                .reference(new DirectiveReference("tactic", "a-tactic", Optional.of("always"), Optional.empty()))
                .build();
    }

    private Pack packOf(PackIdentifier pack) {
        Pack.Builder builder = Pack.builder().id(pack).descriptor(descriptor(pack));
        stored.getOrDefault(pack, List.of()).forEach(builder::directive);
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

    private final class InMemoryDirectives implements DirectiveRepository {
        @Override
        public void save(Directive directive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive) {
            List<Directive> found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found.stream().filter(d -> d.id().equals(directive)).findFirst();
        }
    }
}
