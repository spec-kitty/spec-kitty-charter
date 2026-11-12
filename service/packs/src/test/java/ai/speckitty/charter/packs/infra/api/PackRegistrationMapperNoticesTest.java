package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.web.RefusalHandler;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Notices name activatable kinds only (operator ruling, decision 01M6VXMCDQJWBJXGRQNMF5G6SV; widening is #417): an
 * entry of another kind is left out of the response, and the mapper logs it at WARN, naming the URN.
 */
class PackRegistrationMapperNoticesTest {

    private final PackRegistrationMapper mapper =
            new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler());

    private PackRegistration resultWith(RegistrationImpact.Notices notices) {
        return resultWith(notices, false);
    }

    private PackRegistration resultWith(RegistrationImpact.Notices notices, boolean hasDocument) {
        return mapper.result(
                new RegistrationOutcome(
                        Outcome.CREATED,
                        new PackIdentifier("acme"),
                        new PackDescriptor(
                                "01M4P01D5TWB5FKZGNNQG3943P",
                                "1.0.0",
                                Optional.empty(),
                                "acme",
                                Optional.of(PackLevel.ORG),
                                Optional.empty()),
                        PackLevel.ORG,
                        PackRevision.compute(List.of("x")),
                        notices,
                        List.of(),
                        hasDocument),
                Map.of());
    }

    @Test
    void aNoticeOnATemplateIsNotInTheResponseAndIsLoggedWithItsUrn() {
        Logger logger = (Logger) LoggerFactory.getLogger(PackRegistrationMapper.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            PackRegistration result = resultWith(new RegistrationImpact.Notices(
                    List.of("directive:DIRECTIVE_001", "template:spec-template"),
                    List.of(),
                    List.of("template:other"),
                    List.of()));

            assertThat(result.getNotices().getFloorAdded())
                    .extracting(ArtifactReference::getArtifactId)
                    .containsExactly("DIRECTIVE_001");
            assertThat(result.getNotices().getLocksAdded()).isEmpty();
            assertThat(events.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(2)
                    .anySatisfy(message -> assertThat(message).contains("template:spec-template"))
                    .anySatisfy(message -> assertThat(message).contains("template:other"));
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void theActivationsAffectedAreServedInTheOrderGivenAndTheListIsAlwaysThere() {
        PackRegistration some = resultWith(
                new RegistrationImpact.Notices(List.of(), List.of(), List.of(), List.of(), List.of("a-1", "a-3")));
        PackRegistration none = resultWith(RegistrationImpact.Notices.NONE);

        assertThat(some.getNotices().getActivationsAffected()).containsExactly("a-1", "a-3");
        assertThat(none.getNotices().getActivationsAffected()).isNotNull().isEmpty();
    }

    @Test
    void anActivatableEntryIsKeptAndNothingIsLogged() {
        Logger logger = (Logger) LoggerFactory.getLogger(PackRegistrationMapper.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            PackRegistration result = resultWith(new RegistrationImpact.Notices(
                    List.of("directive:DIRECTIVE_001"), List.of(), List.of(), List.of()));

            assertThat(result.getNotices().getFloorAdded()).hasSize(1);
            assertThat(events.list).isEmpty();
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void aResultLinksTheDocumentExactlyWhenThePackHoldsOne() {
        assertThat(resultWith(RegistrationImpact.Notices.NONE, true).getLinks())
                .containsKey("doc")
                .extractingByKey("doc")
                .extracting(link -> link.getHref())
                .isEqualTo("/api/v1/packs/acme/doc");
        assertThat(resultWith(RegistrationImpact.Notices.NONE, false).getLinks())
                .doesNotContainKey("doc");
    }

    @Test
    void aResultLinksThePresetListWhetherOrNotThePackHoldsADocument() {
        for (boolean hasDocument : new boolean[] {true, false}) {
            assertThat(resultWith(RegistrationImpact.Notices.NONE, hasDocument).getLinks())
                    .extractingByKey("presets")
                    .extracting(link -> link.getHref())
                    .isEqualTo("/api/v1/packs/acme/presets");
        }
    }
}
