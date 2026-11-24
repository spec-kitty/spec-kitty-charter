/**
 * Operating-system fixtures the tests make at test time, which git cannot carry. It lies outside
 * {@code ai.speckitty.charter.packs} on purpose: the packs boundary rule that only {@code packs.infra.fetch.git} starts
 * a process (NetworkCodeBoundaryTest) judges the code under that package, and this helper, which runs {@code mkfifo},
 * is test code, never shipped.
 */
@NullMarked
package ai.speckitty.charter.testfixtures;

import org.jspecify.annotations.NullMarked;
