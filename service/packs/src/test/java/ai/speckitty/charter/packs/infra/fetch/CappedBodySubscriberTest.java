package ai.speckitty.charter.packs.infra.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.infra.fetch.CappedBodySubscriber.BodyTooLargeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The guide fetcher's fail-closed byte cap, driven directly: a hand-written subscription records what the subscriber
 * asks of the publisher ({@code request(n)} and {@code cancel()}), and the test plays the publisher's part, buffer by
 * buffer. No HTTP client and no mocks: every assertion reads either the body future or that record.
 *
 * <p>Every body future is asserted done before it is joined, so a subscriber that never completes it (an
 * {@code onComplete} or {@code onError} that does nothing) fails the test instead of hanging it; the class timeout is
 * the backstop for a join this misses (it runs the test on a separate thread, because {@code join} does not abort on an
 * interrupt).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class CappedBodySubscriberTest {

    private static final int CAP = 1024 * 1024;
    private static final int CHUNK = 16 * 1024;

    /** Buffers delivered per {@code onNext}, so a body arrives across several calls as it does off the wire. */
    private static final int BUFFERS_PER_ON_NEXT = 4;

    static Stream<Arguments> bodiesWithinTheCap() {
        return Stream.of(Arguments.of("exactly the cap, in 16 KiB buffers", chunks(CAP / CHUNK, CHUNK)));
    }

    static Stream<Arguments> bodiesOverTheCap() {
        int[] lastBufferOneByteOver = chunks(CAP / CHUNK, CHUNK);
        lastBufferOneByteOver[lastBufferOneByteOver.length - 1] += 1;
        return Stream.of(
                Arguments.of("one byte over, in the last buffer", lastBufferOneByteOver),
                Arguments.of("over the cap in the first buffer", sizes(CAP + 1, CHUNK)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesWithinTheCap")
    void aBodyWithinTheCapCompletesWithExactlyItsBytesAndIsNeverCancelled(String body, int[] bufferSizes) {
        RecordingSubscription subscription = new RecordingSubscription();
        CappedBodySubscriber subscriber = new CappedBodySubscriber(CAP);
        subscriber.onSubscribe(subscription);
        byte[] expected = deliver(subscriber, bufferSizes);

        subscriber.onComplete();

        assertThat(completed(subscriber).join()).as(body).isEqualTo(expected);
        assertThat(subscription.requests).containsExactly(Long.MAX_VALUE);
        assertThat(subscription.cancels).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesOverTheCap")
    void aBodyOverTheCapFailsNamingTheCapCancelsOnceAndIgnoresTheRestOfTheStream(String body, int[] bufferSizes) {
        RecordingSubscription subscription = new RecordingSubscription();
        CappedBodySubscriber subscriber = new CappedBodySubscriber(CAP);
        subscriber.onSubscribe(subscription);
        deliver(subscriber, bufferSizes);

        assertFailedTooLarge(subscriber, body);
        assertThat(subscription.cancels).as(body).isEqualTo(1);

        // A publisher may still deliver what was in flight before it saw the cancel, and then complete.
        subscriber.onNext(List.of(ByteBuffer.wrap(bytes(CAP + 1, 7))));
        subscriber.onComplete();

        assertFailedTooLarge(subscriber, body);
        assertThat(subscription.cancels).as(body).isEqualTo(1);
        assertThat(subscription.requests).containsExactly(Long.MAX_VALUE);
    }

    @Test
    void aRefusingSubscriberCancelsOnSubscribeAndFailsBeforeReadingAnything() {
        RecordingSubscription subscription = new RecordingSubscription();
        CappedBodySubscriber subscriber = CappedBodySubscriber.refusing(CAP);

        subscriber.onSubscribe(subscription);

        assertFailedTooLarge(subscriber, "refused on subscribe");
        assertThat(subscription.cancels).isEqualTo(1);
        assertThat(subscription.requests).isEmpty();

        subscriber.onNext(List.of(ByteBuffer.wrap(bytes(CAP + 1, 3))));
        subscriber.onComplete();

        assertFailedTooLarge(subscriber, "after a stray onNext and onComplete");
        assertThat(subscription.cancels).isEqualTo(1);
        assertThat(subscription.requests).isEmpty();
    }

    @Test
    void anErrorAfterSomeBytesFailsWithThatErrorAndNotWithAPartialBody() {
        RecordingSubscription subscription = new RecordingSubscription();
        CappedBodySubscriber subscriber = new CappedBodySubscriber(CAP);
        subscriber.onSubscribe(subscription);
        deliver(subscriber, sizes(CHUNK, CHUNK));
        IOException reset = new IOException("connection reset");

        subscriber.onError(reset);

        CompletableFuture<byte[]> future = completed(subscriber);
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .cause()
                .isSameAs(reset);
        assertThat(subscription.cancels).isZero();
    }

    @Test
    void anEmptyBodyCompletesWithZeroBytes() {
        RecordingSubscription subscription = new RecordingSubscription();
        CappedBodySubscriber subscriber = new CappedBodySubscriber(CAP);
        subscriber.onSubscribe(subscription);

        subscriber.onComplete();

        assertThat(completed(subscriber).join()).isEmpty();
        assertThat(subscription.requests).containsExactly(Long.MAX_VALUE);
        assertThat(subscription.cancels).isZero();
    }

    private static void assertFailedTooLarge(CappedBodySubscriber subscriber, String when) {
        CompletableFuture<byte[]> future = result(subscriber);
        assertThat(future).as(when).isCompletedExceptionally();
        assertThatThrownBy(future::join)
                .as(when)
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(BodyTooLargeException.class)
                .hasMessage("the body exceeds " + CAP + " bytes");
    }

    private static CompletableFuture<byte[]> result(CappedBodySubscriber subscriber) {
        return subscriber.getBody().toCompletableFuture();
    }

    /** The body future, asserted done: joining one that is not would wait forever. */
    private static CompletableFuture<byte[]> completed(CappedBodySubscriber subscriber) {
        CompletableFuture<byte[]> future = result(subscriber);
        assertThat(future).as("the body future is completed").isDone();
        return future;
    }

    /**
     * Plays the publisher: hands the subscriber buffers of the given sizes, a few per {@code onNext}, and returns every
     * byte it offered in order.
     */
    private static byte[] deliver(CappedBodySubscriber subscriber, int[] bufferSizes) {
        List<byte[]> offered = new ArrayList<>();
        List<ByteBuffer> batch = new ArrayList<>();
        for (int i = 0; i < bufferSizes.length; i++) {
            byte[] chunk = bytes(bufferSizes[i], i);
            offered.add(chunk);
            batch.add(ByteBuffer.wrap(chunk));
            if (batch.size() == BUFFERS_PER_ON_NEXT) {
                subscriber.onNext(List.copyOf(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            subscriber.onNext(List.copyOf(batch));
        }
        int total = offered.stream().mapToInt(chunk -> chunk.length).sum();
        byte[] all = new byte[total];
        int at = 0;
        for (byte[] chunk : offered) {
            System.arraycopy(chunk, 0, all, at, chunk.length);
            at += chunk.length;
        }
        return all;
    }

    /** Distinct, position-dependent content, so a dropped or reordered buffer cannot pass for the right body. */
    private static byte[] bytes(int length, int seed) {
        byte[] chunk = new byte[length];
        for (int i = 0; i < length; i++) {
            chunk[i] = (byte) (seed * 31 + i);
        }
        return chunk;
    }

    private static int[] chunks(int count, int size) {
        return IntStream.generate(() -> size).limit(count).toArray();
    }

    private static int[] sizes(int... sizes) {
        return Arrays.copyOf(sizes, sizes.length);
    }

    /** A subscription that only records what it is asked: it delivers nothing and asserts nothing. */
    private static final class RecordingSubscription implements Flow.Subscription {

        private final List<Long> requests = new ArrayList<>();
        private int cancels;

        @Override
        public void request(long n) {
            requests.add(n);
        }

        @Override
        public void cancel() {
            cancels++;
        }
    }
}
