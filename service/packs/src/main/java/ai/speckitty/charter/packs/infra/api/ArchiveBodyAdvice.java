package ai.speckitty.charter.packs.infra.api;

import java.io.IOException;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Guards the archive body of {@code PUT /packs/{packId}} before the message converter reads it (NFR-004, T076).
 */
@ControllerAdvice
public class ArchiveBodyAdvice extends RequestBodyAdviceAdapter {

    /** The most body bytes an archive upload may hold, 32 MiB. */
    public static final long MAX_BODY_BYTES = 32L * 1024 * 1024;

    /** An advice over the archive upload; a test constructs it directly. */
    public ArchiveBodyAdvice() {
        // Stateless.
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        return inputMessage;
    }
}
