package ai.speckitty.charter.packs.infra.api;

import java.io.IOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.ResourceHttpMessageConverter;

/**
 * Reads a request body as a stream, never as a byte array (#418, A3). Spring's own {@link ResourceHttpMessageConverter}
 * hands a {@code Resource} parameter the whole body as a {@code ByteArrayResource} even with read streaming on, which
 * holds the 32 MiB archive of an upload in the heap, and twice while it is copied. The generated {@code RegistrationApi}
 * declares the archive as a {@code Resource}, so this converter binds that parameter to the request's input stream: an
 * {@code InputStreamResource} that is read once, by the controller, into the job's spool file.
 */
final class StreamingResourceConverter extends ResourceHttpMessageConverter {

    /** A converter with read streaming on. */
    StreamingResourceConverter() {
        super(true);
    }

    @Override
    protected Resource readInternal(Class<? extends Resource> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        return super.readInternal(Resource.class == clazz ? InputStreamResource.class : clazz, inputMessage);
    }
}
