package ai.speckitty.charter.packs.infra.api;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Guards the JSON body of {@code POST /packs} (skeleton: guards nothing yet, built next). */
@ControllerAdvice
public class PackSourceBodyAdvice extends RequestBodyAdviceAdapter {

    /** An advice over the source registration; a test constructs it directly. */
    public PackSourceBodyAdvice() {
        // Stateless.
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return false;
    }
}
