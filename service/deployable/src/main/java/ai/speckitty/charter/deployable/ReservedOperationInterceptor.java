package ai.speckitty.charter.deployable;

import ai.speckitty.charter.kernel.transport.ReservedOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Answers every {@link ReservedOperation} with {@code 501 Not Implemented} (ADR-0020 D8), decided per handler method:
 * a method marked itself is reserved, and so is every handler method of a marked type, so one controller can serve some
 * operations and reserve others. It runs in {@code preHandle}, after the request is routed and before its arguments are
 * resolved, so a reserved operation answers {@code 501} whatever its request holds: a body the built operation would
 * refuse with {@code 422} is answered {@code 501} too. A body that is not JSON is refused {@code 415} before routing,
 * as the contract declares. The {@link ResponseStatusException} is rendered by the inbound adapter's
 * {@code ProblemHandler} as the closed {@code Problem}, without a code, and the Charter headers are set by
 * {@code CharterHeaders} as on every answer.
 *
 * <p>It lives in the composition root, which every slice's controllers are served through; a controller never names it.
 * A marker on an interface default method is not seen, so a reserved operation is overridden in its controller.
 */
@Component
public class ReservedOperationInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    /** For Spring, which finds the interceptor by component scan. */
    public ReservedOperationInterceptor() {}

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && isReserved(method)) {
            throw notBuilt();
        }
        return true;
    }

    /** The method's own mark first, then its controller's. */
    private static boolean isReserved(HandlerMethod method) {
        return method.hasMethodAnnotation(ReservedOperation.class)
                || method.getBeanType().isAnnotationPresent(ReservedOperation.class);
    }

    /**
     * The answer of a reserved operation.
     *
     * @return a {@code 501} that {@code ProblemHandler} renders as a {@code Problem} without a code
     */
    static ResponseStatusException notBuilt() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
