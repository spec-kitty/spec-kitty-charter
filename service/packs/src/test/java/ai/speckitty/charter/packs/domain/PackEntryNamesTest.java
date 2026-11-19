package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link PackEntryNames#canBeAPack}: the entry rule the service's listing and pack-check share (#478). */
class PackEntryNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"built-in", "org-base", "project", "a.b", "pack-old"})
    void aPlainNameCanBeAPack(String name) {
        assertThat(PackEntryNames.canBeAPack(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {".staging", ".git", ".pack-commit-1", "built-in.pack-old-123", "x.pack-old-"})
    void aHiddenNameOrAnAsideCopyCannot(String name) {
        assertThat(PackEntryNames.canBeAPack(name)).isFalse();
    }
}
