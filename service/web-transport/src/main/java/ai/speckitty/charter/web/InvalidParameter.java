package ai.speckitty.charter.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A request parameter the service refuses (#411): a {@code 400 Problem} without a code whose {@code detail} says where
 * the parameter is, its name as the request spells it, the value it carried and what it accepts. {@link ProblemHandler}
 * answers it; without that advice, Spring's own resolver still answers a {@code 400}. The wording is the service's
 * own: no validator message, framework text or exception message reaches the detail.
 *
 * <p>The value is quoted, its quotes and backslashes escaped, its control, format and line-breaking characters written
 * as four-digit unicode escapes, and cut after 64 code points, so the detail stays one readable line whatever the request held.
 * Only a caller the request guard admitted reads it, and it is that caller's own value.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidParameter extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MAX_CODE_POINTS = 64;

    private static final Set<Class<?>> INTEGERS =
            Set.of(Integer.class, int.class, Long.class, long.class, Short.class, short.class);

    /** Where a parameter is in the request. */
    public enum In {
        /** A query parameter. */
        QUERY("query parameter"),
        /** A path parameter. */
        PATH("path parameter"),
        /** A request header. */
        HEADER("header");

        private final String noun;

        In(String noun) {
            this.noun = noun;
        }
    }

    /** The one sentence the {@code 400} carries as its {@code detail}. */
    private final String detail;

    /**
     * A parameter that carried a value it does not accept.
     *
     * @param in where the parameter is
     * @param name its name as the request spells it
     * @param value the value it carried
     * @param reason what is wrong with the value, a clause that starts with {@code which}, such as {@code which is not
     *     a served kind}
     */
    public InvalidParameter(In in, String name, String value, String reason) {
        this("The " + in.noun + " " + name + " is \"" + quoted(value) + "\", " + reason + ".");
    }

    private InvalidParameter(String detail) {
        super(detail, null, false, false);
        this.detail = detail;
    }

    /**
     * A required parameter the request does not carry.
     *
     * @param in where the parameter is
     * @param name its name as the request spells it
     * @return the refusal
     */
    public static InvalidParameter missing(In in, String name) {
        return new InvalidParameter("The " + in.noun + " " + name + " is missing, and it is required.");
    }

    /**
     * Two request parameters that exclude each other, both sent.
     *
     * @param in where both are
     * @param first the first name as the request spells it
     * @param second the second name
     * @return the refusal
     */
    public static InvalidParameter excluding(In in, String first, String second) {
        return new InvalidParameter("The " + in.noun + "s " + first + " and " + second
                + " exclude each other, and the request sends both.");
    }

    /**
     * The reason of a value outside a closed list: {@code which is not one of a, b or c}.
     *
     * @param accepted the values the parameter accepts, in the order a client reads them
     * @return the clause
     */
    public static String oneOf(List<String> accepted) {
        if (accepted.size() == 1) {
            return "which is not " + accepted.getFirst();
        }
        return "which is not one of " + String.join(", ", accepted.subList(0, accepted.size() - 1)) + " or "
                + accepted.getLast();
    }

    /**
     * The {@code detail} of the {@code 400}.
     *
     * @return one sentence
     */
    public String detail() {
        return detail;
    }

    /**
     * The details of the method-validation violations that are on request parameters, in parameter order.
     *
     * @param violations the violations of one call
     * @return one sentence per violation on a query, path or header parameter; none for a violation elsewhere
     */
    public static List<String> details(Collection<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> of(violation)
                        .map(found -> new Ordered(found.index(), found.refusal().detail())))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(Ordered::index).thenComparing(Ordered::detail))
                .map(Ordered::detail)
                .toList();
    }

    private record Ordered(int index, String detail) {}

    /**
     * The refusal of one method-validation violation, when it is on a parameter bound from the request: its name and
     * place are read from the {@code @RequestParam}, {@code @PathVariable} or {@code @RequestHeader} of the method,
     * which the generated interface declares.
     *
     * @param violation the violation
     * @return the parameter's index and the refusal; empty when the violation is on no request parameter
     */
    static Optional<Located> of(ConstraintViolation<?> violation) {
        Path.MethodNode method = null;
        Path.ParameterNode parameter = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.METHOD) {
                method = node.as(Path.MethodNode.class);
            } else if (node.getKind() == ElementKind.PARAMETER) {
                parameter = node.as(Path.ParameterNode.class);
            } else {
                return Optional.empty();
            }
        }
        if (method == null || parameter == null) {
            return Optional.empty();
        }
        Method declared = ReflectionUtils.findMethod(
                violation.getRootBeanClass(),
                method.getName(),
                method.getParameterTypes().toArray(Class<?>[]::new));
        if (declared == null) {
            return Optional.empty();
        }
        int index = parameter.getParameterIndex();
        MethodParameter bound = new AnnotatedMethod(declared).getMethodParameters()[index];
        return named(bound)
                .map(name -> new Located(
                        index,
                        refusal(
                                name,
                                violation.getInvalidValue(),
                                violation.getConstraintDescriptor().getAnnotation())));
    }

    /**
     * The refusal of one violation of a parameter as Spring's own method validation reports it, with the parameter.
     *
     * @param parameter the method parameter
     * @param violation the violation of its value
     * @return the refusal; empty when the parameter is not bound from the request
     */
    public static Optional<InvalidParameter> of(MethodParameter parameter, ConstraintViolation<?> violation) {
        return named(parameter)
                .map(name -> refusal(
                        name,
                        violation.getInvalidValue(),
                        violation.getConstraintDescriptor().getAnnotation()));
    }

    /**
     * The refusal of a value of the wrong type, as a request parameter's conversion reports it.
     *
     * @param parameter the method parameter the value was bound to
     * @param value the value
     * @param required the type the parameter takes
     * @return the refusal; empty when the parameter is not bound from the request
     */
    public static Optional<InvalidParameter> ofType(
            MethodParameter parameter, @Nullable Object value, Class<?> required) {
        String reason =
                INTEGERS.contains(required) ? "which is not an integer" : "which this parameter does not accept";
        return named(parameter)
                .map(name -> value == null
                        ? missing(name.in(), name.name())
                        : new InvalidParameter(name.in(), name.name(), String.valueOf(value), reason));
    }

    /**
     * A violation's parameter index and its refusal.
     *
     * @param index the parameter's index in the method
     * @param refusal the refusal
     */
    record Located(int index, InvalidParameter refusal) {}

    private record Name(In in, String name) {}

    private static Optional<Name> named(MethodParameter parameter) {
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null) {
            return Optional.of(new Name(In.QUERY, wireName(query.name(), query.value(), parameter)));
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            return Optional.of(new Name(In.PATH, wireName(path.name(), path.value(), parameter)));
        }
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null) {
            return Optional.of(new Name(In.HEADER, wireName(header.name(), header.value(), parameter)));
        }
        return Optional.empty();
    }

    /** The name an annotation declares, by {@code name} or by its alias {@code value}, which an unmerged one keeps apart. */
    private static String wireName(String name, String value, MethodParameter parameter) {
        if (!name.isEmpty()) {
            return name;
        }
        return value.isEmpty() ? Objects.requireNonNullElse(parameter.getParameterName(), "?") : value;
    }

    private static InvalidParameter refusal(Name name, @Nullable Object value, Annotation constraint) {
        if (value == null || constraint instanceof NotNull) {
            return missing(name.in(), name.name());
        }
        return new InvalidParameter(name.in(), name.name(), String.valueOf(value), reason(constraint));
    }

    private static String reason(Annotation constraint) {
        return switch (constraint) {
            case Min min -> "which is less than " + min.value();
            case Max max -> "which is greater than " + max.value();
            case Pattern pattern -> "which does not match " + pattern.regexp();
            case Size size
            when size.max() == Integer.MAX_VALUE -> "which is shorter than " + size.min() + " character(s)";
            case Size size when size.min() == 0 -> "which is longer than " + size.max() + " character(s)";
            case Size size -> "which is not " + size.min() + " to " + size.max() + " characters long";
            default -> "which this parameter does not accept";
        };
    }

    /** A control, format (bidi overrides among them) or line-breaking character, which the detail escapes. */
    private static boolean unprintable(int c) {
        int type = Character.getType(c);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static String quoted(String value) {
        StringBuilder quoted = new StringBuilder();
        int[] codePoints = value.codePoints().toArray();
        for (int i = 0; i < Math.min(codePoints.length, MAX_CODE_POINTS); i++) {
            int c = codePoints[i];
            if (c == '"' || c == '\\') {
                quoted.append('\\').append((char) c);
            } else if (unprintable(c)) {
                quoted.append(String.format("\\u%04x", c));
            } else {
                quoted.appendCodePoint(c);
            }
        }
        if (codePoints.length > MAX_CODE_POINTS) {
            quoted.append('…');
        }
        return quoted.toString();
    }
}
