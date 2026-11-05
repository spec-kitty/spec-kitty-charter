package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.AvailablePacks;
import org.springframework.stereotype.Component;

/**
 * The packs slice, as the activations slice asks it: the one implementation of {@link AvailablePacks}. It is the only
 * class of the activations slice that may name {@code packs}, and it names only {@code packs.application} (and the
 * {@code packs.domain} values its answers expose), never {@code packs.infra}; {@code HexagonalArchitectureTest} pins
 * both. It depends on nothing until the closure read is built (WP09).
 */
@Component
public class PacksAvailability implements AvailablePacks {

    /** For Spring, which finds the adapter by component scan. */
    public PacksAvailability() {}
}
