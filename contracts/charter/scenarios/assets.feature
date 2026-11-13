# The asset reads of a pack (#105, ADR-0074 decisions 2, 9 to 11 and 13; FR-003 to FR-005, NFR-003): GET
# /packs/{packId}/artifacts/asset lists a pack's assets in id order, GET …/asset/{artifactId} reads one asset's
# descriptor, and GET …/asset/{artifactId}/payload answers its bytes with the asset's own media type. The 400, 401, 403
# and 500 answers of the three operations are asserted in read-refusals.feature, every-operation.feature and
# unexpected-failure.feature.

@contract @artifacts
Feature: A pack's assets are listed, read and their payloads served byte for byte
  A client lists a pack's assets, reads one to learn how it runs, and follows its payload link to fetch its bytes. The
  payload is the file the asset names, unchanged, served with the asset's mime as its Content-Type when the mime is a
  well-formed type/subtype and as application/octet-stream when it is not, always with X-Content-Type-Options: nosniff
  and Content-Security-Policy: sandbox. It ignores Accept. Every read answers the pack revision as its ETag, and the
  payload answers 304 to a matching If-None-Match. The pack is judged first: an unknown pack is 404 pack_not_found,
  then an asset the pack does not hold is 404 artifact_not_found.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    And the org pack "qa" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      title: Run the lint
      executable:
        runtime: python
        min_version: '3.11'
        entry: run.py
        args:
        - --guard
      """
    And the org pack "qa" with the file "assets/run.py":
      """
      import sys
      print("lint", sys.argv[1:])
      """
    And the org pack "qa" with the file "assets/logo.png.asset.yaml":
      """
      id: logo
      mime: image/png
      path: logo.png
      """
    And the org pack "qa" with the file "assets/logo.png" in base64:
      """
      iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==
      """
    And the org pack "qa" with the file "assets/loose.txt":
      """
      A payload file no sidecar names: it is not an asset.
      """

  Scenario: The list answers each asset's id, mime and whether it runs, in id order
    When I GET "/packs/qa/artifacts/asset"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {
            "id": "logo",
            "mime": "image/png",
            "executable": false,
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/asset/logo"}}
          },
          {
            "id": "run",
            "mime": "text/x-python",
            "executable": true,
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/asset/run"}}
          }
        ],
        "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/asset"}}
      }
      """
    And following the link "self" of every element of "items" answers 200

  Scenario: An executable asset is read in the Artifact envelope with its descriptor, its payload link and its list
    When I GET "/packs/qa/artifacts/asset/run"
    Then the response status is 200
    And the response body is:
      """
      {
        "kind": "asset",
        "id": "run",
        "packId": "qa",
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/asset/run"},
          "payload": {"href": "/api/v1/packs/qa/artifacts/asset/run/payload"},
          "collection": {"href": "/api/v1/packs/qa/artifacts/asset"}
        },
        "asset": {
          "id": "run",
          "mime": "text/x-python",
          "path": "run.py",
          "title": "Run the lint",
          "executable": {"runtime": "python", "minVersion": "3.11", "entry": "run.py", "args": ["--guard"]}
        }
      }
      """

  Scenario: An asset that declares no title and does not run answers neither
    When I GET "/packs/qa/artifacts/asset/logo"
    Then the response status is 200
    And the field at "/asset" is present
    And the field at "/asset/title" is absent
    And the field at "/asset/executable" is absent

  Scenario: The payload of a script is its file's bytes with its mime, and the ETag of its asset
    When I GET "/packs/qa/artifacts/asset/run"
    And I remember the header "ETag"
    And I follow the link "payload"
    Then the response status is 200
    And the response header "Content-Type" is "text/x-python"
    And the response header "X-Content-Type-Options" is "nosniff"
    And the response header "Content-Security-Policy" is "sandbox"
    And the response header "ETag" is the remembered header
    And the response body is the bytes of the file "assets/run.py" of the pack "qa"

  Scenario: The payload of a binary asset is its file byte for byte
    When I GET "/packs/qa/artifacts/asset/logo/payload"
    Then the response status is 200
    And the response header "Content-Type" is "image/png"
    And the response header "X-Content-Type-Options" is "nosniff"
    And the response header "Content-Security-Policy" is "sandbox"
    And the response body is the bytes of the file "assets/logo.png" of the pack "qa"

  Scenario: The payload ignores Accept: a request for JSON is answered the same bytes and type
    When I GET "/packs/qa/artifacts/asset/logo/payload" accepting "application/json"
    Then the response status is 200
    And the response header "Content-Type" is "image/png"
    And the response body is the bytes of the file "assets/logo.png" of the pack "qa"

  Scenario: A payload whose If-None-Match matches its ETag, or is *, answers 304 without a body
    When I GET "/packs/qa/artifacts/asset/logo/payload"
    And I remember the header "ETag"
    And I GET "/packs/qa/artifacts/asset/logo/payload" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    When I GET "/packs/qa/artifacts/asset/logo/payload" with the header "If-None-Match": "*"
    Then the response status is 304
    And the response header "ETag" is the remembered header

  Scenario: A payload whose mime is not a well-formed type/subtype is served as application/octet-stream
    Given the org pack "odd" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: odd-pack
      pack_level: org
      """
    And the org pack "odd" with the file "assets/notes.txt.asset.yaml":
      """
      id: notes
      mime: plain
      path: notes.txt
      """
    And the org pack "odd" with the file "assets/notes.txt":
      """
      Notes whose sidecar writes no type/subtype.
      """
    When I GET "/packs/odd/artifacts/asset/notes"
    Then the response status is 200
    And the field at "/asset/mime" is "plain"
    When I GET "/packs/odd/artifacts/asset/notes/payload"
    Then the response status is 200
    And the response header "Content-Type" is "application/octet-stream"
    And the response header "X-Content-Type-Options" is "nosniff"
    And the response header "Content-Security-Policy" is "sandbox"
    And the response body is the bytes of the file "assets/notes.txt" of the pack "odd"

  Scenario Outline: <operation> of a pack no configured pack answers to is 404 pack_not_found
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "pack_not_found"

    Examples:
      | operation           | path                                       |
      | listPackAssets      | /packs/nope/artifacts/asset                |
      | getPackAsset        | /packs/nope/artifacts/asset/logo           |
      | getPackAssetPayload | /packs/nope/artifacts/asset/logo/payload   |

  Scenario Outline: <operation> of an asset the pack does not hold is 404 artifact_not_found, a loose payload file included
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "artifact_not_found"

    Examples:
      | operation           | path                                       |
      | getPackAsset        | /packs/qa/artifacts/asset/nothing          |
      | getPackAsset        | /packs/qa/artifacts/asset/loose            |
      | getPackAssetPayload | /packs/qa/artifacts/asset/nothing/payload  |
      | getPackAssetPayload | /packs/qa/artifacts/asset/loose/payload    |
