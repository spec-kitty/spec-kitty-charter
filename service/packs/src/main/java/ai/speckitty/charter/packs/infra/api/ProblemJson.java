package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.Problem;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * How the problem bodies serialize: an absent optional member ({@code type}, {@code detail}, {@code instance},
 * {@code code}) is omitted, never written as {@code null}. The contract's {@code Problem} is closed and declares
 * those members as strings, so a {@code null} would break it. The generated transport DTOs stay as generated;
 * the rule reaches them through a Jackson mix-in, and only them: a success body keeps its {@code null} members,
 * which the contract requires. The rule is the contract's: a member the schema neither requires nor allows to be
 * {@code null} is omitted. The models it covers are exactly those with such a member and no required nullable
 * one, which {@code NullOmissionTest} derives from the schemas and checks against every generated model. Beside the
 * problems, those are the hypermedia {@code Link} (its {@code templated}, {@code method} and {@code title}) and the
 * {@code Artifact} envelope (the four kind members its kind does not name).
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ProblemRuntimeHints.class)
public class ProblemJson {

    /** For Spring, which finds the configuration by component scan. */
    public ProblemJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of the problem, link and artifact DTOs
     */
    @Bean
    public JacksonModule problemJsonModule() {
        return new SimpleModule("charter-problem-json")
                .setMixInAnnotation(CharterRefusal.class, OmitAbsentMembers.class)
                .setMixInAnnotation(Problem.class, OmitAbsentMembers.class)
                .setMixInAnnotation(Link.class, OmitAbsentMembers.class)
                .setMixInAnnotation(Artifact.class, OmitAbsentMembers.class);
    }

    /**
     * The mix-in, for the native executable's hints ({@link ProblemRuntimeHints}).
     *
     * @return the mix-in class
     */
    static Class<?> omitAbsentMembers() {
        return OmitAbsentMembers.class;
    }

    /** The mix-in: every {@code null} member of the target is left out of the JSON. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}
}
