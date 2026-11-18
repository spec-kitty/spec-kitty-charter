package ai.speckitty.charter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The wire form of an exact decimal: plain notation, trailing zeros stripped, never scientific (rule 7). */
class DecimalJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new DecimalJson().decimalJsonModule())
            .build();

    @Test
    void aDecimalIsStrippedOfItsTrailingZerosAndNeverGetsANegativeScale() {
        assertThat(DecimalJson.wire(new BigDecimal("0.300")).toPlainString()).isEqualTo("0.3");
        assertThat(DecimalJson.wire(new BigDecimal("1.00")).toPlainString()).isEqualTo("1");
        assertThat(DecimalJson.wire(new BigDecimal("1E+1")).scale()).isZero();
        assertThat(DecimalJson.wire(new BigDecimal("0.12345678901234567890123")).toPlainString())
                .isEqualTo("0.12345678901234567890123");
    }

    @Test
    void aSmallOrALargeDecimalIsWrittenInPlainNotation() {
        BigDecimal small = DecimalJson.wire(new BigDecimal("0.001").multiply(new BigDecimal("0.0001")));
        assertThat(small.toString())
                .as("the default form this serializer replaces")
                .isEqualTo("1E-7");

        assertThat(mapper.writeValueAsString(Map.of("v", small))).isEqualTo("{\"v\":0.0000001}");
        assertThat(mapper.writeValueAsString(Map.of("v", DecimalJson.wire(new BigDecimal("1E+3")))))
                .isEqualTo("{\"v\":1000}");
        assertThat(mapper.writeValueAsString(Map.of("v", new BigDecimal("0.732"))))
                .isEqualTo("{\"v\":0.732}");
    }
}
