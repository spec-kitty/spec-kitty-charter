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
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmReference;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The paradigm read use case over in-memory ports (FR-014): what is returned (relationships populated, the
 * requires-to-directive edges a paradigm mints from its directive refs), and which refusal an absence becomes.
 */
class ParadigmReadServiceTest {

    private static final PackIdentifier PACK_A = new PackIdentifier("pack-a");
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    private static final ParadigmIdentifier CLEAN = new ParadigmIdentifier("clean-architecture");

    private final Paradigm clean = paradigmWithRelationships(PACK_A, "clean-architecture");
    private final Paradigm hexagonal = paradigm(PACK_A, "hexagonal");
    private final Paradigm other = paradigm(PACK_B, "other");
    private final Map<PackIdentifier, List<Paradigm>> stored =
            Map.of(PACK_A, List.of(clean, hexagonal), PACK_B, List.of(other));

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final AtomicInteger digests = new AtomicInteger();
    private final ParadigmReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private ParadigmReadService serviceDigesting(PackSources sources) {
        return new ParadigmReadService(
                new InMemoryPacks(),
                new InMemoryParadigms(),
                new ConsistentRead(sources),
                ignored -> PackRevision.compute(List.of()));
    }

    private ParadigmReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    @Test
    void listsOnlyTheAskedPacksParadigms() {
        assertThat(service.paradigmsOf(PACK_A).value()).containsExactly(clean, hexagonal);
        assertThat(service.paradigmsOf(PACK_B).value()).containsExactly(other).doesNotContain(clean, hexagonal);
    }

    @Test
    void readsOneParadigmOfTheAskedPack() {
        assertThat(service.paradigm(PACK_A, CLEAN).value()).isEqualTo(clean);
    }

    @Test
    void everyReturnedParadigmCarriesItsDirectiveRelationshipsPopulated() {
        // FR-014: the list read returns each paradigm with its originating relationships derived from its fields.
        Paradigm read = service.paradigmsOf(PACK_A).value().stream()
                .filter(p -> p.id().equals(CLEAN))
                .findFirst()
                .orElseThrow();
        assertThat(read.relationships()).isNotEmpty();
        assertThat(read.relationships()).isEqualTo(clean.relationships());
        // The single read populates them the same way, with the requires-to-directive edge present.
        Paradigm single = service.paradigm(PACK_A, CLEAN).value();
        assertThat(single.relationships()).isEqualTo(clean.relationships());
        assertThat(single.relationships())
                .anySatisfy(
                        relationship -> assertThat(relationship.to().toString()).isEqualTo("directive:ALPHA"));
    }

    @Test
    void anAbsentPackIsAClientSideNotFoundNotAnInternalError() {
        // Control: the present pack answers, so the refusal is the absence's, not a broken setup.
        assertThat(service.paradigmsOf(PACK_A).value()).isNotEmpty();

        assertThatThrownBy(() -> service.paradigmsOf(ABSENT)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
            assertThat(r.getCause())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        });
        assertThatThrownBy(() -> service.paradigm(ABSENT, CLEAN))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void anAbsentParadigmIsNotFoundAndDoesNotFallBackToAnotherPack() {
        assertThatThrownBy(() -> service.paradigm(PACK_A, new ParadigmIdentifier("other")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PARADIGM_NOT_FOUND));
    }

    @Test
    void everyReadDigestsTwiceAndAnswersTheDigestItVerified() {
        assertThat(service.paradigmsOf(PACK_A).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(2);
        assertThat(service.paradigm(PACK_A, CLEAN).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(4);
    }

    @Test
    void changedInputsBeatAnAbsentPackAndAnAbsentParadigm() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().paradigmsOf(ABSENT))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the paradigms were read; read them again.");
        assertThatThrownBy(() -> serviceWhoseInputsChange().paradigm(PACK_A, new ParadigmIdentifier("missing")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void changedInputsRefuseASuccessfulReadToo() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().paradigmsOf(PACK_A))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    private static PackDescriptor descriptor(PackIdentifier pack) {
        return new PackDescriptor(
                pack.value(), "1.0.0", Optional.empty(), pack.value(), Optional.empty(), Optional.empty());
    }

    private static Paradigm paradigm(PackIdentifier pack, String id) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .name(id)
                .summary("Summary of " + id)
                .build();
    }

    private static Paradigm paradigmWithRelationships(PackIdentifier pack, String id) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .name(id)
                .summary("Summary of " + id)
                .directiveRef("ALPHA")
                .reference(new ParadigmReference("tactic", "a-tactic"))
                .build();
    }

    private Pack packOf(PackIdentifier pack) {
        Pack.Builder builder = Pack.builder().id(pack).descriptor(descriptor(pack));
        stored.getOrDefault(pack, List.of()).forEach(builder::paradigm);
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

    private final class InMemoryParadigms implements ParadigmRepository {
        @Override
        public void save(Paradigm paradigm) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm) {
            List<Paradigm> found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found.stream().filter(p -> p.id().equals(paradigm)).findFirst();
        }
    }
}
