package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
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
 *       ({@code commands}, {@code appliesToLanguages}, {@code references}), or a {@code null} item in one: an absent
 *       list is empty, and {@code null} is never a value (#292, #309, as {@link RegistrationBodyAdvice} does for the
 *       other registrations).
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

    /** The registration's list members, looked at for a {@code null} after the guide fields. */
    private static final List<String> LISTS = RegistrationLists.of(ToolguideRegistration.class);

    /** JSON bytes allowed per byte of guide: a JSON escape of one character is six. */
    private static final int ESCAPE_FACTOR = 6;

    /** The room left for the fields beside the guide: every registration's own body limit. */
    private static final long OTHER_FIELDS = RegistrationLists.BODY_LIMIT;

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
        if (bodyLimit >= Integer.MAX_VALUE - 8) {
            // A body is read into one array; a limit past the largest one could never be enforced.
            throw new IllegalArgumentException("charter.guide-fetch.max-size " + maxGuideSize
                    + " makes a toolguide registration body limit no array can hold.");
        }
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
        byte[] body = RegistrationLists.readBounded(inputMessage, bodyLimit, "toolguide");
        explicitNull(body, headers.getContentType()).ifPresent(nul -> {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    GUIDE_FIELDS.contains(nul.member())
                            ? "The " + nul.member() + " is null; a toolguide registration leaves out the guide field"
                                    + " it does not carry."
                            : nul.detail());
        });
        return RegistrationLists.replay(headers, body);
    }

    /**
     * The first guide field the body sends as an explicit {@code null}, or else the first list it sends as one or
     * with a {@code null} item.
     *
     * @param body the request body
     * @param contentType the request's content type, whose charset the body is read in
     * @return the field and what is {@code null} in it; empty when nothing is, or the body is not a readable JSON
     *     object
     */
    static Optional<RegistrationLists.NullValue> explicitNull(byte[] body, @Nullable MediaType contentType) {
        return RegistrationLists.firstNull(body, contentType, GUIDE_FIELDS, LISTS);
    }
}
