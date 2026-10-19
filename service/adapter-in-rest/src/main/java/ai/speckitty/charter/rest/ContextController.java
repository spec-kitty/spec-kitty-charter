package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.ContextApi;
import ai.speckitty.charter.api.model.ActionContext;
import ai.speckitty.charter.api.model.Diagnostic;
import ai.speckitty.charter.api.model.GuidanceEntry;
import ai.speckitty.charter.read.ActionContextQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** {@code GET /mission-types/{missionType}/actions/{action}/context}. */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class ContextController implements ContextApi {

    private final ActionContextQuery query;

    /**
     * A controller over action-scoped context.
     *
     * @param query the read use case it answers with
     */
    public ContextController(ActionContextQuery query) {
        this.query = query;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A depth outside {@link ActionContextQuery#MIN_DEPTH} to {@link ActionContextQuery#MAX_DEPTH} is a 400
     * problem, not a charter refusal: the request is wrong,
     * not the offering.
     */
    @Override
    public ResponseEntity<ActionContext> getActionContext(String missionType, String action, Integer depth) {
        int requested = depth == null ? ActionContextQuery.DEFAULT_DEPTH : depth;
        if (requested < ActionContextQuery.MIN_DEPTH || requested > ActionContextQuery.MAX_DEPTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        ai.speckitty.charter.read.ActionContext context = query.answer(missionType, action, requested);
        return ResponseEntity.ok(new ActionContext(
                context.action().toString(),
                context.missionType().toString(),
                context.depth(),
                context.governanceFingerprint(),
                context.guidance().stream().map(ContextController::toDto).toList(),
                context.diagnostics().stream()
                        .map(d -> new Diagnostic(
                                Diagnostic.CodeEnum.fromValue(d.code().token()), d.detail()))
                        .toList()));
    }

    private static GuidanceEntry toDto(ai.speckitty.charter.read.GuidanceEntry entry) {
        return new GuidanceEntry(
                entry.urn().toString(),
                GuidanceEntry.KindEnum.fromValue(entry.kind().token()),
                Absent.asNull(entry.label()),
                GuidanceEntry.RelationEnum.fromValue(entry.relation().token()),
                Absent.asNull(entry.impacts()),
                entry.via().toString(),
                entry.hops());
    }
}
