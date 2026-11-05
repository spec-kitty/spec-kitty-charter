package ai.speckitty.charter.deployable;

import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.BodyLimit;
import ai.speckitty.charter.packs.infra.api.JsonBodyGuard;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the body of every activation write before the message converter reads it (#125 build notes, #323): the
 * activation document of {@code createActivation} and {@code replaceActivation}, and the bodies of the sub-resource
 * writes ({@code SelectionRequest}, {@code ExclusionRequest}, {@code PresetApplication}), so the writes still reserved
 * need nothing here once built. Each fault is {@code 422 activation_invalid} with a violation:
 *
 * <ul>
 *   <li>a body over {@link BodyLimit#MAX_BYTES}: pointer {@code ""}, code {@code body_too_large}, judged by
 *       {@link JsonBodyGuard#readBounded} before the body is read whole;
 *   <li>a {@code null} anywhere in the body, a member or a list item at any depth (the generated DTOs read a
 *       {@code null} as absent, and absent has a meaning of its own): its pointer, code {@code null_value}. The guard's
 *       own {@code firstNull} looks at top-level members only, and a document's lists sit inside {@code selected} and
 *       {@code deactivated}, so the whole tree is walked here;
 *   <li>a member the body's schema does not declare, at any depth: its pointer, code {@code unknown_member}. The body
 *       is read a second time for it, into the target DTO with {@code FAIL_ON_UNKNOWN_PROPERTIES}: the cost is a
 *       second parse of at most 1 MiB, bounded by the cap above.
 * </ul>
 *
 * <p>A body that is not JSON, or that the strict read refuses for any other reason, is handed on unchanged: the
 * converter refuses it, and {@link ActivationProblems} answers that refusal. The body is handed on as it came.
 */
@ControllerAdvice
public class ActivationBodyAdvice extends RequestBodyAdviceAdapter {

    private static final Set<Type> GUARDED =
            Set.of(ActivationConfig.class, SelectionRequest.class, ExclusionRequest.class, PresetApplication.class);

    private static final JsonMapper TREE = JsonMapper.builder().build();
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** For Spring, which finds the advice by component scan; a test constructs it directly. */
    public ActivationBodyAdvice() {}

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return GUARDED.contains(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        byte[] body = JsonBodyGuard.readBounded(
                inputMessage,
                BodyLimit.MAX_BYTES,
                fault -> refused(List.of(new RefusalViolation(
                        "", "body_too_large", "The body is larger than " + BodyLimit.MAX_BYTES + " bytes."))));
        List<RefusalViolation> violations = violations(body, headers.getContentType(), (Class<?>) targetType);
        if (!violations.isEmpty()) {
            throw refused(violations);
        }
        return JsonBodyGuard.replay(headers, body);
    }

    /**
     * What is wrong with a body that is JSON: each {@code null} in it, in document order, then its first unknown
     * member.
     *
     * @param body the body
     * @param contentType the request's content type, whose charset the body is read in; null when it has none
     * @param target the DTO the body binds to
     * @return the violations; empty when there are none, or the body is not JSON
     */
    static List<RefusalViolation> violations(byte[] body, @Nullable MediaType contentType, Class<?> target) {
        Optional<JsonNode> tree = tree(body, contentType);
        if (tree.isEmpty()) {
            return List.of();
        }
        List<RefusalViolation> violations = new ArrayList<>();
        nulls(tree.orElseThrow(), "", violations);
        unknownMember(tree.orElseThrow(), target).ifPresent(violations::add);
        return violations;
    }

    private static Optional<JsonNode> tree(byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        try {
            return Optional.of(charset == null ? TREE.readTree(body) : TREE.readTree(new String(body, charset)));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    private static void nulls(JsonNode node, String pointer, List<RefusalViolation> violations) {
        if (node.isNull()) {
            violations.add(new RefusalViolation(
                    pointer,
                    "null_value",
                    "The value at '" + pointer + "' is null; null is never a value: an"
                            + " optional member is left out, and an empty list is []."));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                nulls(node.get(i), pointer + "/" + i, violations);
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> member : node.properties()) {
                nulls(member.getValue(), pointer + "/" + escaped(member.getKey()), violations);
            }
        }
    }

    private static Optional<RefusalViolation> unknownMember(JsonNode tree, Class<?> target) {
        try {
            STRICT.treeToValue(tree, target);
            return Optional.empty();
        } catch (UnrecognizedPropertyException unknown) {
            String pointer = ActivationProblems.pointerOf(unknown.getPath());
            return Optional.of(new RefusalViolation(
                    pointer, "unknown_member", "The body declares no member '" + unknown.getPropertyName() + "'."));
        } catch (JacksonException otherwise) {
            // Not this advice's to answer: the converter refuses the body, and ActivationProblems answers it.
            return Optional.empty();
        }
    }

    private static String escaped(String member) {
        return member.replace("~", "~0").replace("/", "~1");
    }

    private static CharterRefusal refused(List<RefusalViolation> violations) {
        return CharterRefusal.withViolations(
                RefusalCode.ACTIVATION_INVALID,
                "The activation request body breaks its schema in " + violations.size() + " place(s).",
                violations);
    }
}
