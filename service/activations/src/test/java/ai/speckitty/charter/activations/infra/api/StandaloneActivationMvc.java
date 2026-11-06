package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.ActivationExclusionService;
import ai.speckitty.charter.activations.application.ActivationIdMint;
import ai.speckitty.charter.activations.application.ActivationPresetService;
import ai.speckitty.charter.activations.application.ActivationSelectionService;
import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.application.AvailablePacks;
import ai.speckitty.charter.activations.application.FakeAvailablePacks;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.web.ProblemHandler;
import ai.speckitty.charter.web.ProblemJson;
import ai.speckitty.charter.web.RefusalHandler;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The activation controller as the service serves it, without the composition root: proxied for the generated
 * interface's method validation as Spring Boot proxies it, behind the slice's own advices ({@link ActivationBodyAdvice},
 * {@link ActivationProblems}) and the shared web transport's ({@link RefusalHandler}, {@link ProblemHandler}), and
 * writing JSON with the activation and problem mix-ins. The packs hold {@link #IDS} of every kind.
 */
final class StandaloneActivationMvc {

    /** The ids the fixture packs hold of each kind. */
    static final String[] IDS = {"a-1", "b-2", "c-3", "d-4"};

    private StandaloneActivationMvc() {}

    /**
     * The MockMvc over a controller backed by the given register.
     *
     * @param repository the register's store
     * @return the MockMvc
     */
    static MockMvc over(InMemoryActivationRepository repository) {
        ClosureSnapshots closure = ClosureSnapshots.snapshot().pack("fixture", "1.0.0");
        for (ArtifactKind kind : ArtifactKind.values()) {
            closure.held(kind, IDS);
        }
        ActivationController controller = controller(repository, new FakeAvailablePacks(closure.build()));
        RefusalHandler refusals = new RefusalHandler();
        JsonMapper json = JsonMapper.builder()
                .addModule(new ActivationJson().activationJsonModule())
                .addModule(new ProblemJson().problemJsonModule())
                .build();
        return MockMvcBuilders.standaloneSetup(validated(controller))
                .setControllerAdvice(
                        new ActivationBodyAdvice(), new ActivationProblems(refusals), refusals, new ProblemHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .build();
    }

    /**
     * A controller over a register and packs, wired as Spring wires it.
     *
     * @param repository the register's store
     * @param packs the packs
     * @return the controller
     */
    static ActivationController controller(InMemoryActivationRepository repository, AvailablePacks packs) {
        ActivationWrites writes = new ActivationWrites(repository, packs);
        ActivationMapper mapper = new ActivationMapper("/api/v1");
        return new ActivationController(
                new ActivationService(repository, packs, new ActivationIdMint()),
                new ActivationSelectionService(writes),
                new ActivationExclusionService(writes),
                new ActivationPresetService(writes),
                mapper,
                new ActivationChangeMapper(mapper));
    }

    /** The controller behind the method validation Spring Boot applies to the {@code @Validated} interface. */
    private static Object validated(ActivationController controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor((jakarta.validation.Validator) validator));
        return proxy.getProxy();
    }
}
