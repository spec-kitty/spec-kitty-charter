package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** What {@link RefusalHandler} logs for a refusal: a routine, retryable one is quiet; a real server fault is loud. */
class RefusalHandlerLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RefusalHandler.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level before;

    @BeforeEach
    void capture() {
        before = logger.getLevel();
        logger.setLevel(Level.TRACE);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void release() {
        logger.detachAppender(events);
        logger.setLevel(before);
    }

    @Test
    void aStaleInputIsLoggedAtInfoWithoutAThrowable() {
        new RefusalHandler().refused(new CharterRefusal(RefusalCode.STALE_INPUT, "changed while read"));

        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("stale_input");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void aServerRefusalOtherThanStaleInputIsStillLoggedAtErrorWithItsCause() {
        new RefusalHandler().refused(new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot read"));

        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void aNotFoundRefusalIsNotLogged() {
        new RefusalHandler().refused(new CharterRefusal(RefusalCode.MISSION_TYPE_NOT_FOUND, "no such type"));

        assertThat(events.list).isEmpty();
    }
}
