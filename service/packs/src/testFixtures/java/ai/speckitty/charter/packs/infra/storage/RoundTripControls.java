package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The whole-pack round trip's positive controls, for the gate in {@code pack-validation} (ADR-0075 decision 8, as amended
 * by operator ruling 2 of 2026-11-15): the production flow ({@link PackRoundTrip}) with one loss planted on its write
 * path, so the verdict can be shown to see it end to end. Only the adapter's package-private constructors take the loss;
 * the public entry ({@link PackRoundTrip#run(PackIdentifier, Path, Path)}) never does. Every control is configured under the id its caller names.
 */
public final class RoundTripControls {

    private RoundTripControls() {}

    /**
     * The flow, with the loaded pack changed before it is saved: a loss travels the production write path, never edited
     * into a written file (#193, #260).
     *
     * @param packId the registered id the pack is configured under
     * @param source the pack directory
     * @param target the directory to write to
     * @param change maps the loaded pack to the pack that is saved
     * @return what the round trip observed
     */
    public static PackRoundTripResult beforeSave(
            PackIdentifier packId, Path source, Path target, UnaryOperator<Pack> change) {
        return PackRoundTrip.run(new DomainRoundTrip(new YamlSerializationStrategy(), change), packId, source, target);
    }

    /**
     * The flow, with the source's mutability policy changed before it is written (#476).
     *
     * @param packId the registered id the pack is configured under
     * @param source the pack directory
     * @param target the directory to write to
     * @param change maps the source's policy to the policy that is written
     * @return what the round trip observed
     */
    public static PackRoundTripResult beforePolicySave(
            PackIdentifier packId, Path source, Path target, UnaryOperator<MutabilityPolicySource> change) {
        return PackRoundTrip.run(
                new DomainRoundTrip(new YamlSerializationStrategy(), UnaryOperator.identity(), change),
                packId,
                source,
                target);
    }

    /**
     * The flow, with every text the format writes for one artefact kind changed after the writer produced it.
     *
     * @param packId the registered id the pack is configured under
     * @param source the pack directory
     * @param target the directory to write to
     * @param kind the artefact kind whose written texts change
     * @param change maps a written text to the text that lands on disk
     * @return what the round trip observed
     */
    public static PackRoundTripResult rewritingWrites(
            PackIdentifier packId, Path source, Path target, ArtifactKind kind, UnaryOperator<String> change) {
        SerializationStrategy yaml = new YamlSerializationStrategy();
        SerializationStrategy lossy = (SerializationStrategy) Proxy.newProxyInstance(
                SerializationStrategy.class.getClassLoader(),
                new Class<?>[] {SerializationStrategy.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(yaml, args);
                    } catch (InvocationTargetException thrown) {
                        throw thrown.getCause();
                    }
                    boolean kindWrite = method.getName().equals("write")
                            && args[0] instanceof KindBinding<?, ?> binding
                            && binding.kind() == kind;
                    return kindWrite ? change.apply((String) result) : result;
                });
        return PackRoundTrip.run(new DomainRoundTrip(lossy), packId, source, target);
    }

    /**
     * An adapter that writes nothing and answers every census file as written in place at its own path, so a test lays
     * out both sides by hand and the flow only reads them: in the content form the bundled table gives each path, or as
     * Markdown for every file when {@code markdown} is set.
     *
     * @param packId the registered id the pack is configured under, which also picks its files' content forms
     * @param source the source directory
     * @param written the directory that already holds the written side
     * @param census the files to answer, with their gate kinds
     * @param markdown whether every file is read as Markdown
     * @return what the round trip observed
     */
    public static PackRoundTripResult inPlace(
            PackIdentifier packId, Path source, Path written, SortedMap<String, String> census, boolean markdown) {
        Set<String> kinds = Set.copyOf(census.values());
        WholePackRoundTrip inPlace = new WholePackRoundTrip() {
            @Override
            public Set<String> modelledKinds() {
                return kinds;
            }

            @Override
            public Map<String, Outcome> roundTrip(String name, Path from, Path to, Map<String, String> kindOf) {
                return kindOf.keySet().stream().collect(Collectors.toMap(path -> path, Written::new));
            }
        };
        return PackRoundTrip.run(
                inPlace,
                packId,
                source,
                written,
                new TreeMap<>(census),
                path -> markdown ? ContentForm.MARKDOWN : WholePackCensus.contentFormOf(packId.value(), path));
    }
}
