package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A file the read could not read holds exactly one of a parse failure and schema violations, so it has a finding. */
class FileReadTest {

    @Test
    void anUnreadableFileHasExactlyOneOfAParseFailureAndSchemaViolations() {
        PackContents.ParseFailure parse = new PackContents.ParseFailure("a.yaml", "while scanning");
        PackContents.SchemaViolation violation = new PackContents.SchemaViolation(
                "a.yaml", List.of("x"), "type", Optional.empty(), Optional.empty(), Map.of());

        assertThatThrownBy(() -> new FileRead.Unreadable<RawGraph>("a.yaml", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.yaml");
        assertThatThrownBy(() -> new FileRead.Unreadable<RawGraph>("a.yaml", parse, List.of(violation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.yaml");
        assertThat(new FileRead.Unreadable<RawGraph>("a.yaml", parse, List.of()).violations())
                .isEmpty();
        assertThat(new FileRead.Unreadable<RawGraph>("a.yaml", null, List.of(violation)).parse())
                .isNull();
    }
}
