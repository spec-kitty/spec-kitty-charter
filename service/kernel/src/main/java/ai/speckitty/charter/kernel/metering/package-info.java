/**
 * Counting the work one call does, for the caller that measures it (ADR-0075 AA5, #659). A
 * {@link ai.speckitty.charter.kernel.metering.WorkMeter} is bound around one call and counts the
 * {@link ai.speckitty.charter.kernel.metering.WorkUnit}s done inside it on that thread; each module names its own units.
 * Depends on {@code java} and JSpecify only.
 */
@NullMarked
package ai.speckitty.charter.kernel.metering;

import org.jspecify.annotations.NullMarked;
