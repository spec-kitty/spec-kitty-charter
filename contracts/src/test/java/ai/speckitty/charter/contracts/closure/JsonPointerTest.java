package ai.speckitty.charter.contracts.closure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonPointerTest {

    @Test
    void tokensAreEscapedAsRfc6901Requires() {
        JsonPointer pointer = JsonPointer.ROOT.child("paths").child("/a~b").child(0);
        assertThat(pointer).hasToString("/paths/~1a~0b/0");
    }

    @Test
    void theRootIsTheEmptyPointer() {
        assertThat(JsonPointer.ROOT).hasToString("");
        assertThat(JsonPointer.ROOT.isRoot()).isTrue();
        assertThat(JsonPointer.ROOT.child("a").isRoot()).isFalse();
    }

    @Test
    void aPointerIsRenderedRelativeToAnAncestor() {
        JsonPointer holder = JsonPointer.ROOT.child("paths").child("/a");
        assertThat(holder.child("allOf").child(0).relativeTo(holder)).isEqualTo("allOf/0");
        assertThat(holder.relativeTo(holder)).isEmpty();
        assertThat(JsonPointer.ROOT.child("x~y").relativeTo(holder)).isEqualTo("/x~0y");
    }
}
