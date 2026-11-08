package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * The service as it runs reads an archive upload as a stream (#418, A3): the converter that binds a {@code Resource}
 * request body is the streaming one, so the 32 MiB body of {@code PUT /packs/{packId}} never sits in the heap. The
 * unit-level behavior is {@code StreamedUploadTest}'s; this pins that the service registers it.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ArchiveUploadStreamingWiringTest.TOKEN)
class ArchiveUploadStreamingWiringTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    @Test
    void theConverterThatReadsAResourceBodyIsTheStreamingOne() {
        List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();

        for (String type :
                List.of("application/gzip", "application/x-gzip", "application/x-tar+gzip", "application/zip")) {
            HttpMessageConverter<?> reads = converters.stream()
                    .filter(converter -> converter.canRead(Resource.class, MediaType.parseMediaType(type)))
                    .findFirst()
                    .orElseThrow();
            assertThat(reads.getClass().getSimpleName())
                    .as("the converter that reads %s into a Resource", type)
                    .isEqualTo("StreamingResourceConverter");
        }
    }
}
