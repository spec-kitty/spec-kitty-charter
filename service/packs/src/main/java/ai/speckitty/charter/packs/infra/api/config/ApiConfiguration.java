package ai.speckitty.charter.packs.infra.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The inbound REST edge's configuration: it scans the {@code infra.api} package, which makes each controller, the
 * connector's problem error handling and the worktree guard a bean; the shared problem and refusal handlers are the
 * web transport's ({@code ai.speckitty.charter.web.config}). The controllers depend on the application services, and
 * {@code InfoController} also on the {@code ContractDocument} the deployable's {@code ContractDocumentWiring} loads, so
 * a slice that boots this package supplies one.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.packs.infra.api")
public class ApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ApiConfiguration() {}
}
