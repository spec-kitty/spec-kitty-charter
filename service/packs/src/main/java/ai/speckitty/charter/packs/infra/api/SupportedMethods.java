package ai.speckitty.charter.packs.infra.api;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The HTTP methods the service supports: those some handler declares, with the {@code HEAD} and {@code OPTIONS} Spring MVC adds. */
@Component
class SupportedMethods {

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    SupportedMethods(ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.mappings = mappings;
    }

    /** The methods as an {@code Allow} header value, for example {@code GET,HEAD,OPTIONS}. */
    String header() {
        Set<HttpMethod> methods = new TreeSet<>(Comparator.comparing(HttpMethod::name));
        mappings.orderedStream()
                .flatMap(mapping -> mapping.getHandlerMethods().keySet().stream())
                .flatMap(info -> info.getMethodsCondition().getMethods().stream())
                .forEach(method -> methods.add(HttpMethod.valueOf(method.name())));
        if (methods.contains(HttpMethod.GET)) {
            methods.add(HttpMethod.HEAD);
        }
        if (!methods.isEmpty()) {
            methods.add(HttpMethod.OPTIONS);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(methods);
        return String.valueOf(headers.getFirst(HttpHeaders.ALLOW));
    }
}
