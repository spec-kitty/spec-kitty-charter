package ai.speckitty.charter.packs.infra.storage;

import java.util.Optional;

/**
 * The rule that a file's declared {@code id} is its file stem (ADR-0036 S1), shared by every file that carries it, so
 * the store's load and validate mode refuse the same files: a preset ({@code presets/<id>.yaml}, ADR-0042) and an
 * artifact whose binding {@linkplain KindBinding#idIsFileStem() declares it} ({@code <id>.<fileToken>.yaml}, a
 * procedure, ADR-0043).
 */
final class FileStems {

    private FileStems() {}

    /**
     * Why a file's declared id is not its file stem, or empty when it is.
     *
     * @param what the file's kind, as the reason names it ({@code preset}, {@code procedure})
     * @param file the pack-relative path of the file
     * @param suffix the file-name suffix the stem is the rest of ({@code .yaml}, {@code .procedure.yaml})
     * @param id the {@code id} the file declares
     * @return the reason, {@code <what> id '<id>' is not its file stem '<stem>'}; empty when they agree
     */
    static Optional<String> mismatch(String what, String file, String suffix, String id) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        String stem = name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
        return stem.equals(id)
                ? Optional.empty()
                : Optional.of(what + " id '" + id + "' is not its file stem '" + stem + "'");
    }

    /**
     * Why an artifact file's declared id is not its file stem, or empty when it is or the kind has no such rule.
     *
     * @param binding the artifact's kind
     * @param file the pack-relative path of the artifact's file
     * @param id the {@code id} the file declares
     * @return the reason; empty when they agree or {@link KindBinding#idIsFileStem()} is {@code false}
     */
    static Optional<String> mismatch(KindBinding<?, ?> binding, String file, String id) {
        if (!binding.idIsFileStem()) {
            return Optional.empty();
        }
        Optional<String> stem = mismatch(binding.kind().token(), file, "." + binding.fileToken() + ".yaml", id);
        if (stem.isPresent()
                || !binding.placedAtItsFileName()
                || binding.fileName(id).equals(file)) {
            return stem;
        }
        return Optional.of(binding.kind().token() + " '" + id + "' is not at its place '" + binding.fileName(id) + "'");
    }
}
