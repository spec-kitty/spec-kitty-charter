package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.io.InputStream;
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
 * Refuses a directive, paradigm or styleguide registration that sends a list as an explicit {@code null}, before the
 * message converter reads the body into the DTO (#292). The generated DTO reads a {@code null} list and an absent one
 * alike, so without this the body {@code {"references": null}} would register as if it carried {@code []}; the
 * contract types every list as an array, and {@code null} is never a value. An absent list still means empty.
 *
 * <p>The refusal is {@code 422 validation_failed} and names the list. The body is otherwise handed on unchanged, and
 * a body that is not JSON is passed through, so the converter answers it as it answers any unreadable body. The
 * toolguide registration is guarded the same way by {@link ToolguideRegistrationBodyAdvice}, which also bounds its
 * body first.
 */
@ControllerAdvice
public class RegistrationBodyAdvice extends RequestBodyAdviceAdapter {

    /** The list members of each registration this advice guards, read off the generated DTOs. */
    private static final Map<Type, List<String>> LISTS = Map.of(
            DirectiveRegistration.class, RegistrationLists.of(DirectiveRegistration.class),
            ParadigmRegistration.class, RegistrationLists.of(ParadigmRegistration.class),
            StyleguideRegistration.class, RegistrationLists.of(StyleguideRegistration.class));

    /** An advice over the directive, paradigm and styleguide registrations; a test constructs it directly. */
    public RegistrationBodyAdvice() {
        // Stateless: the guarded lists are the class's own.
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return LISTS.containsKey(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        byte[] body;
        try (InputStream in = inputMessage.getBody()) {
            body = in.readAllBytes();
        }
        RegistrationLists.firstExplicitNull(body, headers.getContentType(), LISTS.getOrDefault(targetType, List.of()))
                .ifPresent(member -> {
                    throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, RegistrationLists.nullListDetail(member));
                });
        return RegistrationLists.replay(headers, body);
    }
}
