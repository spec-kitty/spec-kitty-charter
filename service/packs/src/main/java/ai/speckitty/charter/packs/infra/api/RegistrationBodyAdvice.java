package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Guards a directive, paradigm or styleguide registration's body before the message converter reads it into the DTO.
 *
 * <ul>
 *   <li><b>Bounded (#310).</b> A body larger than {@link RegistrationLists#BODY_LIMIT} (1 MiB) is refused, by its
 *       {@code Content-Length} before a byte is read and otherwise one byte past the limit, so an oversized body is
 *       never parsed nor held whole. It is the toolguide's room for the fields beside its guide.
 *   <li><b>No explicit null list (#292).</b> The generated DTO reads a {@code null} list and an absent one alike, so
 *       without this the body {@code {"references": null}} would register as if it carried {@code []}; the contract
 *       types every list as an array, and {@code null} is never a value. An absent list still means empty.
 *   <li><b>No null item (#309).</b> The converter keeps a {@code null} item, and the controller's mapping would answer
 *       it {@code 500}; {@code {"references": [null]}} is refused.
 * </ul>
 *
 * <p>Each refusal is {@code 422 validation_failed} and names the list, or the limit. The body is otherwise handed on
 * unchanged, and a body that is not JSON is passed through, so the converter answers it as it answers any unreadable
 * body. The toolguide registration is guarded the same way by {@link ToolguideRegistrationBodyAdvice}, whose limit
 * makes room for its guide.
 */
@ControllerAdvice
public class RegistrationBodyAdvice extends RequestBodyAdviceAdapter {

    /** A guarded registration: its kind, as a refusal names it, and its list members, read off the generated DTO. */
    private record Guarded(String kind, List<String> lists) {
        Guarded(String kind, Class<?> dto) {
            this(kind, RegistrationLists.of(dto));
        }
    }

    /** The registrations this advice guards. */
    private static final Map<Type, Guarded> GUARDED = Map.of(
            DirectiveRegistration.class, new Guarded("directive", DirectiveRegistration.class),
            ParadigmRegistration.class, new Guarded("paradigm", ParadigmRegistration.class),
            StyleguideRegistration.class, new Guarded("styleguide", StyleguideRegistration.class));

    /** An advice over the directive, paradigm and styleguide registrations; a test constructs it directly. */
    public RegistrationBodyAdvice() {
        // Stateless: the guarded registrations are the class's own.
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return GUARDED.containsKey(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        Guarded guarded = GUARDED.get(targetType);
        if (guarded == null) {
            return inputMessage;
        }
        HttpHeaders headers = inputMessage.getHeaders();
        byte[] body = RegistrationLists.readBounded(inputMessage, RegistrationLists.BODY_LIMIT, guarded.kind());
        RegistrationLists.firstNull(body, headers.getContentType(), List.of(), guarded.lists())
                .ifPresent(nul -> {
                    throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, nul.detail());
                });
        return RegistrationLists.replay(headers, body);
    }
}
