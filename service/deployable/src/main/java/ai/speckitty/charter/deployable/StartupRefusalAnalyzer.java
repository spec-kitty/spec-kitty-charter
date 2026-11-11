package ai.speckitty.charter.deployable;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Reports a {@link StartupRefusal} as its one line (#454). Spring Boot finds it in {@code META-INF/spring.factories}
 * and, once a failure is analysed, prints the analysis instead of the stack trace of the bean that refused.
 */
public final class StartupRefusalAnalyzer extends AbstractFailureAnalyzer<StartupRefusal> {

    private static final String ACTION = "Change the setting the line above names, then start the service again.";

    /** For Spring Boot, which instantiates the analyzer. */
    public StartupRefusalAnalyzer() {}

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, StartupRefusal refusal) {
        return new FailureAnalysis(String.valueOf(refusal.getMessage()), ACTION, refusal);
    }
}
