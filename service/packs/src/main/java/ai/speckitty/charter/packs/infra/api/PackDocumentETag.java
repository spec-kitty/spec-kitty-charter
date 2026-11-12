package ai.speckitty.charter.packs.infra.api;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Puts the {@code ETag} on the {@code 200} of {@code GET /packs/{packId}/doc} as its body is written, so that the
 * service's own {@code If-None-Match} rule ({@link PackDocumentController}) is the only one applied. Spring checks a
 * {@code 200} whose entity carries an {@code ETag} against {@code If-None-Match} before writing it and answers
 * {@code 304} on a match, but it skips what it cannot parse and does not match {@code *} on a {@code GET}
 * ({@link ContractDocumentETag} has the same reason). That check has run by the time this advice does; the tag is the
 * one the handler offered on the request.
 */
@ControllerAdvice(assignableTypes = PackRegistrationController.class)
public class PackDocumentETag implements ResponseBodyAdvice<Object> {

    private static final String ATTRIBUTE = PackDocumentETag.class.getName() + ".etag";

    /** For Spring, which finds the advice by component scan. */
    public PackDocumentETag() {}

    /**
     * Hands the quoted tag of the answer being built to the advice that writes it.
     *
     * @param request the request being answered
     * @param etag the strong entity tag, double quotes included
     */
    static void offer(HttpServletRequest request, String etag) {
        request.setAttribute(ATTRIBUTE, etag);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        String operation = returnType.getExecutable().getName();
        return operation.equals("getPackDocument") || operation.equals("getPackDocumentMarkdown");
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
            response.getHeaders().setETag(etag);
        }
        return body;
    }
}
