import java.io.File

/**
 * The prebuilt contract bundle that `-PcontractBundle` names for `:deployable` to package (#324). A relative path
 * resolves against the repository root (the root project's directory), never against the directory of the project
 * that reads it nor the shell's working directory, as `-PopenPacksDir` does ([OpenPacksCheckout]).
 */
object ContractBundleFile {

    /**
     * The bundle file, absolute and normalized.
     *
     * @param repositoryRoot the root project directory
     * @param contractBundle the `contractBundle` property
     * @throws IllegalArgumentException when the property is blank, which would name the repository root itself
     */
    fun resolve(repositoryRoot: File, contractBundle: String): File {
        require(contractBundle.isNotBlank()) {
            "-PcontractBundle is empty; pass the bundle's path, absolute or relative to the repository root, or" +
                " leave it out to bundle the contract in this build"
        }
        return repositoryRoot.toPath().toAbsolutePath().resolve(contractBundle).normalize().toFile()
    }
}
