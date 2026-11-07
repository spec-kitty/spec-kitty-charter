package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** The local path source (skeleton: signatures only, built next). */
public final class LocalSourceReader implements PackSourceFetcher {

    /**
     * A reader of the directories under a root.
     *
     * @param root the configured root, or null when none is set
     * @param configRoot the configuration root the root must not overlap, or null when none is set
     * @param reader the writer of the staged tree
     */
    public LocalSourceReader(@Nullable Path root, @Nullable Path configRoot, PackTreeReader reader) {}

    @Override
    public PackSourceKind kind() {
        return PackSourceKind.FILESYSTEM;
    }

    @Override
    public Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal {
        throw new UnsupportedOperationException("the local source reader is not built yet");
    }
}
