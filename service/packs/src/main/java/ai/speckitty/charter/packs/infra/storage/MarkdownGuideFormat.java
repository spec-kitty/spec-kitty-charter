package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.GuideFormat;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import org.springframework.stereotype.Component;

/**
 * A guide's Markdown by the rules the stored guide is read and written by (FR-010/011): the {@link MarkdownCodec} line
 * rules and canonical form, and {@link GuideMarkdown}'s structure rules. An incoming guide may leave its H1 out
 * (FR-011); a stored one may not ({@link GuideMarkdown#fromStored}).
 */
@Component
public final class MarkdownGuideFormat implements GuideFormat {

    /** For Spring, which finds the adapter by component scan; a test constructs it directly. */
    public MarkdownGuideFormat() {}

    @Override
    public Guide read(String title, String markdown) {
        return switch (MarkdownCodec.parse(markdown)) {
            case MarkdownCodec.Parsed parsed -> GuideMarkdown.fromDocument(title, parsed.document());
            case MarkdownRefusal refusal ->
                throw new IllegalArgumentException("the guide is not readable Markdown at " + refusal.message());
        };
    }

    @Override
    public String write(String title, Guide guide) {
        return GuideMarkdown.write(title, guide);
    }
}
