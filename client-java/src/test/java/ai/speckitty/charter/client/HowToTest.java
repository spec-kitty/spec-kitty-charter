package ai.speckitty.charter.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The "generate your own client" how-to names the generator version this build uses, so a reader generates with the
 * same one. A bump of the catalog's openapi-generator version fails here until the page follows it.
 */
class HowToTest {

    private static final Pattern NAMED_VERSION =
            Pattern.compile("openapi-generator(?:-cli)?[ /-]+(\\d+\\.\\d+\\.\\d+)");

    @Test
    void everyGeneratorVersionTheHowToNamesIsTheBuildsPin() throws IOException {
        String pinned = System.getProperty("charter.client.generator-version");
        String page = Files.readString(Path.of(System.getProperty("charter.client.how-to")), StandardCharsets.UTF_8);

        Matcher named = NAMED_VERSION.matcher(page);
        int count = 0;
        while (named.find()) {
            assertThat(named.group(1))
                    .as("generator version at offset %d", named.start())
                    .isEqualTo(pinned);
            count++;
        }
        assertThat(count).as("generator versions named on the page").isGreaterThanOrEqualTo(3);
    }
}
