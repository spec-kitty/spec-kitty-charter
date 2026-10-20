package ai.speckitty.charter.yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The register's two forms: {@code docs/conformance/divergences.yaml} and {@code divergence-register.md}. */
final class DivergenceRegister {

    private static final Pattern PAGE_ID = Pattern.compile("^(?:### |\\| )(DIV-\\d{3})\\b", Pattern.MULTILINE);

    private DivergenceRegister() {}

    static Path yaml(Path repository) {
        return repository.resolve("docs/conformance/divergences.yaml");
    }

    static Path page(Path repository) {
        return repository.resolve("docs/conformance/divergence-register.md");
    }

    /** The entries of the yaml by id, in file order. */
    static Map<String, Map<?, ?>> entries(String yamlText) {
        Map<String, Map<?, ?>> entries = new LinkedHashMap<>();
        for (Object entry : (List<?>) Yaml.load(yamlText)) {
            Map<?, ?> row = (Map<?, ?>) entry;
            String id = String.valueOf(row.get("id"));
            if (entries.put(id, row) != null) {
                throw new IllegalStateException("duplicate id " + id + " in divergences.yaml");
            }
        }
        return entries;
    }

    static Map<String, Map<?, ?>> entries(Path repository) throws IOException {
        return entries(Files.readString(yaml(repository), StandardCharsets.UTF_8));
    }

    /** The ids the page presents: an entry heading ({@code ### DIV-001: ...}) or a table row ({@code | DIV-101 |}). */
    static List<String> pageIds(String pageText) {
        Matcher matcher = PAGE_ID.matcher(pageText);
        List<String> ids = new java.util.ArrayList<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }
}
