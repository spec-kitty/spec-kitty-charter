package ai.speckitty.charter.deployable.seamfixture;

import ai.speckitty.charter.deployable.bdd.ScenarioPacks;

/** Depends on a class of a {@code bdd} package, as no main class may; the architecture test imports it as a plant. */
public final class UsesSeam {

    private UsesSeam() {}

    /**
     * The dependency.
     *
     * @return a new seam object
     */
    public static Object seam() {
        return new ScenarioPacks();
    }
}
