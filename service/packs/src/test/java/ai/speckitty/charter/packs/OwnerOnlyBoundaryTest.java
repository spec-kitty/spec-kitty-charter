package ai.speckitty.charter.packs;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.OwnerOnly;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * OWNER_ONLY_MODES_ARE_BUILT_IN_ONE_PLACE (#510): no class of the packs module but {@link OwnerOnly} builds a POSIX
 * permission attribute. Five call sites that each built their own failed on Windows, where the file system has no
 * POSIX permissions; the helper falls back to an ACL, and a sixth call site that skips it would fail the same way.
 *
 * <p>The known-bad control follows {@code NetworkCodeBoundaryTest}: a test-only class in a {@code badfixture} package,
 * against which the unchanged production rule must fail.
 */
class OwnerOnlyBoundaryTest {

    private static final String ROOT = "ai.speckitty.charter.packs";

    static final ArchRule OWNER_ONLY_MODES_ARE_BUILT_IN_ONE_PLACE = noClasses()
            .that(not(equivalentTo(OwnerOnly.class)))
            .should()
            .callMethod(PosixFilePermissions.class, "asFileAttribute", Set.class)
            .because("a POSIX mode fails on a file system without POSIX permissions; OwnerOnly falls back to an ACL"
                    + " (#510)");

    @Test
    void onlyTheOwnerOnlyHelperBuildsAPosixModeAttribute() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        assertThatCode(() -> OWNER_ONLY_MODES_ARE_BUILT_IN_ONE_PLACE.check(production))
                .doesNotThrowAnyException();
    }

    @Test
    void theRuleFailsOnAClassThatBuildsItsOwnPosixMode() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(ROOT + ".infra.archive.badfixture");

        assertThatThrownBy(() -> OWNER_ONLY_MODES_ARE_BUILT_IN_ONE_PLACE.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ArchiveBuildsPosixMode")
                .hasMessageContaining("PosixFilePermissions.asFileAttribute");
    }
}
