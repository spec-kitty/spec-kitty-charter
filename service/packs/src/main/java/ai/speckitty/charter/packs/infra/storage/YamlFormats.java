package ai.speckitty.charter.packs.infra.storage;

import java.util.List;

/**
 * The YAML form of every stored kind: one {@link YamlFormat} per {@link KindBinding} in {@link KindBindings}. A kind
 * joins by adding its format here.
 */
final class YamlFormats {

    private static final List<YamlFormat<?>> ALL = List.of(
            TacticYaml.INSTANCE,
            DirectiveYaml.INSTANCE,
            StyleguideYaml.INSTANCE,
            ToolguideYaml.INSTANCE,
            ParadigmYaml.INSTANCE,
            AgentProfileYaml.INSTANCE,
            MissionStepContractYaml.INSTANCE,
            GlossaryPackYaml.INSTANCE,
            SkillYaml.INSTANCE);

    private YamlFormats() {}

    /**
     * Every format.
     *
     * @return an immutable list
     */
    static List<YamlFormat<?>> all() {
        return ALL;
    }

    /**
     * The format of a bound kind.
     *
     * @param binding the kind's binding
     * @param <D> the kind's persistence shape
     * @return the format, typed by the binding's shape
     * @throws IllegalArgumentException when the kind has no format, or its format reads another shape
     */
    @SuppressWarnings("unchecked") // the DSO type is checked against the binding's before the cast
    static <D> YamlFormat<D> of(KindBinding<D, ?> binding) {
        for (YamlFormat<?> format : ALL) {
            if (format.kind() == binding.kind()) {
                if (format.dsoType() != binding.dsoType()) {
                    throw new IllegalArgumentException(
                            "the YAML format of " + binding.kind().token()
                                    + " reads " + format.dsoType().getSimpleName() + ", not "
                                    + binding.dsoType().getSimpleName());
                }
                return (YamlFormat<D>) format;
            }
        }
        throw new IllegalArgumentException(
                "no YAML format for " + binding.kind().token());
    }
}
