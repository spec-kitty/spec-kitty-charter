package ai.speckitty.charter.packs.infra.storage;

import com.google.re2j.Pattern;
import com.networknt.schema.regex.RegularExpression;
import com.networknt.schema.regex.RegularExpressionFactory;

/**
 * Runs the {@code pattern}, {@code patternProperties} and {@code format: regex} expressions of an embedded schema on
 * re2j, which matches in time linear in the input: a pack author cannot write a pattern that backtracks without end
 * (ReDoS). A pattern re2j cannot compile (a backreference, a lookaround) throws, so the schema is refused; there is no
 * fallback to the JDK engine.
 */
final class Re2jRegularExpressionFactory implements RegularExpressionFactory {

    static final Re2jRegularExpressionFactory INSTANCE = new Re2jRegularExpressionFactory();

    private Re2jRegularExpressionFactory() {}

    @Override
    public RegularExpression getRegularExpression(String regex) {
        Pattern pattern = Pattern.compile(regex);
        return value -> pattern.matcher(value).find();
    }
}
