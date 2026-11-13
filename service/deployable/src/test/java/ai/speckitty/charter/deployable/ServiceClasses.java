package ai.speckitty.charter.deployable;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The service's production classes, imported once for every architecture test of this module. An import of the whole
 * {@code ai.speckitty.charter} package is the largest object a test run holds, and a test class's static field keeps
 * its copy for the rest of the run, so the tests share this one instead of each importing its own.
 */
final class ServiceClasses {

    private ServiceClasses() {}

    /**
     * The production classes under {@code ai.speckitty.charter}, tests excluded, imported on the first call.
     *
     * @return the shared import
     */
    static JavaClasses main() {
        return Holder.MAIN;
    }

    /** Lazy holder: the import runs when a test first asks for it. */
    private static final class Holder {
        private static final JavaClasses MAIN = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ai.speckitty.charter");

        private Holder() {}
    }
}
