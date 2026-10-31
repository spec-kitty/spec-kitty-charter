package ai.speckitty.charter.deployable.bdd;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The relation keys of the table under {@code ## Relation names} in {@code docs/development/hypermedia-hateoas.md}, the
 * one source of the relation keys (R-03 revised). The build copies the guide onto the test classpath as
 * {@value #RESOURCE}; a key is a backticked name of the table's first column, and a row may name two
 * ({@code `select`, `unselect`}).
 */
public final class RelationTable {

    /** The guide's name on the test classpath. */
    public static final String RESOURCE = "guide/hypermedia-hateoas.md";

    private static final Pattern NAME = Pattern.compile("`([^`]+)`");

    private RelationTable() {}

    /**
     * The relation keys of the guide on the test classpath.
     *
     * @return the keys, in table order
     * @throws AssertionError when the guide is not on the classpath
     */
    public static Set<String> keys() {
        try (InputStream in = RelationTable.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new AssertionError("the hypermedia guide is not on the test classpath as " + RESOURCE);
            }
            return keys(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The relation keys of a guide's text.
     *
     * @param guide the Markdown of the guide
     * @return the backticked names of the first column of the table under {@code ## Relation names}, in order
     */
    static Set<String> keys(String guide) {
        List<String> rows = new ArrayList<>();
        boolean inSection = false;
        for (String line : guide.lines().toList()) {
            if (line.startsWith("## ")) {
                inSection = line.equals("## Relation names");
            } else if (inSection && line.startsWith("|") && !line.startsWith("|---")) {
                rows.add(line.split("\\|", -1)[1]);
            }
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String firstColumn : rows.subList(Math.min(1, rows.size()), rows.size())) {
            Matcher name = NAME.matcher(firstColumn);
            while (name.find()) {
                keys.add(name.group(1));
            }
        }
        return keys;
    }
}
