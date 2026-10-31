package ai.speckitty.charter.deployable;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * Binds and validates {@link ContainerProperties} under the {@code container} profile only (ADR-0041 decision 1).
 * Without the profile nothing at {@code charter.container.*} is bound, so the local start is unchanged.
 *
 * <p>The profile is read when the service starts, not declared with {@code @Profile}: Spring AOT decides a profile when
 * the native image is built, and an image built without the profile would then never bind or check the namespace
 * (ADR-0046 decision 5). This bean is always registered, so one image serves both starts, and it binds the record
 * itself under the profile; {@code @EnableConfigurationProperties} would bind and validate it whatever the profile.
 * The native image's binding hints for the record are registered by {@code CharterRuntimeHints}. The binding stays
 * eager under {@code spring.main.lazy-initialization}, so no setting can skip the validation.
 */
@Configuration(proxyBeanMethods = false)
public class ContainerWiring implements InitializingBean {

    private final ConfigurableEnvironment environment;

    private @Nullable ContainerProperties properties;

    /**
     * For Spring, which instantiates the configuration class.
     *
     * @param environment the environment the service starts with
     */
    public ContainerWiring(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Binds and validates {@code charter.container.*} when the {@code container} profile is active.
     *
     * @throws org.springframework.boot.context.properties.bind.BindException when a value is missing or invalid; the
     *     constraint's message names the property
     */
    @Override
    public void afterPropertiesSet() {
        if (!environment.matchesProfiles(ContainerProperties.PROFILE)) {
            return;
        }
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ValidationBindHandler validation = new ValidationBindHandler(
                    new IgnoreTopLevelConverterNotFoundBindHandler(),
                    new SpringValidatorAdapter(factory.getValidator()));
            properties = Binder.get(environment)
                    .bindOrCreate("charter.container", Bindable.of(ContainerProperties.class), validation);
        }
    }

    /**
     * The bound settings.
     *
     * @return the settings under the {@code container} profile, or empty without it
     */
    public Optional<ContainerProperties> properties() {
        return Optional.ofNullable(properties);
    }

    /**
     * Keeps the binding eager when {@code spring.main.lazy-initialization} is set, so no setting can skip it.
     *
     * @return the filter that excludes this wiring from lazy initialization
     */
    @Bean
    static LazyInitializationExcludeFilter containerWiringIsEager() {
        return LazyInitializationExcludeFilter.forBeanTypes(ContainerWiring.class);
    }
}
