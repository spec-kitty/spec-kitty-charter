package ai.speckitty.charter.deployable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The settings of the {@code container} profile (ADR-0041 decision 2), bound at {@code charter.container.*} and only
 * under that profile ({@link ContainerWiring}). A missing or invalid value fails the start, naming the property. Keys
 * outside this namespace ({@code server.*}, {@code charter.config-root}, {@code charter.worktree-token}) are not part
 * of it, and no component holds a secret (ADR-0041 decision 8). Each constraint's message names the property in its
 * canonical form, because Spring reports the field by its Java name ({@code allowedHosts}). Spring's start-up report
 * prints a rejected value, so a secret must never become a component here.
 *
 * @param allowedHosts the host names a request's {@code Host} may carry under the container profile,
 *     {@code charter.container.allowed-hosts} (ADR-0041 decision 6); required, with at least one entry and no blank
 *     entry
 */
@ConfigurationProperties("charter.container")
@Validated
public record ContainerProperties(
        @NotEmpty(
                message =
                        "charter.container.allowed-hosts is required under the container profile: name at least one host")
        List<@NotBlank(message = "charter.container.allowed-hosts must not hold a blank host") String> allowedHosts) {

    /** The profile of a containerized service; activated only explicitly, never a default (ADR-0041 decision 1). */
    public static final String PROFILE = "container";

    /**
     * The directory the profile's file imports as a required config tree (ADR-0041 decision 3). In the namespace but not
     * a component: the import reads it, through a placeholder, before anything is bound.
     */
    static final String SECRETS_DIR_PROPERTY = "charter.container.secrets-dir";

    /**
     * The secrets directory when {@link #SECRETS_DIR_PROPERTY} is not set. Kept in sync with the
     * {@code configtree:${charter.container.secrets-dir:/run/secrets}/} default in
     * {@code application-container.yaml}; drift between the two is caught by {@code ShippedConfigurationTest}.
     */
    static final String DEFAULT_SECRETS_DIR = "/run/secrets";
}
