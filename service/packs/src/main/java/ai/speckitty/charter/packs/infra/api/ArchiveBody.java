package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

/**
 * The body of a pack upload once {@link ArchiveBodyAdvice} judged it: the bytes the converter read, bounded by the
 * 32 MiB limit, and the archive format the {@code Content-Type} named and the leading bytes confirmed.
 */
public final class ArchiveBody extends AbstractResource {

    private final ArchiveFormat format;
    private final Resource bytes;

    /**
     * A judged upload.
     *
     * @param format the archive format
     * @param bytes the body the converter read
     */
    public ArchiveBody(ArchiveFormat format, Resource bytes) {
        this.format = Objects.requireNonNull(format, "format");
        this.bytes = Objects.requireNonNull(bytes, "bytes");
    }

    /**
     * The format the upload is in.
     *
     * @return the format
     */
    public ArchiveFormat format() {
        return format;
    }

    @Override
    public String getDescription() {
        return "a pack archive upload";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return bytes.getInputStream();
    }
}
