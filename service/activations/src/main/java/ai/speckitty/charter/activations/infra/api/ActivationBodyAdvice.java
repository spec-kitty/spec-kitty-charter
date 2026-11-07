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
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Guards the body of every activation write before the message converter reads it (#125 build notes, #323): the
 * activation document of {@code createActivation} and {@code replaceActivation}, and the bodies of the sub-resource
 * writes ({@code SelectionRequest}, {@code ExclusionRequest}, {@code PresetApplication}), so every activation write
 * reads its body through the same guard. Each fault is {@code 422 activation_invalid} with a violation:
 *
 * <ul>
 *   <li>a member repeated in one object (the last would win, silently): the pointer of the member, code
 *       {@code duplicate_member}, raised by the tree read ({@link StreamReadFeature#STRICT_DUPLICATE_DETECTION});
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
 *   <li>a scalar of another type than the one declared, which the strict re-read does not coerce (a string or a
 *       decimal for the integer {@code schemaVersion}, a number or a boolean for a string): its pointer, code
 *       {@code unreadable_body};
 *   <li>a member the document had before the one kind model (IC-20, Q4): {@code activatedKinds} or
 *       {@code missionTypes} at the top of {@code createActivation}'s or {@code replaceActivation}'s document, whatever
 *       its value, code {@code removed_member}, whose detail names its replacement. A body with one is
 *       {@code 422 validation_failed} instead of {@code activation_invalid}, its other faults listed beside it; there is
 *       no deprecation window.
 * </ul>
 *
 * <p>A body that is not JSON, or that the strict read refuses for any other reason, is handed on unchanged: the
 * converter refuses it, and {@link ActivationProblems} answers that refusal. The body is handed on as it came.
 *
 * <p>The document of {@code createActivation} and {@code replaceActivation} is refused here, as above. The body of a
 * sub-resource write is not: its preconditions come first ({@code 428}, {@code 404 activation_not_found},
 * {@code 412}; FR-010), so every fault of it is recorded ({@link DeferredBody}) and refused by the use case at its
 * {@code 422} step. Besides the faults above, that covers a body that is missing, not JSON or of the wrong shape
 * ({@code unreadable_body}, at the value Jackson names), and one that breaks a constraint of its schema
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
    /** The members the one kind model removed from the document (Q4), each with the member that replaces it. */
    private static final Map<String, String> REMOVED_MEMBERS = Map.of(
            "activatedKinds",
            "selected.<kind>: the kind gate is gone, and a select brings its artifact into force",
            "missionTypes",
            "selected.mission_type: a mission type is selected like any kind");

    private static final String REMOVED = "removed_member";
    private static final byte[] NO_MEMBERS = "{}".getBytes(StandardCharsets.UTF_8);

    /** Reads the body as a tree, and refuses a member repeated in one object instead of keeping the last. */
    private static final JsonMapper TREE = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /**
     * Binds the tree as the activation DTOs are bound, but strictly: an unknown member is refused, and a scalar is
     * never coerced to the declared type ({@code "1"} or {@code 1.0} for an integer, {@code 1} or {@code true} for a
     * string).
     */
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .withCoercionConfig(
                    LogicalType.Textual,
                    config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
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
        DeferredBody.record(List.of(ActivationProblems.unreadableAt("")));
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
     * Every fault of a sub-resource body, judged in turn: each fault {@link #violations} finds (a member repeated, each
     * {@code null}, each unknown member, a scalar of another type); else a body that is not JSON or that the strict
     * read cannot bind, which a document's write leaves to the converter; else each broken constraint.
     *
     * @param body the body
     * @param contentType the request's content type; null when it has none
     * @param target the DTO the body binds to
     * @param validator the bean validator; null to leave the constraints to method validation
     * @return the violations; empty when the body is well formed
     */
    static List<RefusalViolation> deferredViolations(
            byte[] body, @Nullable MediaType contentType, Class<?> target, @Nullable Validator validator) {
        List<RefusalViolation> violations = violations(body, contentType, target);
        if (!violations.isEmpty()) {
            return violations;
        }
        Object bound;
        try {
            bound = STRICT.treeToValue(read(body, contentType), target);
        } catch (JacksonException unbindable) {
            return List.of(ActivationProblems.unreadableAt(ActivationProblems.pointerOf(unbindable.getPath())));
        }
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

    private static RefusalViolation tooLarge() {
        return new RefusalViolation("", "body_too_large", "The body is larger than " + BodyLimit.MAX_BYTES + " bytes.");
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
        JsonNode tree;
        try {
            tree = read(body, contentType);
        } catch (StreamReadException unreadable) {
            return unreadable.processor() != null && isRepeatedMember(unreadable)
                    ? List.of(repeatedMember(unreadable))
                    : List.of();
        } catch (JacksonException notJson) {
            return List.of();
        }
        List<RefusalViolation> violations = new ArrayList<>();
        nulls(tree, "", violations);
        List<RefusalViolation> unknown = new ArrayList<>();
        unknownMembers(tree, target, "", unknown);
        if (unknown.isEmpty()) {
            strictRead(tree, target).ifPresent(unknown::add);
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
                violations.add(missingAt(dto, at, member.getKey()));
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

    /** A member the DTO does not declare: removed by the one kind model, at the document's top, or unknown. */
    private static RefusalViolation missingAt(Class<?> dto, String pointer, String member) {
        String replacement =
                dto == ActivationConfig.class && pointer.equals("/" + member) ? REMOVED_MEMBERS.get(member) : null;
        return replacement == null
                ? unknownAt(pointer, member)
                : new RefusalViolation(
                        pointer,
                        REMOVED,
                        "The member '" + member + "' is not part of the activation document any more; use "
                                + replacement + ".");
    }

    private static RefusalViolation unknownAt(String pointer, String member) {
        return new RefusalViolation(pointer, "unknown_member", "The body declares no member '" + member + "'.");
    }

    private static JsonNode read(byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        return charset == null ? TREE.readTree(body) : TREE.readTree(new String(body, charset));
    }

    private static boolean isRepeatedMember(StreamReadException unreadable) {
        return String.valueOf(unreadable.getOriginalMessage()).startsWith("Duplicate Object property");
    }

    /** The member repeated in an object, at the pointer the parser had reached when it met the second one. */
    private static RefusalViolation repeatedMember(StreamReadException unreadable) {
        String pointer =
                unreadable.processor().streamReadContext().pathAsPointer().toString();
        return new RefusalViolation(
                pointer,
                "duplicate_member",
                "The body repeats the member at '" + pointer + "'; a member is stated once.");
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

    /**
     * The strict bind of the tree: its first unknown member, or its first scalar of another type than the one declared
     * (no coercion), at the pointer Jackson names. Anything else is not this advice's to answer: the converter refuses
     * the body, and {@link ActivationProblems} answers that refusal.
     */
    private static Optional<RefusalViolation> strictRead(JsonNode tree, Class<?> target) {
        try {
            STRICT.treeToValue(tree, target);
            return Optional.empty();
        } catch (UnrecognizedPropertyException unknown) {
            return Optional.of(unknownAt(ActivationProblems.pointerOf(unknown.getPath()), unknown.getPropertyName()));
        } catch (MismatchedInputException mismatched) {
            return Optional.of(ActivationProblems.unreadableAt(ActivationProblems.pointerOf(mismatched.getPath())));
        } catch (JacksonException otherwise) {
            return Optional.empty();
        }
    }

    private static String escaped(String member) {
        return member.replace("~", "~0").replace("/", "~1");
    }

    /**
     * The refusal of a body's faults: {@code validation_failed} when the body sends a member the one kind model
     * removed, else {@code activation_invalid}.
     */
    private static CharterRefusal refused(List<RefusalViolation> violations) {
        boolean removed = violations.stream().anyMatch(violation -> REMOVED.equals(violation.code()));
        return CharterRefusal.withViolations(
                removed ? RefusalCode.VALIDATION_FAILED : RefusalCode.ACTIVATION_INVALID,
                "The activation request body breaks its schema in " + violations.size() + " place(s).",
                violations);
    }
}
