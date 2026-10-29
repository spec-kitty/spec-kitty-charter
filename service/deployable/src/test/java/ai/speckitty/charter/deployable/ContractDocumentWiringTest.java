package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.infra.api.ContractDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ContractDocumentWiringTest {

    @Test
    void theWiringLoadsTheBundleTheContractBuildWrote() throws IOException {
        byte[] bundle = Files.readAllBytes(Path.of(System.getProperty("charter.test.contract-bundle")));

        new ApplicationContextRunner()
                .withUserConfiguration(ContractDocumentWiring.class)
                .run(context -> assertThat(
                                context.getBean(ContractDocument.class).text().getBytes(StandardCharsets.UTF_8))
                        .isEqualTo(bundle));
    }

    @Test
    void aMissingBundleRefusesTheStart() {
        assertThatThrownBy(() -> ContractDocumentWiring.load(ContractDocumentWiring.class, "absent.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("absent.yaml is missing from the build; the service cannot serve its contract");
    }

    @Test
    void aBundleThatIsNotUtf8RefusesTheStart() {
        assertThatThrownBy(() -> ContractDocumentWiring.load(
                        ContractDocumentWiringTest.class, "contract-document/not-utf8.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("contract-document/not-utf8.yaml cannot be served:");
    }
}
