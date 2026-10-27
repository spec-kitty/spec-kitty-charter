package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Payload value object")
class PayloadTest {

    @Test
    @DisplayName("two payloads are equal by content, not by identity")
    void equalByContent() {
        Payload one = Payload.of(new byte[] {1, 2, 3});
        Payload same = Payload.of(new byte[] {1, 2, 3});
        Payload other = Payload.of(new byte[] {1, 2, 4});

        assertThat(one).isNotSameAs(same).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("mutating the input array or a bytes() result does not change the payload")
    void neverExposesItsArray() {
        byte[] input = {10, 20, 30};
        Payload payload = Payload.of(input);

        input[0] = 99;
        payload.bytes()[1] = 99;

        assertThat(payload.bytes()).containsExactly(10, 20, 30);
        assertThat(payload).isEqualTo(Payload.of(new byte[] {10, 20, 30}));
    }

    @Test
    @DisplayName("an empty payload is allowed")
    void emptyIsAllowed() {
        Payload empty = Payload.of(new byte[0]);

        assertThat(empty.size()).isZero();
        assertThat(empty.bytes()).isEmpty();
        assertThat(empty.sha256()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("NUL bytes, CR/LF pairs and a missing final newline round-trip through bytes()")
    void binaryBytesRoundTrip() {
        byte[] binary = {0, 'O', 'T', 'T', 'O', 0, '\r', '\n', (byte) 0xFF, '\r', '\n', 0};

        Payload payload = Payload.of(binary);

        assertThat(payload.bytes()).containsExactly(binary);
        assertThat(payload.size()).isEqualTo(binary.length);
    }

    @Test
    @DisplayName("the digest is lower-case hex SHA-256 and toString names size and digest, never the bytes")
    void diagnosticsNeverPrintTheBytes() {
        Payload payload = Payload.of("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(payload.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(payload.toString())
                .contains("3 bytes")
                .contains(payload.sha256())
                .doesNotContain("abc");
    }
}
