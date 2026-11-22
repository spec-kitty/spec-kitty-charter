/**
 * The pack-set check (#478, FR-002 to FR-004, FR-007): validate mode per pack ({@link
 * ai.speckitty.charter.packvalidation.PackCheck}), then {@code CLOSURE} with its preset phase over the set as given (D5),
 * on the composition facts each pack's one read already carries, and the round trip per pack when asked. When a pack
 * has no facts or no level, {@code CLOSURE} is skipped and the verdict says so (D3). It names only the packs domain and
 * the kernel ({@code PACK_VALIDATION_IS_FRAMEWORK_FREE}); a composition root, pack-doctor's main, supplies the reader and
 * the round trip.
 */
@NullMarked
package ai.speckitty.charter.packvalidation.set;

import org.jspecify.annotations.NullMarked;
