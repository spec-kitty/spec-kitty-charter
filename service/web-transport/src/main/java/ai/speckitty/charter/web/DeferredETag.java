package ai.speckitty.charter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Puts the {@code ETag} a handler offered on the {@code 200} as its body is written, so that the service's own
 * {@code If-None-Match} rule ({@code EntityTags.noneMatch}, applied by the handler) is the only one applied. Spring checks
 * a {@code 200} whose entity carries an {@code ETag} against {@code If-None-Match} before writing it and answers
 * {@code 304} on a match, but it skips what it cannot parse, so a value that is not an entity-tag list could be a
 * {@code 304} where the contract answers {@code 200}, and it does not match {@code *} on a {@code GET}. That check has
 * run by the time this advice does; the tag is the one the handler offered on the request, and an answer whose handler
 * offered none is left as it is. The tag is written only on a {@code 2xx}: a problem answered after the offer (a refusal
 * or a failure while the body is built) carries none. The offer is read once and cleared.
 *
 * <p>It serves every inbound adapter that answers a {@code 200} with an {@code ETag}: the pack document read, and the
 * asset reads of the artifacts edge, the payload's included.
 */
@ControllerAdvice
public class DeferredETag implements ResponseBodyAdvice<Object> {

    private static final String ATTRIBUTE = DeferredETag.class.getName() + ".etag";

    /** For Spring, which finds the advice by component scan. */
    public DeferredETag() {}

    /**
     * Hands the quoted tag of the answer being built to the advice that writes it.
     *
     * @param request the request being answered
     * @param etag the strong entity tag, double quotes included
     */
    public static void offer(HttpServletRequest request, String etag) {
        request.setAttribute(ATTRIBUTE, etag);
    }

    /**
     * Hands the quoted tag of the answer being built to the advice that writes it, for the request the current thread
     * is handling.
     *
     * @param etag the strong entity tag, double quotes included
     * @throws IllegalStateException when the thread handles no servlet request
     */
    public static void offer(String etag) {
        if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes servlet) {
            offer(servlet.getRequest(), etag);
            return;
        }
        throw new IllegalStateException("no servlet request is being handled");
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servlet
                && servlet.getServletRequest().getAttribute(ATTRIBUTE) instanceof String etag) {
            // Read once: the attribute is cleared, so no later body written for the same request picks it up.
            servlet.getServletRequest().removeAttribute(ATTRIBUTE);
            if (successful(response)) {
                response.getHeaders().setETag(etag);
            }
        }
        return body;
    }

    /**
     * Whether the answer being written is a {@code 2xx}: a handler offers its tag before its body is built, so a
     * refusal or a failure raised after the offer is answered by an exception handler, and that problem must not carry
     * the tag of the {@code 200} it replaced.
     */
    private static boolean successful(ServerHttpResponse response) {
        return response instanceof ServletServerHttpResponse servlet
                && HttpStatusCode.valueOf(servlet.getServletResponse().getStatus())
                        .is2xxSuccessful();
    }
}
