package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.ContextApi;
import ai.speckitty.charter.api.model.ActionContext;
import ai.speckitty.charter.api.model.GuidanceEntry;
import ai.speckitty.charter.read.ActionContextQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Override
    public ResponseEntity<ActionContext> getActionContext(String missionType, String action) {
        ai.speckitty.charter.read.ActionContext context = query.answer(missionType, action);
        return ResponseEntity.ok(new ActionContext(
                context.action().toString(),
                context.missionType().toString(),
                context.guidance().stream().map(ContextController::toDto).toList()));
    }

    private static GuidanceEntry toDto(ai.speckitty.charter.read.GuidanceEntry entry) {
        return new GuidanceEntry(
                entry.urn().toString(),
                GuidanceEntry.KindEnum.fromValue(entry.kind().token()),
                Absent.asNull(entry.label()),
                GuidanceEntry.RelationEnum.fromValue(entry.relation().token()),
                entry.via().toString());
    }
}
