package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.ArtifactRemovalService;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RelationshipRemovalService;
import ai.speckitty.charter.packs.application.RevisionTrail;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.RefusalHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the two {@code DELETE}s (IC-06): {@code 204} with the new revision as the {@code ETag}, and each
 * refusal with its status and code; every outcome is entered in the audit log once (NFR-010, IC-18), by the audit filter
 * over the answer, whichever rule refused.
 */
class PackRemovalControllerTest {

    private static final String ARTIFACT = "/api/v1/packs/qa/artifacts/directive/D1";
    private static final String RELATIONSHIP = "/api/v1/packs/qa/relationships?from=directive:D1&to=directive:D2";
    private static final PackRevision REVISION = PackRevision.computeOverBytes(List.of(new byte[] {1}));
    private static final PackRevision BEFORE = PackRevision.computeOverBytes(List.of(new byte[] {2}));

    private final ArtifactRemovalService artifacts = mock(ArtifactRemovalService.class);
    private final RelationshipRemovalService relationships = mock(RelationshipRemovalService.class);
    private final List<AuditLog.Entry> entries = new ArrayList<>();
    private final RegistrationSwitch registration = new RegistrationSwitch(true);
    private final ServiceInfoQuery info =
            new ServiceInfoQuery("worktree-1", new ConsistentRead(new FileSystemPackSources(List::of)));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PackRegistrationController(
                    mock(RegistrationJobs.class),
                    mock(PackSummaryQuery.class),
                    mock(PackArchiveReader.class),
                    mock(PackRegistrationMapper.class),
                    PackSourceIntake.none(),
                    new PackRemovalController(artifacts, relationships),
                    mock(PackDocumentController.class)))
            .addInterceptors(new RegistrationAdmission(registration, mock(RegistrationJobs.class), info))
            .setControllerAdvice(new RefusalHandler())
            .addFilters(new RegistrationAuditFilter(
                    entries::add, info, "/api/v1", Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC)))
            .build();

    private static MockHttpServletRequestBuilder withIfMatch(MockHttpServletRequestBuilder request) {
        return request.header("If-Match", "*");
    }

    /** What the services do on a removal that changed the pack: tell the trail, then answer the new revision. */
    private static PackRevision movedFrom(RevisionTrail trail) {
        trail.moved(BEFORE, REVISION);
        return REVISION;
    }

    private void artifactRemovalAnswers(Throwable failure) {
        when(artifacts.remove(any(PackIdentifier.class), eq("directive"), eq("D1"), any(), any()))
                .thenThrow(failure);
    }

    private void relationshipRemovalAnswers(Throwable failure) {
        when(relationships.remove(
                        any(PackIdentifier.class),
                        any(PackQualifiedReference.class),
                        any(PackQualifiedReference.class),
                        any(),
                        any()))
                .thenThrow(failure);
    }

    @Test
    void deletingAnArtifactAnswers204WithTheNewRevisionAsTheETag() throws Exception {
        when(artifacts.remove(eq(new PackIdentifier("qa")), eq("directive"), eq("D1"), eq("*"), any()))
                .thenAnswer(invocation -> movedFrom(invocation.getArgument(4)));

        mvc.perform(withIfMatch(delete(ARTIFACT)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("ETag", "\"" + REVISION.token() + "\""));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo("succeeded:204");
            assertThat(entry.operation()).isEqualTo("deletePackArtifact");
            assertThat(entry.packId()).isEqualTo("qa");
            assertThat(entry.revisionBefore())
                    .as("the revision the delete found")
                    .isEqualTo(BEFORE.token());
            assertThat(entry.revisionAfter()).as("the revision it left").isEqualTo(REVISION.token());
        });
    }

    @Test
    void bothDeletesAreRefused503WhileRegistrationIsSwitchedOffAndNothingIsRemoved() throws Exception {
        registration.set(false);

        for (String path : List.of(ARTIFACT, RELATIONSHIP)) {
            mvc.perform(withIfMatch(delete(path)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("pack_source_unavailable"));
        }
        // Not even a request without If-Match gets to the precondition rule: the switch is the first answer.
        mvc.perform(delete(ARTIFACT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pack_source_unavailable"));

        verifyNoInteractions(artifacts, relationships);
        assertThat(entries)
                .hasSize(3)
                .allSatisfy(entry -> assertThat(entry.outcome()).isEqualTo("refused:503"));
    }

    @Test
    void deletingARelationshipAnswers204WithTheNewRevisionAsTheETag() throws Exception {
        when(relationships.remove(
                        eq(new PackIdentifier("qa")),
                        eq(PackQualifiedReference.parse("directive:D1")),
                        eq(PackQualifiedReference.parse("directive:D2")),
                        eq("*"),
                        any()))
                .thenAnswer(invocation -> movedFrom(invocation.getArgument(4)));

        mvc.perform(withIfMatch(delete(RELATIONSHIP)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("ETag", "\"" + REVISION.token() + "\""));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo("succeeded:204");
            assertThat(entry.operation()).isEqualTo("deletePackRelationship");
            assertThat(entry.revisionBefore())
                    .as("the revision the delete found")
                    .isEqualTo(BEFORE.token());
            assertThat(entry.revisionAfter()).as("the revision it left").isEqualTo(REVISION.token());
        });
    }

    @Test
    void everyRefusalOfAnArtifactDeleteHasItsStatusAndCodeAndIsAuditedOnce() throws Exception {
        List<Object[]> outcomes = List.of(
                new Object[] {new CharterRefusal(RefusalCode.DIRECTIVE_NOT_FOUND, "No D1."), 404, "directive_not_found"
                },
                new Object[] {
                    new CharterRefusal(RefusalCode.ARTIFACT_REFERENCED, "Referred to."), 409, "artifact_referenced"
                },
                new Object[] {
                    new CharterRefusal(RefusalCode.PACK_SIBLING_CONFLICT, "Broken."), 422, "pack_sibling_conflict"
                },
                new Object[] {new PreconditionException(PreconditionException.Kind.STALE, "Stale."), 412, null},
                new Object[] {new PreconditionException(PreconditionException.Kind.MISSING, "Required."), 428, null});

        for (Object[] outcome : outcomes) {
            artifactRemovalAnswers((Throwable) outcome[0]);
            int before = entries.size();

            var result = mvc.perform(withIfMatch(delete(ARTIFACT))).andExpect(status().is((Integer) outcome[1]));
            if (outcome[2] != null) {
                result.andExpect(jsonPath("$.code").value(outcome[2]));
            }

            assertThat(entries).as("one audit entry for %s", outcome[1]).hasSize(before + 1);
            assertThat(entries.getLast().outcome()).isEqualTo("refused:" + outcome[1]);
            assertThat(entries.getLast().operation()).isEqualTo("deletePackArtifact");
        }
    }

    @Test
    void everyRefusalOfARelationshipDeleteHasItsStatusAndCodeAndIsAuditedOnce() throws Exception {
        List<Object[]> outcomes = List.of(
                new Object[] {
                    new CharterRefusal(RefusalCode.RELATIONSHIP_NOT_FOUND, "None."), 404, "relationship_not_found"
                },
                new Object[] {
                    new CharterRefusal(RefusalCode.RELATIONSHIP_NOT_REGISTERED, "Authored."),
                    409,
                    "relationship_not_registered"
                },
                new Object[] {
                    new CharterRefusal(RefusalCode.OVERRIDE_NOT_PERMITTED, "Locked."), 422, "override_not_permitted"
                },
                new Object[] {new PreconditionException(PreconditionException.Kind.STALE, "Stale."), 412, null},
                new Object[] {new PreconditionException(PreconditionException.Kind.MISSING, "Required."), 428, null});

        for (Object[] outcome : outcomes) {
            relationshipRemovalAnswers((Throwable) outcome[0]);
            int before = entries.size();

            var result = mvc.perform(withIfMatch(delete(RELATIONSHIP))).andExpect(status().is((Integer) outcome[1]));
            if (outcome[2] != null) {
                result.andExpect(jsonPath("$.code").value(outcome[2]));
            }

            assertThat(entries).as("one audit entry for %s", outcome[1]).hasSize(before + 1);
            assertThat(entries.getLast().outcome()).isEqualTo("refused:" + outcome[1]);
            assertThat(entries.getLast().operation()).isEqualTo("deletePackRelationship");
        }
    }

    @Test
    void theCompositionCodesAreA422OnBothDeletes() throws Exception {
        artifactRemovalAnswers(new CharterRefusal(RefusalCode.PACK_DEPENDENCY_UNKNOWN, "Unknown."));
        relationshipRemovalAnswers(new CharterRefusal(RefusalCode.PACK_DEPENDENCY_UNKNOWN, "Unknown."));

        mvc.perform(withIfMatch(delete(ARTIFACT))).andExpect(status().is(422));
        mvc.perform(withIfMatch(delete(RELATIONSHIP))).andExpect(status().is(422));
    }

    @Test
    void aQueryParameterThatIsNoReferenceIsA400AndNothingIsRemoved() throws Exception {
        mvc.perform(withIfMatch(delete("/api/v1/packs/qa/relationships?from=a::b::c&to=directive:D2")))
                .andExpect(status().isBadRequest());

        assertThat(entries)
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("refused:400"));
    }

    @Test
    void aRefusedDeleteEntersTheRevisionTheServiceFoundAsTheRevisionBeforeAndTheUnchangedPackAfter() throws Exception {
        List<Throwable> refusals = List.of(
                new CharterRefusal(RefusalCode.ARTIFACT_REFERENCED, "Referred to."),
                new PreconditionException(PreconditionException.Kind.STALE, "Stale."),
                new CharterRefusal(RefusalCode.PACK_SIBLING_CONFLICT, "Broken."));

        for (Throwable refusal : refusals) {
            when(artifacts.remove(any(PackIdentifier.class), eq("directive"), eq("D1"), any(), any()))
                    .thenAnswer(invocation -> refusedAfterFinding(invocation.getArgument(4), refusal));
            when(relationships.remove(
                            any(PackIdentifier.class),
                            any(PackQualifiedReference.class),
                            any(PackQualifiedReference.class),
                            any(),
                            any()))
                    .thenAnswer(invocation -> refusedAfterFinding(invocation.getArgument(4), refusal));
            entries.clear();

            mvc.perform(withIfMatch(delete(ARTIFACT)));
            mvc.perform(withIfMatch(delete(RELATIONSHIP)));

            assertThat(entries)
                    .as("both deletes refused with %s", refusal.getClass().getSimpleName())
                    .hasSize(2)
                    .allSatisfy(entry -> {
                        assertThat(entry.outcome()).startsWith("refused:");
                        assertThat(entry.revisionBefore())
                                .as("the revision the service found")
                                .isEqualTo(BEFORE.token());
                        assertThat(entry.revisionAfter())
                                .as("nothing moved, so the pack is as it was")
                                .isEqualTo(BEFORE.token());
                    });
        }
    }

    @Test
    void aDeleteRefusedBeforeTheServiceReadTheRevisionEntersNoRevision() throws Exception {
        artifactRemovalAnswers(new PreconditionException(PreconditionException.Kind.MISSING, "Required."));

        mvc.perform(withIfMatch(delete(ARTIFACT))).andExpect(status().is(428));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.revisionBefore()).isEmpty();
            assertThat(entry.revisionAfter()).isEmpty();
        });
    }

    /** What the services do on a refusal after the If-Match check: tell the trail what was found, then refuse. */
    private static Object refusedAfterFinding(RevisionTrail trail, Throwable refusal) throws Throwable {
        trail.found(BEFORE);
        throw refusal;
    }
}
