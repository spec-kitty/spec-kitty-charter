package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.BodyLimit;
import ai.speckitty.charter.web.JsonBodyGuard;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
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
 *   <li>every member the body's schema does not declare, at any depth: its pointer, code {@code unknown_member}, so a
 *       client fixes the body in one round trip. The tree is walked against the target DTO's declared members
 *       ({@code @JsonProperty} on its getters), level by level, into nested DTOs and lists of them; the walk is linear
 *       in the body, bounded by the cap above. A strict re-read ({@code FAIL_ON_UNKNOWN_PROPERTIES}) backs the walk
 *       up, so a member the walk could not judge is still refused, as its first unknown member.
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
     * What is wrong with a body that is JSON: each {@code null} in it, in document order, then each unknown member, in
     * document order.
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
        List<RefusalViolation> unknown = new ArrayList<>();
        unknownMembers(tree.orElseThrow(), target, "", unknown);
        if (unknown.isEmpty()) {
            unknownMember(tree.orElseThrow(), target).ifPresent(unknown::add);
        }
        violations.addAll(unknown);
        return violations;
    }

    /** Each member of an object node that its DTO does not declare, descending into the members it does declare. */
    private static void unknownMembers(JsonNode node, Type type, String pointer, List<RefusalViolation> violations) {
        if (!(type instanceof Class<?> dto) || !node.isObject() || !isDto(dto)) {
            return;
        }
        Map<String, Type> declared = declaredMembers(dto);
        for (Map.Entry<String, JsonNode> member : node.properties()) {
            String at = pointer + "/" + escaped(member.getKey());
            Type memberType = declared.get(member.getKey());
            if (memberType == null) {
                violations.add(unknownAt(at, member.getKey()));
            } else {
                descend(member.getValue(), memberType, at, violations);
            }
        }
    }

    /** Into a declared member: a nested DTO, or each item of a list of them; anything else declares no members. */
    private static void descend(JsonNode value, Type type, String pointer, List<RefusalViolation> violations) {
        if (type instanceof ParameterizedType list && list.getRawType() == List.class && value.isArray()) {
            Type item = list.getActualTypeArguments()[0];
            for (int i = 0; i < value.size(); i++) {
                unknownMembers(value.get(i), item, pointer + "/" + i, violations);
            }
            return;
        }
        unknownMembers(value, type, pointer, violations);
    }

    /** A generated model class of the contract, whose members its {@code @JsonProperty} getters declare. */
    private static boolean isDto(Class<?> type) {
        return !type.isEnum() && type.getPackageName().equals(ActivationConfig.class.getPackageName());
    }

    private static Map<String, Type> declaredMembers(Class<?> dto) {
        Map<String, Type> members = new HashMap<>();
        for (Method method : dto.getMethods()) {
            JsonProperty property = method.getAnnotation(JsonProperty.class);
            if (property != null && method.getParameterCount() == 0) {
                members.put(property.value(), method.getGenericReturnType());
            }
        }
        return members;
    }

    private static RefusalViolation unknownAt(String pointer, String member) {
        return new RefusalViolation(pointer, "unknown_member", "The body declares no member '" + member + "'.");
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
            return Optional.of(unknownAt(ActivationProblems.pointerOf(unknown.getPath()), unknown.getPropertyName()));
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
