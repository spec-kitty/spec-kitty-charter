package ai.speckitty.charter.packs.infra.fetch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.jspecify.annotations.Nullable;

/**
 * Collects a response body while it streams and fails as soon as it passes the cap, cancelling the subscription so
 * the rest is never read (NFR-004: a 1 MiB streamed cap, not a check after buffering everything).
 */
final class CappedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    private final long maxBytes;
    private final boolean refuseAtOnce;
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.@Nullable Subscription subscription;

    CappedBodySubscriber(long maxBytes) {
        this(maxBytes, false);
    }

    private CappedBodySubscriber(long maxBytes, boolean refuseAtOnce) {
        this.maxBytes = maxBytes;
        this.refuseAtOnce = refuseAtOnce;
    }

    /**
     * A subscriber that refuses the body before reading any of it: for a response whose declared
     * {@code Content-Length} already passes the cap.
     *
     * @param maxBytes the cap, named in the failure
     * @return the subscriber; it cancels its subscription and fails as soon as it is subscribed
     */
    static CappedBodySubscriber refusing(long maxBytes) {
        return new CappedBodySubscriber(maxBytes, true);
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (refuseAtOnce) {
            subscription.cancel();
            result.completeExceptionally(new BodyTooLargeException(maxBytes));
            return;
        }
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (result.isDone()) {
            return;
        }
        for (ByteBuffer buffer : buffers) {
            if ((long) body.size() + buffer.remaining() > maxBytes) {
                Flow.Subscription current = subscription;
                if (current != null) {
                    current.cancel();
                }
                result.completeExceptionally(new BodyTooLargeException(maxBytes));
                return;
            }
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            body.writeBytes(chunk);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        result.complete(body.toByteArray());
    }

    /** The body passed the cap. */
    static final class BodyTooLargeException extends IOException {

        @Serial
        private static final long serialVersionUID = 1L;

        BodyTooLargeException(long maxBytes) {
            super("the body exceeds " + maxBytes + " bytes");
        }
    }
}
