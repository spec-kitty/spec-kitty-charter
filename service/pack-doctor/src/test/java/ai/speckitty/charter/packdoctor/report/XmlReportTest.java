package ai.speckitty.charter.packdoctor.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link XmlReport}: the XML report is a mechanical mapping of the report's tree (FR-008, plan D6, ruling R-n: our own
 * shape, not JUnit's), and {@link XmlReport#read} is its inverse, so a tree written and read back is the same tree.
 */
class XmlReportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode tree(String json) {
        return JSON.readTree(json);
    }

    /** Written, then read back: the same tree, in key order and scalar type. */
    private static void assertRoundTrips(JsonNode tree) {
        JsonNode back = XmlReport.read(XmlReport.write(tree));

        assertThat(back).isEqualTo(tree);
        assertThat(JSON.writeValueAsString(back)).isEqualTo(JSON.writeValueAsString(tree));
    }

    @Nested
    class TheMapping {

        @Test
        void anElementPerKeyAnElementPerItemNamedForTheListAndATypeOnlyForWhatIsNoString() {
            JsonNode tree = tree("""
                    {"tool": {"name": "pack-doctor"},
                     "checks": ["PACK", "CLOSURE"],
                     "packs": [{"name": "a", "path": "x/a"}],
                     "skipped": [{"check": "CLOSURE"}],
                     "problems": [{"status": 422, "violations": [{"code": "c"}]}],
                     "nothing": [],
                     "empty": {},
                     "options": {"full": false, "round_trip": true}}""");

            assertThat(XmlReport.write(tree)).isEqualTo("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <pack-doctor-report>
                      <tool>
                        <name>pack-doctor</name>
                      </tool>
                      <checks type="array">
                        <check>PACK</check>
                        <check>CLOSURE</check>
                      </checks>
                      <packs type="array">
                        <pack>
                          <name>a</name>
                          <path>x/a</path>
                        </pack>
                      </packs>
                      <skipped type="array">
                        <skip>
                          <check>CLOSURE</check>
                        </skip>
                      </skipped>
                      <problems type="array">
                        <problem>
                          <status type="integer">422</status>
                          <violations type="array">
                            <violation>
                              <code>c</code>
                            </violation>
                          </violations>
                        </problem>
                      </problems>
                      <nothing type="array"/>
                      <empty type="object"/>
                      <options>
                        <full type="boolean">false</full>
                        <round_trip type="boolean">true</round_trip>
                      </options>
                    </pack-doctor-report>
                    """);
            assertRoundTrips(tree);
        }

        @Test
        void anItemOfAListWhoseKeyHasNoPluralIsAnItem() {
            assertThat(XmlReport.singular("data")).isEqualTo("item");
            assertThat(XmlReport.singular("skipped")).isEqualTo("skip");
            assertThat(XmlReport.singular("packs")).isEqualTo("pack");
            assertRoundTrips(tree("{\"data\": [\"a\", 1, true, [\"b\"]]}"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "<&>\"'",
                    "]]>",
                    "a line\nand another",
                    "a carriage return\r\nthen a line feed, and a lone \r",
                    "\ttabbed",
                    "  leading and trailing  ",
                    " ",
                    "",
                    "é ✓ 😀",
                    "422",
                    "true"
                })
        void aStringIsTheElementsTextAndReadsBackAsItWas(String text) {
            ObjectNode tree = JSON.createObjectNode();
            tree.put("detail", text);
            tree.putArray("checks").add(text);

            assertRoundTrips(tree);
        }

        /**
         * Ruling R-ac: a string holding a character XML 1.0 cannot carry is written escaped, {@code type="escaped"},
         * each such character as a backslash, {@code u} and its four upper-case hex digits, and each backslash as two,
         * and reads back as it was.
         */
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "\u0000",
                    "a\u0001b",
                    "\u001f",
                    "\ufffe",
                    "\uffff",
                    "\ud800",
                    "x\udc00",
                    "a \\u0001 written out \u0001 and a backslash \\",
                    "\u0001<&>\r\n\t😀"
                })
        void aCharacterXmlCannotCarryIsEscapedAndReadsBackAsItWas(String text) {
            ObjectNode tree = JSON.createObjectNode();
            tree.putArray("packs").addObject().put("path", text);

            assertRoundTrips(tree);
        }

        @Test
        void theEscapeIsTheSchemasWordsAndOnlyAStringThatNeedsItIsEscaped() {
            ObjectNode tree = JSON.createObjectNode();
            tree.put("detail", "agent_profiles/x\u0001y.agent.yaml, read as C:\\packs");
            tree.put("path", "C:\\packs\\org");

            assertThat(XmlReport.write(tree))
                    .contains(
                            "<detail type=\"escaped\">agent_profiles/x\\u0001y.agent.yaml, read as C:\\\\packs</detail>")
                    .contains("<path>C:\\packs\\org</path>");
        }

        @Test
        void anIntegerBeyondAnIntReadsBackAsALong() {
            assertRoundTrips(tree("{\"n\": 4294967296, \"m\": -1, \"z\": 0}"));
        }
    }

    @Nested
    class WhatTheWriterRefuses {

        @Test
        void aValueNoReportHolds() {
            assertThatThrownBy(() -> XmlReport.write(tree("{\"x\": 1.5}")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("/x");
            assertThatThrownBy(() -> XmlReport.write(tree("{\"x\": null}")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("/x");
        }

        @Test
        void aKeyThatIsNoElementName() {
            assertThatThrownBy(() -> XmlReport.write(tree("{\"a b\": \"x\"}")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a b");
            assertThatThrownBy(() -> XmlReport.write(tree("{\"1a\": \"x\"}")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> XmlReport.write(tree("{\"\": \"x\"}")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aTreeThatIsNoObject() {
            assertThatThrownBy(() -> XmlReport.write(tree("[]"))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class WhatTheReaderRefuses {

        private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

        @ParameterizedTest
        @ValueSource(
                strings = {
                    // A document type, with an entity: never expanded, never fetched.
                    "<!DOCTYPE pack-doctor-report [<!ENTITY x \"y\">]><pack-doctor-report><a>&x;</a></pack-doctor-report>",
                    "<!DOCTYPE pack-doctor-report SYSTEM \"file:///etc/passwd\"><pack-doctor-report/>",
                    // Another root.
                    "<report><a>x</a></report>",
                    // A root that is no object.
                    "<pack-doctor-report type=\"array\"/>",
                    // An unknown type, an unknown attribute, an attribute in a namespace.
                    "<pack-doctor-report><a type=\"number\">1</a></pack-doctor-report>",
                    "<pack-doctor-report><a kind=\"x\">1</a></pack-doctor-report>",
                    "<pack-doctor-report xmlns:q=\"urn:q\"><a q:type=\"integer\">1</a></pack-doctor-report>",
                    // An element in a namespace.
                    "<pack-doctor-report xmlns=\"urn:q\"><a>1</a></pack-doctor-report>",
                    // Text beside the keys of an object.
                    "<pack-doctor-report>text<a>x</a></pack-doctor-report>",
                    // A key twice.
                    "<pack-doctor-report><a>x</a><a>y</a></pack-doctor-report>",
                    // An item not named for its list.
                    "<pack-doctor-report><packs type=\"array\"><problem>x</problem></packs></pack-doctor-report>",
                    // Text in a list.
                    "<pack-doctor-report><packs type=\"array\">x<pack>y</pack></packs></pack-doctor-report>",
                    // A scalar with children, or an object marked empty that is not.
                    "<pack-doctor-report><a type=\"integer\"><b>1</b></a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"object\"><b>1</b></a></pack-doctor-report>",
                    // An integer or a boolean that is none.
                    "<pack-doctor-report><a type=\"integer\">one</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"integer\"> 1</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"boolean\">yes</a></pack-doctor-report>",
                    // An escaped string that holds an escape the mapping never writes, or none it needs.
                    "<pack-doctor-report><a type=\"escaped\">\\u001</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"escaped\">\\u00zz</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"escaped\">\\n</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"escaped\">trailing \\</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"escaped\">\\u0041</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"escaped\">nothing to escape</a></pack-doctor-report>",
                    "<pack-doctor-report><a type=\"escaped\"><b>\\u0001</b></a></pack-doctor-report>",
                    // Not XML, or not all of it.
                    "<pack-doctor-report><a>x</a>",
                    "<pack-doctor-report/><pack-doctor-report/>",
                    "",
                })
        void aDocumentTheMappingDoesNotWrite(String body) {
            String xml = body.startsWith("<!DOCTYPE") || body.isEmpty() ? body : HEAD + body;

            assertThatThrownBy(() -> XmlReport.read(xml)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void commentsAndWhitespaceBetweenElementsAreNoValue() {
            JsonNode back = XmlReport.read(HEAD + "\n<!-- a comment -->\n<pack-doctor-report>\n  <a>x</a>\n"
                    + "  <!-- another -->\n  <b type=\"array\">\n  </b>\n</pack-doctor-report>\n");

            assertThat(back).isEqualTo(tree("{\"a\": \"x\", \"b\": []}"));
        }
    }
}
