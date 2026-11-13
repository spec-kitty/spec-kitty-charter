package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.Preset;
import ai.speckitty.charter.api.model.PresetItem;
import ai.speckitty.charter.api.model.PresetList;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * How a preset serializes (#98): a kind's list the preset does not carry is left out, never written as {@code null},
 * by the contract rule {@code NullOmissionTest} checks against the schemas ({@code Preset}'s {@code activated<Plural>}
 * arrays are optional and not nullable). The rule reaches the generated DTO only through the mix-in. A native
 * executable also needs the DTOs' reflection hints, which {@link Hints} registers.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(PresetJson.Hints.class)
public class PresetJson {

    /** For Spring, which finds the configuration by component scan. */
    public PresetJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of a preset
     */
    @Bean
    public JacksonModule presetJsonModule() {
        return new SimpleModule("charter-preset-json").setMixInAnnotation(Preset.class, OmitAbsentMembers.class);
    }

    /** The mix-in: every {@code null} member of the target is left out of the JSON. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}

    /**
     * What a native executable needs to write the preset answers (ADR-0046 decision 5): the mix-in is read only for its
     * class annotation, so the type alone is registered, and the DTOs bind through their accessors, so each is
     * registered for binding.
     */
    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            hints.reflection().registerType(OmitAbsentMembers.class);
            new BindingReflectionHintsRegistrar()
                    .registerReflectionHints(
                            hints.reflection(), PresetList.class, Preset.class, PresetItem.class, Link.class);
        }
    }
}
