package ai.speckitty.charter.packs.infra.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The counting bound: what it lets through, what it refuses, and how far it ever reads into its source. */
class LimitedInputStreamTest {

    /** A source that records the most it was asked for in total. */
    private static final class Metered extends InputStream {
        private final ByteArrayInputStream data;
        private long delivered;

        Metered(int size) {
            this.data = new ByteArrayInputStream(new byte[size]);
        }

        @Override
        public int read() {
            int b = data.read();
            delivered += b < 0 ? 0 : 1;
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            int n = data.read(buffer, offset, length);
            delivered += Math.max(n, 0);
            return n;
        }

        @Override
        public int available() {
            return 7;
        }

        long delivered() {
            return delivered;
        }
    }

    @Test
    void theStrictFormDeliversExactlyTheLimitAndNeverReadsItsSourcePastIt() throws IOException {
        Metered source = new Metered(100);
        LimitedInputStream in = new LimitedInputStream(source, 10, Code.EXPANDED_TOO_LARGE, false);

        byte[] buffer = new byte[64];
        assertThat(in.read(buffer, 0, 64)).isEqualTo(10);
        assertThat(in.count()).isEqualTo(10);
        assertThatThrownBy(() -> in.read(buffer, 0, 1))
                .isInstanceOfSatisfying(
                        ArchiveLimitException.class, e -> assertThat(e.code()).isEqualTo(Code.EXPANDED_TOO_LARGE));
        assertThat(source.delivered()).isEqualTo(10);
    }

    @Test
    void theProbingFormReadsOneBeyondToTellExactlyTheLimitFromOverIt() throws IOException {
        LimitedInputStream exact = new LimitedInputStream(new Metered(10), 10, Code.BODY_TOO_LARGE, true);
        LimitedInputStream over = new LimitedInputStream(new Metered(11), 10, Code.BODY_TOO_LARGE, true);

        assertThat(exact.readAllBytes()).hasSize(10);
        assertThatThrownBy(over::readAllBytes)
                .isInstanceOfSatisfying(
                        ArchiveLimitException.class, e -> assertThat(e.code()).isEqualTo(Code.BODY_TOO_LARGE));
    }

    @Test
    void aSingleByteReadIsCountedAndEndOfStreamIsReported() throws IOException {
        LimitedInputStream in = new LimitedInputStream(new Metered(2), 5, Code.BODY_TOO_LARGE, false);

        assertThat(in.read()).isZero();
        assertThat(in.read()).isZero();
        assertThat(in.read()).isEqualTo(-1);
        assertThat(in.count()).isEqualTo(2);
    }

    @Test
    void skippingCountsAgainstTheLimit() throws IOException {
        Metered source = new Metered(100);
        LimitedInputStream in = new LimitedInputStream(source, 6, Code.EXPANDED_TOO_LARGE, false);

        assertThat(in.skip(4)).isEqualTo(4);
        assertThat(in.count()).isEqualTo(4);
        assertThat(in.skip(0)).isZero();
        assertThat(in.skip(-3)).isZero();
        assertThat(in.skip(100)).isEqualTo(2);
        assertThatThrownBy(() -> in.skip(1)).isInstanceOf(ArchiveLimitException.class);
    }

    @Test
    void aZeroLengthReadDeliversNothingAndCountsNothing() throws IOException {
        LimitedInputStream in = new LimitedInputStream(new Metered(3), 0, Code.BODY_TOO_LARGE, false);

        assertThat(in.read(new byte[4], 0, 0)).isZero();
        assertThat(in.count()).isZero();
    }

    @Test
    void availableAndCloseDelegateNothingToTheCaller() throws IOException {
        Metered source = new Metered(3);
        LimitedInputStream in = new LimitedInputStream(source, 10, Code.BODY_TOO_LARGE, false);

        assertThat(in.available()).isEqualTo(7);
        in.close();
        assertThat(in.read()).isZero();
    }
}
