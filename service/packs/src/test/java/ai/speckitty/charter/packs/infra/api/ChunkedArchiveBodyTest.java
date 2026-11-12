package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.RefusalHandler;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * A chunked body over 32 MiB is a {@code 413} through Spring's own message conversion, not a {@code 400}: the advice's
 * refusal is raised while the converter reads the stream, and the converter must let it through. The request declares no
 * length (a chunked one cannot), so only the count of the streamed bytes can refuse it.
 */
class ChunkedArchiveBodyTest {

    @Test
    void aChunkedBodyOverTheLimitIsRefused413ThroughTheMessageConverterAndFreesItsSlot(@TempDir Path staging)
            throws Exception {
        RegistrationJobs jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                (AuditLog) entry -> {},
                RegistrationJobs.JobGate.OPEN,
                RegistrationJobs.StorageRoom.UNBOUNDED,
                new FileStagingArea(staging)));
        try {
            ServiceInfoQuery info =
                    new ServiceInfoQuery("worktree-chunked", new ConsistentRead(new FileSystemPackSources(List::of)));
            PackRegistrationController controller = new PackRegistrationController(
                    jobs,
                    mock(PackSummaryQuery.class),
                    mock(PackArchiveReader.class),
                    new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                    PackSourceIntake.none(),
                    mock(PackRemovalController.class),
                    mock(PackDocumentController.class));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                    .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                    .setControllerAdvice(new ArchiveBodyAdvice(), new RefusalHandler())
                    .addPlaceholderValue("charter.api.base-path", "/api/v1")
                    .build();
            byte[] body = new byte[(int) ArchiveBodyAdvice.MAX_BODY_BYTES + 1];
            body[0] = 0x1f;
            body[1] = (byte) 0x8b;

            MockHttpServletResponse response = mvc.perform(put("/api/v1/packs/acme")
                            .contentType("application/gzip")
                            .content(body)
                            .with(request -> withoutADeclaredLength(request, body)))
                    .andExpect(status().is(413))
                    .andReturn()
                    .getResponse();

            assertThat(response.getContentAsString()).doesNotContain("\"code\"");
            assertThat(jobs.retained())
                    .as("the refused upload leaves no job behind")
                    .isZero();
            assertThat(jobs.owned()).as("and no staging directory").isEmpty();
        } finally {
            jobs.close();
        }
    }

    /** The same request, as a chunked one arrives: no Content-Length, a Transfer-Encoding, the bytes streamed. */
    private static MockHttpServletRequest withoutADeclaredLength(MockHttpServletRequest from, byte[] body) {
        MockHttpServletRequest chunked =
                new MockHttpServletRequest(from.getServletContext(), from.getMethod(), from.getRequestURI()) {
                    @Override
                    public int getContentLength() {
                        return -1;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }
                };
        Collections.list(from.getHeaderNames()).stream()
                .filter(name -> !"Content-Length".equalsIgnoreCase(name))
                .forEach(name -> chunked.addHeader(name, from.getHeader(name)));
        chunked.addHeader("Transfer-Encoding", "chunked");
        chunked.setContentType(from.getContentType());
        chunked.setContent(body);
        return chunked;
    }
}
