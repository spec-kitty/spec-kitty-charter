package ai.speckitty.charter.packs.infra.api;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;

/** The converters of a Spring MVC application as the service adjusts them, for the tests that stand a controller up. */
final class HttpConverters {

    private HttpConverters() {}

    static List<HttpMessageConverter<?>> serverConverters() {
        HttpMessageConverters.ServerBuilder builder =
                HttpMessageConverters.forServer().registerDefaults();
        new ArchiveBodyConverters().configureMessageConverters(builder);
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        builder.build().forEach(converters::add);
        return converters;
    }
}
