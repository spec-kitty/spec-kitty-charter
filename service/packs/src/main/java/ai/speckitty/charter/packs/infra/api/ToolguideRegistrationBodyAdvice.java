package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Guards a toolguide registration's body before the message converter reads it into the DTO (FR-010, NFR-005).
 *
 * <ul>
 *   <li><b>Bounded.</b> A body larger than {@link #bodyLimit} bytes is refused, by its {@code Content-Length} before a
 *       byte is read and otherwise while it is read, so an oversized body is never parsed nor held whole. The limit
 *       follows the guide cap ({@code charter.guide-fetch.max-size}, 1 MiB by default): six bytes of JSON for each
 *       byte of guide, the most a JSON escape ({@code A}) spells one in, and 1 MiB for the other fields; 7 MiB by
 *       default. The guide itself is then held to its cap by {@link ToolguideController}.
 *   <li><b>No explicit null.</b> The generated DTO reads a {@code null} and an absent field alike, so the body
 *       {@code {"guide": null, "guideUri": "https://..."}} would otherwise pass as a registration by URI, although
 *       {@code guide} is typed {@code string} in the contract. A registration leaves out the guide field it does not
 *       carry; an explicit {@code null} for {@code guide} or {@code guideUri} is refused. So is one for a list
 *       ({@code commands}, {@code appliesToLanguages}, {@code references}): an absent list is empty, and {@code null}
 *       is never a value (#292, as {@link RegistrationBodyAdvice} does for the other registrations).
 * </ul>
 *
 * <p>Both are {@code 422 validation_failed}. The body is handed on unchanged. A body that is not JSON is passed
 * through, so the message converter answers it as it answers any unreadable body. Only the two toolguide
 * registrations are guarded; every other body is left to the converter.
 */
@ControllerAdvice
public class ToolguideRegistrationBodyAdvice extends RequestBodyAdviceAdapter {

    /** The registration's guide fields; an explicit {@code null} is refused for these and for the lists. */
    static final List<String> GUIDE_FIELDS = List.of("guide", "guideUri");

    /** The guide fields, then the registration's list members, in the order an explicit {@code null} is looked for. */
    private static final List<String> NOT_NULL = Stream.concat(
                    GUIDE_FIELDS.stream(), RegistrationLists.of(ToolguideRegistration.class).stream())
            .toList();

    /** JSON bytes allowed per byte of guide: a JSON escape of one character is six. */
    private static final int ESCAPE_FACTOR = 6;

    /** The room left for the fields beside the guide. */
    private static final long OTHER_FIELDS = 1024L * 1024;

    private final long bodyLimit;

    /** An advice with the default guide cap; a test constructs it directly. */
    public ToolguideRegistrationBodyAdvice() {
        this(DataSize.ofBytes(ToolguideController.MAX_GUIDE_BYTES));
    }

    /**
     * An advice whose body limit follows the guide cap.
     *
     * @param maxGuideSize the guide cap, {@code charter.guide-fetch.max-size}
     */
    @Autowired
    public ToolguideRegistrationBodyAdvice(@Value("${charter.guide-fetch.max-size:1MB}") DataSize maxGuideSize) {
        this.bodyLimit = bodyLimit(maxGuideSize.toBytes());
    }

    /**
     * The largest registration body accepted for a guide cap.
     *
     * @param maxGuideBytes the guide cap in bytes
     * @return six bytes per byte of guide, plus 1 MiB for the other fields
     */
    static long bodyLimit(long maxGuideBytes) {
        return ESCAPE_FACTOR * maxGuideBytes + OTHER_FIELDS;
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == ToolguideRegistration.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        if (headers.getContentLength() > bodyLimit) {
            throw tooLarge();
        }
        byte[] body;
        try (InputStream in = inputMessage.getBody()) {
            // One byte past the limit is enough to know the body is over it; the rest is never read.
            body = in.readNBytes(Math.toIntExact(Math.min(bodyLimit + 1, Integer.MAX_VALUE - 8)));
        }
        if (body.length > bodyLimit) {
            throw tooLarge();
        }
        explicitNull(body).ifPresent(field -> {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    GUIDE_FIELDS.contains(field)
                            ? "The " + field + " is null; a toolguide registration leaves out the guide field it does"
                                    + " not carry."
                            : RegistrationLists.nullListDetail(field));
        });
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    private CharterRefusal tooLarge() {
        return new CharterRefusal(
                RefusalCode.VALIDATION_FAILED,
                "The toolguide registration body is larger than " + bodyLimit + " bytes.");
    }

    /**
     * The first guide field, or else the first list, the body sends as an explicit {@code null}.
     *
     * @param body the request body
     * @return the field; empty when none is {@code null}, or the body is not a JSON object
     */
    static Optional<String> explicitNull(byte[] body) {
        return RegistrationLists.firstExplicitNull(body, NOT_NULL);
    }
}
