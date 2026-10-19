package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.domain.RefusalCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain refusals to the contract's {@code CharterRefusal} problem bodies. */
@RestControllerAdvice
public class RefusalHandler {

    @ExceptionHandler(ai.speckitty.charter.domain.CharterRefusal.class)
    public ResponseEntity<CharterRefusal> refused(ai.speckitty.charter.domain.CharterRefusal refusal) {
        HttpStatus status = refusal.code().notFound() ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
        CharterRefusal body = new CharterRefusal(title(refusal.code()), status.value(),
                CharterRefusal.CodeEnum.fromValue(refusal.code().token()));
        body.setDetail(refusal.getMessage());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static String title(RefusalCode code) {
        return switch (code) {
            case MISSION_TYPE_NOT_FOUND -> "Mission type not found";
            case ACTION_NOT_FOUND -> "Action not found";
            case PACK_LEVEL_MISMATCH -> "Pack level mismatch";
            case PACK_UNREADABLE -> "Pack unreadable";
            case DRG_SCHEMA_ERROR -> "Invalid DRG document";
            case UNRESOLVED_EDGE_ENDPOINT -> "Unresolved edge endpoint";
        };
    }
}
