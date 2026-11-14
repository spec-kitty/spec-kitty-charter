package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.VocabularyApi;
import ai.speckitty.charter.api.model.TabooList;
import ai.speckitty.charter.api.model.VocabularyTermList;
import ai.speckitty.charter.artifacts.application.VocabularyReads;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The vocabulary reads of an activation (#106, tag {@code Vocabulary}; ADR-0076 decisions 4 to 9):
 * {@code GET /activations/{activationId}/vocabulary/terms}, {@code …/contextive} and {@code …/taboos}. Each answers as
 * the other reads under an activation answer ({@link EffectiveAssetsController#answer}): a {@code 304} with the tag
 * alone, or a {@code 200} with the tag, the source fingerprint of the read and the body. A refusal the reads throw,
 * judged before {@code If-None-Match}, is answered by the shared refusal handler.
 *
 * <p>The Contextive document is answered as {@code application/yaml} text ({@link ContextiveYaml}), as the contract
 * document is. A request whose {@code Accept} excludes it never reaches this controller: Spring's mapping answers
 * {@code 406}, as for any operation.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class VocabularyController implements VocabularyApi {

    /** RFC 9512's media type, without a {@code charset}: it defines none. */
    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    private final VocabularyReads vocabulary;
    private final VocabularyBodies bodies;

    /**
     * A controller over the vocabulary reads of an activation.
     *
     * @param vocabulary the reads
     * @param bodies the wire mapper of the JSON answers and their links
     */
    @Autowired
    public VocabularyController(VocabularyReads vocabulary, VocabularyBodies bodies) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<VocabularyTermList> listActivationVocabularyTerms(
            String activationId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                vocabulary.terms(activationId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::terms);
    }

    @Override
    public ResponseEntity<String> getActivationVocabularyContextive(String activationId, @Nullable String ifNoneMatch) {
        ResponseEntity<String> answer = EffectiveAssetsController.answer(
                vocabulary.contextive(activationId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                read -> ContextiveYaml.render(read.document()));
        if (!answer.getStatusCode().is2xxSuccessful()) {
            return answer;
        }
        // The content type is set, so the converter adds no charset; Boot's string converter writes UTF-8.
        return ResponseEntity.ok()
                .headers(answer.getHeaders())
                .contentType(APPLICATION_YAML)
                .body(answer.getBody());
    }

    @Override
    public ResponseEntity<TabooList> listActivationVocabularyTaboos(String activationId, @Nullable String ifNoneMatch) {
        return EffectiveAssetsController.answer(
                vocabulary.taboos(activationId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::taboos);
    }
}
