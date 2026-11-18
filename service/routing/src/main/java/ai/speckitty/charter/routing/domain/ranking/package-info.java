/**
 * The ranker of the model recommendation (ADR-0080 decisions 10 to 12; data-model rules 1 to 4, 6 and 7): plain Java,
 * in exact decimal. It answers the catalog's candidates for a task type and the tiers in play; the profile candidate and
 * the tier's source are joined by the read that serves it.
 */
@NullMarked
package ai.speckitty.charter.routing.domain.ranking;

import org.jspecify.annotations.NullMarked;
