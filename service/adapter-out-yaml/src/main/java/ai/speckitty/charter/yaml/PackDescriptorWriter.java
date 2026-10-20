package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.PackDescriptor;

/**
 * Serializes a {@link PackDescriptor} to {@code pack.yaml} text from scratch, through {@link YamlDumpEmitter}.
 *
 * <p>{@code active_charter} and {@code pack_drg} are emitted when present: a from-scratch writer that left them out
 * would delete them from disk. {@link PackDescriptor#undeclaredKeys()} holds key names without values, so it cannot be
 * written back; it is not emitted and is outside the round-trip equality contract.
 */
final class PackDescriptorWriter {

    private PackDescriptorWriter() {}

    static String write(PackDescriptor descriptor) {
        return new YamlDumpEmitter()
                .scalar("pack_id", descriptor.packId())
                .scalar("pack_version", descriptor.packVersion())
                .scalar("name", descriptor.name())
                .scalar("parent_pack", descriptor.parentPack())
                .scalar("pack_level", descriptor.declaredLevel().map(level -> level.token()))
                .scalar("requires_core", descriptor.requiresCore())
                .scalar("active_charter", descriptor.activeCharter())
                .scalar("pack_drg", descriptor.packDrg())
                .dump();
    }
}
