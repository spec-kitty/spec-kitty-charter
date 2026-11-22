package ai.speckitty.charter.packdoctor.badfixture;

import org.springframework.stereotype.Component;

/** A pack-doctor class that is a Spring bean: the CLI runs without Spring, so the rule must refuse it. */
@Component
public final class CheckUsesSpring {}
