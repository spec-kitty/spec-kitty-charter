package ai.speckitty.charter.packs.infra.api;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The settings of whole-pack registration (IC-18, FR-037): {@code charter.registration.*}.
 *
 * @param enabled whether registration is switched on; off by default, so a service that was never configured for it
 *     refuses every registration {@code 503 pack_source_unavailable}
 * @param stagingDir where a registration spools and extracts its upload; unset, the directory is
 *     {@code <config-root>/staging}, on the volume of the packs
 */
@ConfigurationProperties("charter.registration")
public record RegistrationProperties(
        @DefaultValue("false") boolean enabled, @Nullable Path stagingDir) {}
