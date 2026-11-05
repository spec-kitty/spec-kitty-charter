package ai.speckitty.charter.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The shared web transport's configuration: it scans the {@code web} package, which makes the refusal and problem
 * handlers and the problem JSON configuration beans for every slice's inbound adapter.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.web")
public class WebTransportConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public WebTransportConfiguration() {}
}
