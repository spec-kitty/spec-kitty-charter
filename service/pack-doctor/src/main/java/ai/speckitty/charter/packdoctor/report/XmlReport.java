package ai.speckitty.charter.packdoctor.report;

import java.io.StringReader;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The XML report (FR-008, plan D6, ruling R-n): a mechanical mapping of the report's tree, its own shape, not JUnit's or
 * xUnit's, written with no library but the JDK's (plan D9) and read back by {@link #read}, its inverse, so the contract
 * test validates the XML against the one JSON Schema, {@code PackDoctorReport}, and no XSD is kept by hand.
 *
 * <p>The mapping, which the schema's description states too:
 *
 * <ul>
 *   <li>the root element is {@value #ROOT}, in no namespace, after an XML declaration (version 1.0, UTF-8);
 *   <li>an object is an element with one child element per key, named after the key, in the tree's order; an object
 *       with no key is an empty element with {@code type="object"};
 *   <li>an array is an element with {@code type="array"} and one child element per item, named after the singular of
 *       the array's key ({@link #singular}: {@code checks/check}, {@code packs/pack}, {@code problems/problem},
 *       {@code violations/violation}, {@code skipped/skip});
 *   <li>a string is an element whose text is the string; an integer is an element with {@code type="integer"} and a
 *       boolean one with {@code type="boolean"}, whose text is the value;
 *   <li>a string holding a character XML 1.0 cannot carry (a control character other than tab, line feed and carriage
 *       return, U+FFFE, U+FFFF, or a surrogate that is not half of a pair) is an element with {@code type="escaped"}
 *       (ruling R-ac): in its text each such character is a backslash, {@code u} and the four upper-case hex digits of
 *       its UTF-16 code unit, and each backslash is two backslashes; every other character is as in a plain string. A
 *       string with no such character is never escaped, so a path's backslashes stay as they are;
 *   <li>no other attribute is written, and no value is written as an attribute; {@code &}, {@code <} and {@code >} are
 *       escaped, and a carriage return is written as the character reference to 13, so a parser's line-end handling
 *       keeps it;
 *   <li>each element is on its own line, indented by two spaces a level.
 * </ul>
 *
 * <p>So every string the report holds can be written, and pack content (a file name, a detail) never makes the XML
 * fail; {@link #read} undoes the escape.
 */
public final class XmlReport {

    /** The root element. */
    static final String ROOT = "pack-doctor-report";

    private static final String TYPE = "type";
    private static final String ARRAY = "array";
    private static final String OBJECT = "object";
    private static final String INTEGER = "integer";
    private static final String BOOLEAN = "boolean";
    private static final String ESCAPED = "escaped";

    /** The keys the writer names an element after: ASCII names, which need no namespace and no escaping. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    /** The digits of an escape: four upper-case hex digits, as {@link #escaped} writes them. */
    private static final Pattern HEX = Pattern.compile("[0-9A-F]{4}");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private XmlReport() {}

    /**
     * The tree as XML, ending with a line feed.
     *
     * @param tree the report's tree, an object
     * @return the XML
     * @throws IllegalArgumentException for a tree that is no object, a key that is no element name, or a value that is
     *     no object, array, string, integer or boolean, naming where it is
     */
    public static String write(JsonNode tree) {
        if (!tree.isObject()) {
            throw new IllegalArgumentException("the report is an object, not " + tree.getNodeType());
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        element(xml, ROOT, tree, "", 0);
        return xml.toString();
    }

    /**
     * The singular of an array's key, which names its items: {@code skipped} is {@code skip}, a key ending in {@code s}
     * drops it, and any other key's items are {@code item}.
     *
     * @param key the array's key, or the item name of the array that holds it
     * @return the name of its items
     */
    static String singular(String key) {
        if ("skipped".equals(key)) {
            return "skip";
        }
        if (key.length() > 1 && key.endsWith("s")) {
            return key.substring(0, key.length() - 1);
        }
        return "item";
    }

    private static void element(StringBuilder xml, String name, JsonNode node, String pointer, int depth) {
        xml.append("  ".repeat(depth)).append('<').append(name);
        switch (node.getNodeType()) {
            case OBJECT -> {
                if (node.isEmpty()) {
                    xml.append(" type=\"object\"/>\n");
                    return;
                }
                xml.append(">\n");
                for (Map.Entry<String, JsonNode> member : node.properties()) {
                    String key = member.getKey();
                    String at = pointer + "/" + key;
                    if (!NAME.matcher(key).matches()) {
                        throw new IllegalArgumentException(
                                "the key '" + key + "' at " + at + " is no XML element name the report writes");
                    }
                    element(xml, key, member.getValue(), at, depth + 1);
                }
                xml.append("  ".repeat(depth)).append("</").append(name).append(">\n");
            }
            case ARRAY -> {
                xml.append(" type=\"array\"");
                if (node.isEmpty()) {
                    xml.append("/>\n");
                    return;
                }
                xml.append(">\n");
                String item = singular(name);
                for (int index = 0; index < node.size(); index++) {
                    element(xml, item, node.get(index), pointer + "/" + index, depth + 1);
                }
                xml.append("  ".repeat(depth)).append("</").append(name).append(">\n");
            }
            case STRING -> string(xml, name, node.asString());
            case NUMBER -> {
                if (!node.isInt() && !node.isLong()) {
                    throw new IllegalArgumentException(
                            pointer + " holds " + node + ", which is no integer the report holds");
                }
                xml.append(" type=\"integer\">")
                        .append(node.longValue())
                        .append("</")
                        .append(name)
                        .append(">\n");
            }
            case BOOLEAN ->
                xml.append(" type=\"boolean\">")
                        .append(node.booleanValue())
                        .append("</")
                        .append(name)
                        .append(">\n");
            default ->
                throw new IllegalArgumentException(
                        pointer + " holds a " + node.getNodeType() + ", which the report never holds");
        }
    }

    /** A string's element: its text, or its escaped text with {@code type="escaped"} when XML 1.0 cannot carry it. */
    private static void string(StringBuilder xml, String name, String value) {
        boolean escape = needsEscape(value);
        xml.append(escape ? " type=\"escaped\">" : ">");
        text(xml, escape ? escaped(value) : value);
        xml.append("</").append(name).append(">\n");
    }

    /** The text of a string, its markup characters escaped; every character in it is one XML 1.0 carries. */
    private static void text(StringBuilder xml, String value) {
        value.codePoints().forEach(point -> {
            switch (point) {
                case '&' -> xml.append("&amp;");
                case '<' -> xml.append("&lt;");
                case '>' -> xml.append("&gt;");
                case '\r' -> xml.append("&#13;");
                default -> xml.appendCodePoint(point);
            }
        });
    }

    /** Whether a string holds a character XML 1.0 cannot carry, so it is written escaped. */
    private static boolean needsEscape(String value) {
        return value.codePoints().anyMatch(point -> !isXmlCharacter(point));
    }

    /**
     * The escaped form of a string (ruling R-ac): each backslash doubled, and each character XML 1.0 cannot carry as a
     * backslash, {@code u} and the four upper-case hex digits of its code unit. Such a character is never supplementary,
     * so one code unit is the whole of it.
     */
    private static String escaped(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        value.codePoints().forEach(point -> {
            if (point == '\\') {
                escaped.append("\\\\");
            } else if (isXmlCharacter(point)) {
                escaped.appendCodePoint(point);
            } else {
                escaped.append(String.format(Locale.ROOT, "\\u%04X", point));
            }
        });
        return escaped.toString();
    }

    /**
     * The string an escaped text stands for: the inverse of {@link #escaped}. A backslash followed by anything but a
     * backslash or {@code u} and four hex digits naming a character XML 1.0 cannot carry, or an escaped text with no
     * such character, is never written, so it is refused.
     */
    private static String unescaped(XMLStreamReader reader, String text, String pointer) {
        StringBuilder value = new StringBuilder(text.length());
        boolean needed = false;
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c != '\\') {
                value.append(c);
                index++;
                continue;
            }
            if (text.startsWith("\\\\", index)) {
                value.append('\\');
                index += 2;
                continue;
            }
            int point = index + 6 <= text.length() && text.charAt(index + 1) == 'u'
                    ? hex(text.substring(index + 2, index + 6))
                    : -1;
            if (point < 0 || isXmlCharacter(point)) {
                throw refused(reader, at(pointer) + " is marked escaped but holds an escape the mapping never writes");
            }
            value.append((char) point);
            needed = true;
            index += 6;
        }
        if (!needed) {
            throw refused(reader, at(pointer) + " is marked escaped but holds no character that needs it");
        }
        return value.toString();
    }

    /** Four upper-case hex digits, as the writer writes them, as a number; -1 for anything else. */
    private static int hex(String digits) {
        return HEX.matcher(digits).matches() ? Integer.parseInt(digits, 16) : -1;
    }

    /** XML 1.0's {@code Char} production. */
    private static boolean isXmlCharacter(int point) {
        return point == '\t'
                || point == '\n'
                || point == '\r'
                || (point >= 0x20 && point <= 0xD7FF)
                || (point >= 0xE000 && point <= 0xFFFD)
                || (point >= 0x10000 && point <= 0x10FFFF);
    }

    /**
     * The tree an XML report holds: the inverse of {@link #write}, an escaped string unescaped. A document type, an
     * entity, a namespace, an attribute other than {@code type}, an unknown type, text beside child elements, a key
     * given twice, an item not named after its array, or an escape the mapping never writes is refused, so only what
     * the mapping writes reads back. Comments and the whitespace between elements are
     * no value.
     *
     * @param xml the XML
     * @return the tree, an object
     * @throws IllegalArgumentException when the text is not XML the mapping writes
     */
    public static JsonNode read(String xml) {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                return document(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException failure) {
            throw new IllegalArgumentException("not the XML of a pack-doctor report: " + failure.getMessage(), failure);
        }
    }

    private static JsonNode document(XMLStreamReader reader) throws XMLStreamException {
        JsonNode tree = null;
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    if (tree != null) {
                        throw refused(reader, "a second root element");
                    }
                    if (!ROOT.equals(reader.getLocalName())) {
                        throw refused(reader, "the root element is " + reader.getLocalName() + ", not " + ROOT);
                    }
                    tree = value(reader, ROOT, "");
                    if (!tree.isObject()) {
                        throw refused(reader, "the root element holds no object");
                    }
                }
                case XMLStreamConstants.DTD -> throw refused(reader, "a document type");
                case XMLStreamConstants.ENTITY_REFERENCE -> throw refused(reader, "an entity");
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE -> {
                    if (!reader.isWhiteSpace()) {
                        throw refused(reader, "text outside the root element");
                    }
                }
                default -> {
                    // A comment, a processing instruction or the end of the document: no value.
                }
            }
        }
        if (tree == null) {
            throw new IllegalArgumentException("not the XML of a pack-doctor report: no root element");
        }
        return tree;
    }

    /** The value of the element the reader is at the start of; the reader is left at its end. */
    private static JsonNode value(XMLStreamReader reader, String name, String pointer) throws XMLStreamException {
        String namespace = reader.getNamespaceURI();
        if (namespace != null && !namespace.isEmpty()) {
            throw refused(reader, "the element " + name + " is in the namespace " + namespace);
        }
        @Nullable String type = null;
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String attributeNamespace = reader.getAttributeNamespace(index);
            if (!TYPE.equals(reader.getAttributeLocalName(index))
                    || (attributeNamespace != null && !attributeNamespace.isEmpty())) {
                throw refused(reader, "the attribute " + reader.getAttributeName(index) + " at " + at(pointer));
            }
            type = reader.getAttributeValue(index);
        }
        StringBuilder text = new StringBuilder();
        ObjectNode members = NODES.objectNode();
        ArrayNode items = NODES.arrayNode();
        boolean children = false;
        String item = singular(name);
        while (true) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    children = true;
                    String child = reader.getLocalName();
                    if (ARRAY.equals(type)) {
                        if (!item.equals(child)) {
                            throw refused(reader, "the item " + child + " of " + at(pointer) + ", not " + item);
                        }
                        items.add(value(reader, child, pointer + "/" + items.size()));
                    } else {
                        if (members.has(child)) {
                            throw refused(reader, "the key " + child + " twice at " + at(pointer));
                        }
                        members.set(child, value(reader, child, pointer + "/" + child));
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE, XMLStreamConstants.CDATA ->
                    text.append(reader.getText());
                case XMLStreamConstants.ENTITY_REFERENCE -> throw refused(reader, "an entity at " + at(pointer));
                case XMLStreamConstants.END_ELEMENT -> {
                    return scalarOrContainer(reader, type, pointer, text.toString(), children, members, items);
                }
                default -> {
                    // A comment or a processing instruction: no value.
                }
            }
        }
    }

    private static JsonNode scalarOrContainer(
            XMLStreamReader reader,
            @Nullable String type,
            String pointer,
            String text,
            boolean children,
            ObjectNode members,
            ArrayNode items) {
        if (type == null) {
            if (!children) {
                return NODES.stringNode(text);
            }
            requireBlank(reader, text, pointer);
            return members;
        }
        return switch (type) {
            case ARRAY -> {
                requireBlank(reader, text, pointer);
                yield items;
            }
            case OBJECT -> {
                requireBlank(reader, text, pointer);
                if (children) {
                    throw refused(reader, at(pointer) + " is marked an empty object but holds keys");
                }
                yield members;
            }
            case INTEGER -> {
                requireLeaf(reader, children, pointer);
                long number;
                try {
                    number = Long.parseLong(text);
                } catch (NumberFormatException notANumber) {
                    throw refused(reader, at(pointer) + " is marked an integer but holds '" + text + "'");
                }
                yield number == (int) number ? NODES.numberNode((int) number) : NODES.numberNode(number);
            }
            case ESCAPED -> {
                requireLeaf(reader, children, pointer);
                yield NODES.stringNode(unescaped(reader, text, pointer));
            }
            case BOOLEAN -> {
                requireLeaf(reader, children, pointer);
                if (!"true".equals(text) && !"false".equals(text)) {
                    throw refused(reader, at(pointer) + " is marked a boolean but holds '" + text + "'");
                }
                yield NODES.booleanNode(Boolean.parseBoolean(text));
            }
            default -> throw refused(reader, "the type " + type + " at " + at(pointer));
        };
    }

    private static void requireBlank(XMLStreamReader reader, String text, String pointer) {
        if (!text.chars().allMatch(c -> c == ' ' || c == '\t' || c == '\n' || c == '\r')) {
            throw refused(reader, "text beside the elements of " + at(pointer));
        }
    }

    private static void requireLeaf(XMLStreamReader reader, boolean children, String pointer) {
        if (children) {
            throw refused(reader, at(pointer) + " is marked a scalar but holds elements");
        }
    }

    private static String at(String pointer) {
        return pointer.isEmpty() ? "/" : pointer;
    }

    private static IllegalArgumentException refused(XMLStreamReader reader, String what) {
        return new IllegalArgumentException("not the XML of a pack-doctor report: " + what + " (line "
                + reader.getLocation().getLineNumber() + ")");
    }
}
