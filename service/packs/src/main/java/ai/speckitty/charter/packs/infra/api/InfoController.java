package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.ServiceApi;
import ai.speckitty.charter.api.model.ServiceInfo;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /info} and {@code GET /openapi.yaml}, the bundled contract the service was built from. */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class InfoController implements ServiceApi {

    /** RFC 9512's media type, without a {@code charset}: it defines none. */
    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    private final ServiceInfoQuery query;
    private final ContractDocument contract;

    /**
     * A controller over the service info and the contract document.
     *
     * @param query the read use case it answers with
     * @param contract the bundled contract, loaded when the service started
     */
    public InfoController(ServiceInfoQuery query, ContractDocument contract) {
        this.query = query;
        this.contract = contract;
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
     * The bundled contract, or {@code 304} when the client holds it. The guard has already refused a browser request
     * or a missing token, so {@code If-None-Match} is read only here. The {@code 200} carries no {@code ETag} on the
     * entity: {@link ContractDocumentETag} adds it when the body is written, after Spring's own conditional check,
     * which would otherwise answer {@code 304} to values the contract answers {@code 200}. The {@code 304} carries
     * it here; Spring checks only a {@code 200}. Neither carries {@code Charter-Source-Fingerprint}: no pack file is
     * read.
     *
     * @param ifNoneMatch the entity tags the client holds (several header lines arrive joined by commas), if any
     * @return {@code 304} with the ETag, or {@code 200} with the document as {@code application/yaml}
     */
    @Override
    public ResponseEntity<String> getOpenApiDocument(@Nullable String ifNoneMatch) {
        if (contract.matchedBy(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(contract.etag())
                    .build();
        }
        // The content type is set, so the converter adds no charset; Boot's string converter writes UTF-8.
        return ResponseEntity.ok().contentType(APPLICATION_YAML).body(contract.text());
    }
}
