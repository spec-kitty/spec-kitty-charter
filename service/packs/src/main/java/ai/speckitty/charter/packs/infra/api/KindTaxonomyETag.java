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
 * Puts the {@code ETag} on the {@code 200} of {@code GET /offering/kinds} as its body is written, for the reason
 * {@link ContractDocumentETag} gives: the service's own {@code If-None-Match} rule ({@link StrongETag#matchedBy}) is
 * the only one applied, never Spring's laxer check of an entity that carries an {@code ETag}.
 */
@ControllerAdvice(assignableTypes = OfferingController.class)
public class KindTaxonomyETag implements ResponseBodyAdvice<Object> {

    private static final String OPERATION = "getKindTaxonomy";

    private final KindTaxonomyAnswer answer;

    /**
     * An advice that tags the kind taxonomy.
     *
     * @param answer the answer whose entity tag it sets
     */
    KindTaxonomyETag(KindTaxonomyAnswer answer) {
        this.answer = answer;
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
        response.getHeaders().setETag(answer.tag().etag());
        return body;
    }
}
