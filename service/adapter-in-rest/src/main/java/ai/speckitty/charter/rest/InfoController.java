package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.ServiceApi;
import ai.speckitty.charter.api.model.ServiceInfo;
import ai.speckitty.charter.read.ServiceInfoQuery;
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
        ai.speckitty.charter.read.ServiceInfo info = query.answer();
        return ResponseEntity.ok(new ServiceInfo(ContractVersion.value(), info.worktree(), info.sourceFingerprint()));
    }
}
