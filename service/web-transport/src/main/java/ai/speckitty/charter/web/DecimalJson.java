package ai.speckitty.charter.web;

import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * The wire form of an exact decimal (data-model rule 7 of ADR-0080): a JSON number in plain notation, unrounded, with
 * trailing zeros stripped, so {@code 0.30} answers {@code 0.3}, {@code 1.0} answers {@code 1} and {@code 1E-7} answers
 * {@code 0.0000001}. {@link BigDecimal#toString()} writes a small or a large value in scientific notation, which a JSON
 * number allows but the contract does not, so every {@link BigDecimal} the service writes goes through
 * {@link #wire(BigDecimal)} and is written by {@link BigDecimal#toPlainString()}. The routing catalog's scores, weights
 * and prices are the decimals the service answers today.
 */
@Configuration(proxyBeanMethods = false)
public class DecimalJson {

    /** For Spring, which finds the configuration by component scan. */
    public DecimalJson() {}

    /**
     * The exact decimal a body carries: its trailing zeros stripped, and never a negative scale.
     *
     * @param decimal the value, as the domain holds it
     * @return the same value, with the least scale that keeps it exact and at least 0
     */
    public static BigDecimal wire(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that writes every {@link BigDecimal} in plain notation
     */
    @Bean
    public JacksonModule decimalJsonModule() {
        return new SimpleModule("charter-decimal-json").addSerializer(BigDecimal.class, new PlainDecimal());
    }

    /** Writes a decimal as a JSON number in plain notation, never in scientific notation. */
    static final class PlainDecimal extends StdSerializer<BigDecimal> {

        PlainDecimal() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
            generator.writeNumber(value.toPlainString());
        }
    }
}
