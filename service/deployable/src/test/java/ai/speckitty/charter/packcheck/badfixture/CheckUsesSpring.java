package ai.speckitty.charter.packcheck.badfixture;

import org.springframework.stereotype.Component;

/** A pack-check class that is a Spring bean: the CLI runs without Spring, so the rule must refuse it. */
@Component
public final class CheckUsesSpring {}
