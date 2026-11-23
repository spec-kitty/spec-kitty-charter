/**
 * The report file of {@code pack-doctor validate --report} (FR-008 of mission pack-doctor-coverage-01M7YMQK, plan D6,
 * rulings R-i and R-n): the writers of the one report tree as YAML ({@link
 * ai.speckitty.charter.packdoctor.report.YamlReport}, the default) and as XML ({@link
 * ai.speckitty.charter.packdoctor.report.XmlReport}, with its reader, the inverse of its mapping). The tree itself is
 * built by the CLI's report writer, the one model standard output carries as JSON; this package only serializes it,
 * with the YAML engine the service already uses and the JDK's XML (plan D9: no new library).
 */
@NullMarked
package ai.speckitty.charter.packdoctor.report;

import org.jspecify.annotations.NullMarked;
