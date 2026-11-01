package ai.speckitty.charter.contracts.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/** How the contract's checks read a bundle that keeps components (#294): each reference as the component it names. */
class BundleDocumentTest {

    @Test
    void aComponentReferenceIsReadAsTheComponentItNamesAtEveryUse() {
        Object inlined = BundleDocument.inline(yaml("""
                paths:
                  /a:
                    get:
                      responses:
                        '200':
                          $ref: '#/components/responses/Ok'
                  /b:
                    get:
                      responses:
                        '200':
                          $ref: '#/components/responses/Ok'
                components:
                  responses:
                    Ok:
                      description: Ok.
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Probe'
                  schemas:
                    Probe:
                      type: string
                """));

        assertThat(inlined).isEqualTo(yaml("""
                        paths:
                          /a:
                            get:
                              responses:
                                '200': {ok}
                          /b:
                            get:
                              responses:
                                '200': {ok}
                        components:
                          responses:
                            Ok: {ok}
                          schemas:
                            Probe:
                              type: string
                        """.replace(
                        "{ok}", "{description: Ok., content: {application/json: {schema: {type: string}}}}")));
    }

    @Test
    void aReferenceNoComponentResolvesIsLeftInPlace() {
        Object document = yaml("""
                paths:
                  /a:
                    $ref: '#/components/pathItems/missing'
                  /b:
                    $ref: ../paths/b.yaml
                """);

        assertThat(BundleDocument.inline(document)).isEqualTo(document);
    }

    @Test
    void aReferenceWithSiblingKeysIsLeftInPlace() {
        Object document = yaml("""
                schema:
                  $ref: '#/components/schemas/Probe'
                  description: A sibling.
                components:
                  schemas:
                    Probe:
                      type: string
                """);

        assertThat(BundleDocument.inline(document)).isEqualTo(document);
    }

    @Test
    void aComponentThatRefersToItselfIsLeftAsAReferenceWhereItRecurs() {
        Object inlined = BundleDocument.inline(yaml("""
                schema:
                  $ref: '#/components/schemas/Tree'
                components:
                  schemas:
                    Tree:
                      properties:
                        child:
                          $ref: '#/components/schemas/Tree'
                """));

        assertThat(inlined).isEqualTo(yaml("""
                        schema:
                          properties:
                            child:
                              $ref: '#/components/schemas/Tree'
                        components:
                          schemas:
                            Tree:
                              properties:
                                child:
                                  $ref: '#/components/schemas/Tree'
                        """));
    }

    @Test
    void anEscapedComponentNameResolves() {
        Object inlined = BundleDocument.inline(yaml("""
                schema:
                  $ref: '#/components/schemas/a~1b~0c'
                components:
                  schemas:
                    a/b~c:
                      type: string
                """));

        assertThat(inlined).isEqualTo(yaml("""
                        schema:
                          type: string
                        components:
                          schemas:
                            a/b~c:
                              type: string
                        """));
    }

    @Test
    void aBundleIsReadWithTheYamlReadersDefaultLimit(@TempDir Path dir) throws IOException {
        // The bundle keeps components and stays far below snakeyaml's default 3 MiB of code points (#294), so no
        // reader of it raises the limit any more.
        Path small = Files.writeString(dir.resolve("small.yaml"), "openapi: 3.1.0\n");
        Path large = Files.writeString(dir.resolve("large.yaml"), "description: " + "x".repeat(3 * 1024 * 1024) + "\n");

        assertThat(BundleDocument.load(small)).isEqualTo(yaml("openapi: 3.1.0"));
        assertThatThrownBy(() -> BundleDocument.load(large)).isInstanceOf(YamlEngineException.class);
    }

    private static Object yaml(String text) {
        return new Load(LoadSettings.builder().build()).loadFromString(text);
    }
}
