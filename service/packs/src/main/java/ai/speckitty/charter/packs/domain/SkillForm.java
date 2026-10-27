package ai.speckitty.charter.packs.domain;

import java.util.Arrays;

/** The two forms of a {@link Skill}: a prompt with a Markdown body, or a wrapper that expands to a command. */
public enum SkillForm {
    /** A prompt skill: its body is the prompt. */
    PROMPT("prompt"),
    /** A wrapper skill: it expands to a built-in or {@code spec-kitty} command. */
    WRAPPER("wrapper");

    private final String token;

    SkillForm(String token) {
        this.token = token;
    }

    /**
     * The form as the {@code form} field spells it.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * The form a token names.
     *
     * @param token the {@code form} field's text
     * @return the form
     * @throws IllegalArgumentException when the token names no form
     */
    public static SkillForm fromToken(String token) {
        return Arrays.stream(values())
                .filter(form -> form.token.equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown skill form '" + token + "'"));
    }
}
