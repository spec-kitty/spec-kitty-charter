package ai.speckitty.charter.packs.application;

import java.nio.file.Path;

/**
 * A pack directory extracted into staging (IC-03).
 *
 * @param directory the pack's directory inside the staging root; the archive's one shared leading directory, when it
 *     had one, is already stripped
 */
public record StagedPack(Path directory) {}
