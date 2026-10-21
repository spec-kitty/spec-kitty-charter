package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ServiceApi;
import ai.speckitty.charter.api.model.ServiceInfo;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /info}. */
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
}
