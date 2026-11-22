package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Origin;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PackOrigins: the packs domain names the element a reason is about (#614, R-o)")
class PackOriginsTest {

    private static final PackIdentifier ACME = new PackIdentifier("acme");

    private static final RefusalPack ACME_NAMED =
            new RefusalPack(new PackUlid("01ACME00000000000000000000"), ACME, "Acme");

    private static final RefusalPack BASE_NAMED =
            new RefusalPack(new PackUlid("01BASE00000000000000000000"), new PackIdentifier("base"), "Base");

    @Test
    @DisplayName("a pack is its pack id")
    void aPackIsItsId() {
        assertThat(PackOrigins.of(ACME)).isEqualTo(new Origin("acme"));
    }

    @Test
    @DisplayName("an artifact of a pack is the pack id and the URN, pack-qualified as a reference writes it")
    void anArtifactIsThePackIdAndTheUrn() {
        Urn directive = new Urn(NodeKind.DIRECTIVE, "DIR-1");

        assertThat(PackOrigins.of(ACME, directive)).isEqualTo(new Origin("acme::" + directive));
        assertThat(PackOrigins.of(ACME, directive)).isNotEqualTo(PackOrigins.of(ACME));
    }

    @Test
    @DisplayName("an artifact's origin parses back, as a pack-qualified reference, to the same pack and URN")
    void anArtifactOriginRoundTripsThroughTheReferenceGrammar() {
        Urn directive = new Urn(NodeKind.DIRECTIVE, "DIR-1");

        PackQualifiedReference parsed =
                PackQualifiedReference.parse(PackOrigins.of(ACME, directive).id());

        assertThat(parsed.packName()).isEqualTo(Optional.of(ACME.value()));
        assertThat(parsed.asUrn()).contains(directive);
    }

    @Test
    @DisplayName("the first of the packs a fault names is the pack that broke the rule first; none names no element")
    void theFirstNamedPackIsTheOrigin() {
        assertThat(PackOrigins.firstOf(List.of(BASE_NAMED, ACME_NAMED))).isEqualTo(new Origin("base"));
        assertThat(PackOrigins.firstOf(List.of())).isEqualTo(Origin.NONE);
    }
}
