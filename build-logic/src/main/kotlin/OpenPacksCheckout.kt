import java.io.File

/**
 * Where the build looks for the spec-kitty-open-packs checkout that the corpus tests read (#322). The `openPacksDir`
 * Gradle property names it; a relative path is resolved against the repository root, the directory `./gradlew` is run
 * from, never against the directory of the project that reads it. Without the property, the checkout is the sibling
 * of the repository root, `../spec-kitty-open-packs`.
 */
object OpenPacksCheckout {

    /** The default checkout, relative to the repository root. */
    const val DEFAULT = "../spec-kitty-open-packs"

    /**
     * The checkout directory, absolute and normalized.
     *
     * @param repositoryRoot the root project directory
     * @param openPacksDir the `openPacksDir` property, or `null` when it is not set
     * @throws IllegalArgumentException when the property is set but blank, which would name the repository root itself
     */
    fun resolve(repositoryRoot: File, openPacksDir: String?): File {
        require(openPacksDir == null || openPacksDir.isNotBlank()) {
            "-PopenPacksDir is empty; pass a checkout path, absolute or relative to the repository root, or leave it" +
                " out for $DEFAULT"
        }
        return repositoryRoot.toPath().toAbsolutePath().resolve(openPacksDir ?: DEFAULT).normalize().toFile()
    }
}
