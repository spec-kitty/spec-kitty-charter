package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigValue raw tree")
class ConfigValueTest {

    @Test
    @DisplayName("a nested tree survives of -> toParsed -> of with equality and raw types intact")
    void nestedTreeRoundTripsWithoutCoercion() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("flag", true);
        inner.put("nothing", null);
        inner.put("ratio", 0.5d);
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("limit", 42);
        parsed.put("huge", new BigInteger("123456789012345678901234567890"));
        parsed.put("name", "docs");
        parsed.put("list", new ArrayList<>(Arrays.asList(1, "two", null, List.of(3.5d, false))));
        parsed.put("nested", inner);

        ConfigValue first = ConfigValue.of(parsed);
        Object back = first.toParsed();
        ConfigValue second = ConfigValue.of(back);

        assertThat(second).isEqualTo(first);
        Map<?, ?> map = (Map<?, ?>) back;
        assertThat(map.get("limit")).isInstanceOf(Integer.class).isEqualTo(42);
        assertThat(map.get("huge")).isInstanceOf(BigInteger.class);
        assertThat(map.get("name")).isEqualTo("docs");
        List<?> list = (List<?>) map.get("list");
        assertThat(list.get(0)).isInstanceOf(Integer.class);
        assertThat(list.get(2)).isNull();
        assertThat(((List<?>) list.get(3)).get(0)).isInstanceOf(Double.class);
        Map<?, ?> nested = (Map<?, ?>) map.get("nested");
        assertThat(nested.get("flag")).isInstanceOf(Boolean.class).isEqualTo(true);
        assertThat(nested.get("ratio")).isInstanceOf(Double.class);
        assertThat(nested.containsKey("nothing")).isTrue();
        assertThat(nested.get("nothing")).isNull();
        assertThat(new ArrayList<Object>(map.keySet())).containsExactly("limit", "huge", "name", "list", "nested");
    }

    @Test
    @DisplayName("an int and the same digits as text are different values")
    void scalarTypeIsPartOfEquality() {
        assertThat(ConfigValue.of(1)).isNotEqualTo(ConfigValue.of("1"));
        assertThat(ConfigValue.of(1)).isNotEqualTo(ConfigValue.of(1.0d));
    }
}
