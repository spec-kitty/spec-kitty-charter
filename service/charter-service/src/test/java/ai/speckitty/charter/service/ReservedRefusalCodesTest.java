package ai.speckitty.charter.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import ai.speckitty.charter.domain.RefusalCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces that {@code governance_policy_violation} is reserved: no operation of this version raises it. The contract
 * scenario "governance_policy_violation is reserved" pins its declaration; this test pins the other half, that
 * nothing in the main code names the constant except the enum that declares it and the title mapping of
 * {@code RefusalHandler}.
 *
 * <p>The exclusion is by name pattern, not by simple name: javac compiles the {@code switch} over the enum in
 * {@code RefusalHandler.title()} into a synthetic class {@code RefusalHandler$1} (a {@code $SwitchMap} table) that
 * reads the field, and the enum's own constants initializer is part of the enum class.
 *
 * <p>Issue #48 raises the code, and deletes this test when it does.
 */
class ReservedRefusalCodesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ai.speckitty.charter");

    private static ArchRule nothingRaisesGovernancePolicyViolation() {
        return noClasses()
                .that()
                .haveNameNotMatching("ai\\.speckitty\\.charter\\.domain\\.RefusalCode(\\$.*)?")
                .and()
                .haveNameNotMatching("ai\\.speckitty\\.charter\\.rest\\.RefusalHandler(\\$.*)?")
                .should()
                .accessField(RefusalCode.class, "GOVERNANCE_POLICY_VIOLATION");
    }

    @Test
    void noMainClassButTheEnumAndTheTitleMappingNamesGovernancePolicyViolation() {
        nothingRaisesGovernancePolicyViolation().check(MAIN);
    }
}
