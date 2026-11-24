package ai.speckitty.charter.packdoctor.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import tools.jackson.databind.JsonNode;

/**
 * The YAML report (FR-008, plan D6, ruling R-i: the default of {@code --report}): the report's tree in block style, in
 * its key order, each list item indented under its key, a line per scalar however long, written by the YAML engine the
 * service already uses (SnakeYAML Engine, plan D9). The engine quotes a string a YAML 1.2 loader would read as another
 * type ({@code '1.0'}, {@code 'true'}, {@code ''}), so the file loads back as the same tree.
 */
public final class YamlReport {

    private static final DumpSettings SETTINGS = DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .setIndicatorIndent(2)
            .setIndentWithIndicator(true)
            .setSplitLines(false)
            .build();

    private YamlReport() {}

    /**
     * The tree as YAML, ending with a line feed.
     *
     * @param tree the report's tree, an object
     * @return the YAML
     * @throws IllegalArgumentException for a tree that is no object, or a value that is no object, array, string,
     *     integer or boolean, naming where it is
     */
    public static String write(JsonNode tree) {
        if (!tree.isObject()) {
            throw new IllegalArgumentException("the report is an object, not " + tree.getNodeType());
        }
        return new Dump(SETTINGS).dumpToString(plain(tree, ""));
    }

    /** The node as the engine's values: maps in key order, lists, strings, integers and booleans. */
    private static Object plain(JsonNode node, String pointer) {
        return switch (node.getNodeType()) {
            case OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> member : node.properties()) {
                    map.put(member.getKey(), plain(member.getValue(), pointer + "/" + member.getKey()));
                }
                yield map;
            }
            case ARRAY -> {
                List<Object> list = new ArrayList<>();
                for (int index = 0; index < node.size(); index++) {
                    list.add(plain(node.get(index), pointer + "/" + index));
                }
                yield list;
            }
            case STRING -> node.asString();
            case BOOLEAN -> node.booleanValue();
            case NUMBER -> {
                if (node.isInt()) {
                    yield node.intValue();
                }
                if (node.isLong()) {
                    yield node.longValue();
                }
                throw new IllegalArgumentException(
                        pointer + " holds " + node + ", which is no integer the report holds");
            }
            default ->
                throw new IllegalArgumentException(
                        pointer + " holds a " + node.getNodeType() + ", which the report never holds");
        };
    }
}
