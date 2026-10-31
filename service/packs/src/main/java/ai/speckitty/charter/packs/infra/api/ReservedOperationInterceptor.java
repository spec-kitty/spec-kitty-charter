package ai.speckitty.charter.packs.infra.api;

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
 * Answers every operation of a {@link ReservedOperation} controller with {@code 501 Not Implemented} (ADR-0020 D8).
 * It runs in {@code preHandle}, after the request is routed and before its arguments are resolved, so a reserved
 * operation answers {@code 501} whatever its request holds: a body the built operation would refuse with {@code 422}
 * is answered {@code 501} too. A body that is not JSON is refused {@code 415} before routing, as the contract declares.
 * The {@link ResponseStatusException} is rendered by {@link ProblemHandler} as the closed {@code Problem}, without a
 * code, and the Charter headers are set by {@link CharterHeaders} as on every answer.
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
        if (handler instanceof HandlerMethod method
                && method.getBeanType().isAnnotationPresent(ReservedOperation.class)) {
            throw notBuilt();
        }
        return true;
    }

    /**
     * The answer of a reserved operation.
     *
     * @return a {@code 501} that {@link ProblemHandler} renders as a {@code Problem} without a code
     */
    static ResponseStatusException notBuilt() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
