package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.packs.domain.Guide;

/**
 * The port between a toolguide's {@link Guide} and the Markdown text it travels as (#194, FR-010/011): an incoming
 * inline {@code guide} or fetched {@code guideUri} is read through it, and a toolguide's guide is answered through it.
 * The adapter lives in {@code packs.infra.storage}, beside the Markdown model the stored guide is written in, so
 * incoming, answered and stored guides obey the same rules; the application ring and the inbound adapter name only
 * this interface.
 */
public interface GuideFormat {

    /**
     * Reads the guide the text holds.
     *
     * @param title the toolguide's title, which an H1 in the text, when present, must equal; an absent H1 is accepted
     * @param markdown the guide's Markdown
     * @return the guide
     * @throws IllegalArgumentException naming the first rule the text breaks
     */
    Guide read(String title, String markdown);

    /**
     * The Markdown a guide is answered and stored as, its H1 written from the toolguide's title.
     *
     * @param title the toolguide's title
     * @param guide the guide
     * @return the guide's Markdown
     * @throws IllegalArgumentException when the title cannot be an H1 (blank, or more than one line)
     */
    String write(String title, Guide guide);
}
