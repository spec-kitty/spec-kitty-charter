package ai.speckitty.charter.deployable.transportfixture;

import ai.speckitty.charter.api.model.ActivationConfig;

/**
 * A composition-root class that names a generated transport type: the known-bad that proves the transport rule no
 * longer exempts the composition root.
 */
public final class RootNamesGeneratedDto {

    private RootNamesGeneratedDto() {}

    /**
     * A generated DTO held in the composition root.
     *
     * @param document the type named
     * @return the same value
     */
    public static Object hold(ActivationConfig document) {
        return document;
    }
}
