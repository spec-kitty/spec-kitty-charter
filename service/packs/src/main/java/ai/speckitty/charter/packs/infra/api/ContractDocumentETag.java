package ai.speckitty.charter.packs.infra.api;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Puts the {@code ETag} on the {@code 200} of {@code GET /openapi.yaml} as its body is written, so the service's own
 * {@code If-None-Match} rule ({@link ContractDocument#matchedBy}) is the only one applied. Spring checks a {@code 200}
 * whose entity carries an {@code ETag} against {@code If-None-Match} before writing it and answers {@code 304} on a
 * match, but it skips the characters it cannot parse, so {@code garbage "sha256:…"} or a lower-case {@code w/} would
 * be a 304 where the contract answers 200, and it does not match {@code *} on a GET. That check has run by the time
 * this advice does.
 */
@ControllerAdvice(assignableTypes = InfoController.class)
public class ContractDocumentETag implements ResponseBodyAdvice<Object> {

    private static final String OPERATION = "getOpenApiDocument";

    private final ContractDocument contract;

    /**
     * An advice that tags the contract document.
     *
     * @param contract the document whose entity tag it sets
     */
    public ContractDocumentETag(ContractDocument contract) {
        this.contract = contract;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getExecutable().getName().equals(OPERATION);
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        response.getHeaders().setETag(contract.etag());
        return body;
    }
}
