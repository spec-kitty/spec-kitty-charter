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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the body of every activation write before the message converter reads it (#125 build notes, #323): the
 * activation document of {@code createActivation} and {@code replaceActivation}, and the bodies of the sub-resource
 * writes ({@code SelectionRequest}, {@code ExclusionRequest}, {@code PresetApplication}), so every activation write
 * reads its body through the same guard. Each fault is {@code 422 activation_invalid} with a violation:
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
 *       up, so a member the walk could not judge is still refused, as its first unknown member;
 *   <li>a member declared twice in one object, at any depth: its pointer, code {@code unreadable_body}. The strict
 *       reader detects duplicates ({@code STRICT_DUPLICATE_DETECTION}), where the converter would keep the last;
 *   <li>a value the strict read cannot bind, a value of the wrong type above all: its pointer, as Jackson names it,
 *       code {@code unreadable_body}. The strict reader coerces no scalar into another (no {@code 1} or
 *       {@code "true"} for a boolean, no {@code "1"} or {@code 1.0} for an integer, no number or boolean for a
 *       string), where the converter would, so the body is judged here and never handed on to be coerced.
 * </ul>
 *
 * <p>A body that is not JSON is handed on unchanged: the converter refuses it, and {@link ActivationProblems} answers
 * that refusal. A body without a fault is handed on as it came.
 *
 * <p>The document of {@code createActivation} and {@code replaceActivation} is refused here, as above. The body of a
 * sub-resource write is not: its preconditions come first ({@code 428}, {@code 404 activation_not_found},
 * {@code 412}; FR-010), so every fault of it is recorded ({@link DeferredBody}) and refused by the use case at its
 * {@code 422} step. Besides the faults above, that covers a body that is missing, not JSON or of the wrong shape
 * ({@code unreadable_body}, at the value Jackson names, or at the member declared twice), and one that breaks a constraint of its schema
 * ({@code invalid_value}, as bean validation finds it when a validator is at hand). The converter then reads
 * {@code {}} in its place, so nothing refuses the request before its preconditions are judged. The {@code cascade}
 * tokens bind as strings ({@code SingleModelPerSchemaNormalizer}), so a token outside the cascade's kinds is the use
 * case's {@code unknown_kind}, not a fault of the body.
 */
@ControllerAdvice
public class ActivationBodyAdvice extends RequestBodyAdviceAdapter {

    private static final Set<Type> GUARDED =
            Set.of(ActivationConfig.class, SelectionRequest.class, ExclusionRequest.class, PresetApplication.class);
    private static final Set<Type> DEFERRED =
            Set.of(SelectionRequest.class, ExclusionRequest.class, PresetApplication.class);
    private static final byte[] NO_MEMBERS = "{}".getBytes(StandardCharsets.UTF_8);

    private static final JsonMapper TREE = JsonMapper.builder().build();
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .withCoercionConfigDefaults(scalars -> scalars.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail))
            .build();

    private final @Nullable Validator validator;

    /** An advice without a validator: a sub-resource body's constraints are then left to method validation. */
    public ActivationBodyAdvice() {
        this((Validator) null);
    }

    /**
     * For Spring, which finds the advice by component scan, with the application's validator when it has one.
     *
     * @param validator the bean validator
     */
    @Autowired
    public ActivationBodyAdvice(ObjectProvider<Validator> validator) {
        this(validator.getIfAvailable());
    }

    /**
     * An advice over a validator.
     *
     * @param validator the bean validator a sub-resource body's constraints are judged by; null for none
     */
    public ActivationBodyAdvice(@Nullable Validator validator) {
        this.validator = validator;
    }

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
        if (DEFERRED.contains(targetType)) {
            return deferred(inputMessage, (Class<?>) targetType);
        }
        HttpHeaders headers = inputMessage.getHeaders();
        byte[] body =
                JsonBodyGuard.readBounded(inputMessage, BodyLimit.MAX_BYTES, fault -> refused(List.of(tooLarge())));
        List<RefusalViolation> violations = violations(body, headers.getContentType(), (Class<?>) targetType);
        if (!violations.isEmpty()) {
            throw refused(violations);
        }
        return JsonBodyGuard.replay(headers, body);
    }

    /** A sub-resource body without one: recorded as unreadable, and read as {@code {}}. */
    @Override
    public @Nullable Object handleEmptyBody(
            @Nullable Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (body != null || !DEFERRED.contains(targetType)) {
            return body;
        }
        DeferredBody.record(List.of(unreadable("")));
        return STRICT.readValue(NO_MEMBERS, (Class<?>) targetType);
    }

    /** A sub-resource body: its faults are recorded for the write's 422 step, and the converter reads {}. */
    private HttpInputMessage deferred(HttpInputMessage inputMessage, Class<?> target) throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        byte[] body;
        try {
            body = JsonBodyGuard.readBounded(inputMessage, BodyLimit.MAX_BYTES, fault -> refused(List.of(tooLarge())));
        } catch (CharterRefusal tooLarge) {
            return deferring(headers, tooLarge.violations());
        }
        List<RefusalViolation> violations = deferredViolations(body, headers.getContentType(), target, validator);
        return violations.isEmpty() ? JsonBodyGuard.replay(headers, body) : deferring(headers, violations);
    }

    private static HttpInputMessage deferring(HttpHeaders headers, List<RefusalViolation> violations) {
        DeferredBody.record(violations);
        HttpHeaders replayed = new HttpHeaders();
        replayed.putAll(headers);
        replayed.setContentLength(NO_MEMBERS.length);
        return JsonBodyGuard.replay(replayed, NO_MEMBERS);
    }

    /**
     * Every fault of a sub-resource body, judged in turn: a body that is not JSON; else each {@code null}, a member
     * declared twice, each unknown member and a value the strict read cannot bind ({@link #violations}); else each
     * broken constraint.
     *
     * @param body the body
     * @param contentType the request's content type; null when it has none
     * @param target the DTO the body binds to
     * @param validator the bean validator; null to leave the constraints to method validation
     * @return the violations; empty when the body is well formed
     */
    static List<RefusalViolation> deferredViolations(
            byte[] body, @Nullable MediaType contentType, Class<?> target, @Nullable Validator validator) {
        Optional<JsonNode> tree = tree(body, contentType);
        if (tree.isEmpty()) {
            return List.of(unreadable(""));
        }
        List<RefusalViolation> violations = violations(body, contentType, target);
        if (!violations.isEmpty()) {
            return violations;
        }
        Object bound = STRICT.treeToValue(tree.orElseThrow(), target);
        return validator == null || bound == null ? List.of() : constraintViolations(validator.validate(bound));
    }

    private static List<RefusalViolation> constraintViolations(Set<ConstraintViolation<Object>> broken) {
        return broken.stream()
                .map(violation -> {
                    String pointer = ActivationProblems.pointerOfProperty(
                            violation.getPropertyPath().toString());
                    return new RefusalViolation(
                            pointer, "invalid_value", "The value at '" + pointer + "' " + violation.getMessage() + ".");
                })
                .sorted(Comparator.comparing(RefusalViolation::pointer))
                .toList();
    }

    private static RefusalViolation declaredTwice(String pointer) {
        return new RefusalViolation(
                pointer, "unreadable_body", "The body declares the member at '" + pointer + "' more than once.");
    }

    private static RefusalViolation unreadable(String pointer) {
        return new RefusalViolation(
                pointer, "unreadable_body", "The body is not JSON of the request's schema at '" + pointer + "'.");
    }

    private static RefusalViolation tooLarge() {
        return new RefusalViolation("", "body_too_large", "The body is larger than " + BodyLimit.MAX_BYTES + " bytes.");
    }

    /**
     * What is wrong with a body that is JSON: each {@code null} in it, in document order, then the first member
     * declared twice, then each unknown member, in document order; when there is no unknown member, the first value
     * the strict read cannot bind.
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
        duplicateMember(body, contentType).ifPresent(violations::add);
        List<RefusalViolation> unknown = new ArrayList<>();
        unknownMembers(tree.orElseThrow(), target, "", unknown);
        if (unknown.isEmpty()) {
            unboundValue(tree.orElseThrow(), target).ifPresent(unknown::add);
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
        try {
            return Optional.of(read(TREE, body, contentType));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    /**
     * The first member declared twice in one object of a body that is JSON: the lenient tree keeps the last, and only
     * the strict reader's duplicate detection tells them apart.
     */
    private static Optional<RefusalViolation> duplicateMember(byte[] body, @Nullable MediaType contentType) {
        try {
            read(STRICT, body, contentType);
            return Optional.empty();
        } catch (JacksonException duplicate) {
            String pointer = duplicate.processor() instanceof JsonParser parser
                    ? parser.streamReadContext().pathAsPointer().toString()
                    : "";
            return Optional.of(declaredTwice(pointer));
        }
    }

    private static JsonNode read(JsonMapper reader, byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        return charset == null ? reader.readTree(body) : reader.readTree(new String(body, charset));
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

    /** The first fault of the strict read: an unknown member the walk could not judge, or a value it cannot bind. */
    private static Optional<RefusalViolation> unboundValue(JsonNode tree, Class<?> target) {
        try {
            STRICT.treeToValue(tree, target);
            return Optional.empty();
        } catch (UnrecognizedPropertyException unknown) {
            return Optional.of(unknownAt(ActivationProblems.pointerOf(unknown.getPath()), unknown.getPropertyName()));
        } catch (JacksonException unbindable) {
            return Optional.of(unreadable(ActivationProblems.pointerOf(unbindable.getPath())));
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
