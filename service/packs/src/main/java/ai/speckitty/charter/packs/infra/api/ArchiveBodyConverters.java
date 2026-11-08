package ai.speckitty.charter.packs.infra.api;

import java.util.List;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes the service read an archive upload as a stream (#418, A3): the {@code Resource} converter Spring registers by
 * default is replaced, in its place in the list, by {@link StreamingResourceConverter}. No other operation takes a
 * {@code Resource} body, and what the converter writes is unchanged.
 */
@Component
public class ArchiveBodyConverters implements WebMvcConfigurer {

    /** For Spring, which registers the configurer; a test constructs it directly. */
    public ArchiveBodyConverters() {
        // Stateless.
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(ArchiveBodyConverters::streamResources);
    }

    private static void streamResources(List<HttpMessageConverter<?>> converters) {
        boolean replaced = false;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i).getClass() == ResourceHttpMessageConverter.class) {
                converters.set(i, new StreamingResourceConverter());
                replaced = true;
            }
        }
        if (!replaced) {
            converters.add(0, new StreamingResourceConverter());
        }
    }
}
