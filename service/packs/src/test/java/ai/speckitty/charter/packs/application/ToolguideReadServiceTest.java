package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The toolguide read use case over in-memory ports: what is returned (relationships empty), and which refusal an absence becomes. */
class ToolguideReadServiceTest {

    private static final PackIdentifier PACK_A = new PackIdentifier("pack-a");
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    private static final ToolguideIdentifier GIT = new ToolguideIdentifier("git");

    private final Toolguide git = toolguide(PACK_A, "git", "2026-01-01");
    private final Toolguide make = toolguide(PACK_A, "make", null);
    private final Toolguide other = toolguide(PACK_B, "other", null);
    private final Map<PackIdentifier, List<Toolguide>> stored =
            Map.of(PACK_A, List.of(git, make), PACK_B, List.of(other));

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final AtomicInteger digests = new AtomicInteger();
    private final ToolguideReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private ToolguideReadService serviceDigesting(PackSources sources) {
        return new ToolguideReadService(
                new InMemoryPacks(),
                new InMemoryToolguides(),
                new ConsistentRead(sources),
                ignored -> PackRevision.compute(List.of()));
    }

    private ToolguideReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    @Test
    void listsOnlyTheAskedPacksToolguides() {
        assertThat(service.toolguidesOf(PACK_A).value()).containsExactly(git, make);
        assertThat(service.toolguidesOf(PACK_B).value()).containsExactly(other).doesNotContain(git, make);
    }

    @Test
    void readsOneToolguideOfTheAskedPack() {
        Toolguide read = service.toolguide(PACK_A, GIT).value();
        assertThat(read).isEqualTo(git);
        // Value-equality beyond identity: the round trip preserves every field, including the present optional.
        assertThat(read.tool()).isEqualTo(git.tool());
        assertThat(read.title()).isEqualTo(git.title());
        assertThat(read.guidePath()).isEqualTo(git.guidePath());
        assertThat(read.summary()).isEqualTo(git.summary());
        assertThat(read.commands()).isEqualTo(git.commands());
        assertThat(read.appliesToLanguages()).isEqualTo(git.appliesToLanguages());
        assertThat(read.references()).isEqualTo(git.references());
        assertThat(read.lastUpdated()).isEqualTo(git.lastUpdated()).contains("2026-01-01");
    }

    @Test
    void readsOneToolguideWithAnAbsentOptionalLastUpdated() {
        // SC-001 control: the present/absent optional round-trips both ways.
        Toolguide read =
                service.toolguide(PACK_A, new ToolguideIdentifier("make")).value();
        assertThat(read.lastUpdated()).isEmpty();
    }

    @Test
    void everyReturnedToolguideMintsNoRelationships() {
        // FR-015 / data-model: a toolguide's bare-string citations are not typed edges, so it originates no edges.
        assertThat(service.toolguidesOf(PACK_A).value())
                .allSatisfy(t -> assertThat(t.relationships()).isEmpty());
        assertThat(service.toolguide(PACK_A, GIT).value().relationships()).isEmpty();
    }

    @Test
    void anAbsentPackIsAClientSideNotFoundNotAnInternalError() {
        // Control: the present pack answers, so the refusal is the absence's, not a broken setup.
        assertThat(service.toolguidesOf(PACK_A).value()).isNotEmpty();

        assertThatThrownBy(() -> service.toolguidesOf(ABSENT)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
            assertThat(r.getCause())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        });
        assertThatThrownBy(() -> service.toolguide(ABSENT, GIT))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void anAbsentToolguideIsNotFoundAndDoesNotFallBackToAnotherPack() {
        // Control: the present pack has git, so the refusal is the toolguide's absence, not a broken fixture.
        assertThat(service.toolguide(PACK_A, GIT).value()).isEqualTo(git);
        assertThatThrownBy(() -> service.toolguide(PACK_A, new ToolguideIdentifier("other")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.TOOLGUIDE_NOT_FOUND));
    }

    @Test
    void everyReadDigestsTwiceAndAnswersTheDigestItVerified() {
        assertThat(service.toolguidesOf(PACK_A).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(2);
        assertThat(service.toolguide(PACK_A, GIT).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(4);
    }

    @Test
    void changedInputsBeatAnAbsentPackAndAnAbsentToolguide() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().toolguidesOf(ABSENT))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the toolguides were read; read them again.");
        assertThatThrownBy(() -> serviceWhoseInputsChange().toolguide(PACK_A, new ToolguideIdentifier("missing")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void changedInputsRefuseASuccessfulReadToo() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().toolguidesOf(PACK_A))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    private static PackDescriptor descriptor(PackIdentifier pack) {
        return new PackDescriptor(
                pack.value(), "1.0.0", Optional.empty(), pack.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Toolguide toolguide(PackIdentifier pack, String id, String lastUpdated) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .tool(id)
                .title("How to use " + id)
                .guidePath(id + ".md")
                .summary("Using " + id + " under governance.")
                .command(id + " status")
                .appliesToLanguage("java")
                .lastUpdated(lastUpdated)
                .reference("docs/" + id + ".md")
                .build();
    }

    private Pack packOf(PackIdentifier pack) {
        Pack.Builder builder = Pack.builder().id(pack).descriptor(descriptor(pack));
        stored.getOrDefault(pack, List.of()).forEach(builder::toolguide);
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

    private final class InMemoryToolguides implements ToolguideRepository {
        @Override
        public void save(Toolguide toolguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier toolguide) {
            List<Toolguide> found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found.stream().filter(t -> t.id().equals(toolguide)).findFirst();
        }
    }
}
