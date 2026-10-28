package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ServiceApi;
import ai.speckitty.charter.api.model.ServiceInfo;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /info}; {@code GET /openapi.yaml} answers 501 until #209 serves it. */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class InfoController implements ServiceApi {

    private final ServiceInfoQuery query;

    /**
     * A controller over the service info.
     *
     * @param query the read use case it answers with
     */
    public InfoController(ServiceInfoQuery query) {
        this.query = query;
    }

    @Override
    public ResponseEntity<ServiceInfo> getServiceInfo() {
        ai.speckitty.charter.packs.application.ServiceInfo info = query.answer();
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(info.sourceFingerprint()))
                .body(new ServiceInfo(
                        ContractVersion.value(), ServiceVersion.value(), info.worktree(), info.sourceFingerprint()));
    }

    /**
     * Not served yet: the contract declares the operation (#208) before the service packages the bundle it serves
     * (#209). Answers the closed {@code Problem} of 501, which the operation's {@code default} response admits.
     *
     * @param ifNoneMatch the entity tags the client holds, if it sent any; unread
     * @return never
     * @throws ErrorResponseException always, with 501
     */
    @Override
    public ResponseEntity<String> getOpenApiDocument(@Nullable String ifNoneMatch) {
        throw new ErrorResponseException(HttpStatus.NOT_IMPLEMENTED);
    }
}
