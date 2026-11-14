# Quickstart: R12 glossary contexts and terms

How to see the Mission working once it is built. Run these from the repository root.

1. Build and run the gates (foreground, one run at a time):

   ```bash
   ./gradlew verifyAll --max-workers=1
   ```

2. Run the service against charter's example packs, as `docs/guides/service-build.md` describes. Then, with
   `BASE` the service URL:

   ```bash
   curl -s "$BASE/packs/built-in/artifacts/glossary"                                   # contexts, with term counts
   curl -s "$BASE/packs/built-in/artifacts?kind=glossary"                              # the same contexts
   curl -s "$BASE/packs/built-in/artifacts/glossary/spec-kitty-core"                   # one context, every term
   curl -s "$BASE/packs/built-in/artifacts/glossary/spec-kitty-core/terms/mission"     # one term
   curl -s "$BASE/packs/built-in/artifacts/glossary/spec-kitty-core/terms/ghost"       # 404 artifact_not_found, names the term
   ```

3. Create an activation over the example packs, then:

   ```bash
   curl -s "$BASE/activations/$ID/artifacts/glossary/spec-kitty-core"                  # the winning copy, with state
   curl -s "$BASE/activations/$ID/vocabulary/terms"                                    # every term in force
   curl -s -H 'Accept: application/yaml' "$BASE/activations/$ID/vocabulary/contextive" # a Contextive v1 file
   curl -s "$BASE/activations/$ID/vocabulary/taboos"                                   # avoided words, with their terms
   ```

4. Check that the old shape is refused: register a pack that holds `glossary_packs/x.glossary-pack.yaml`. It is
   refused as invalid content, and the refusal names the file.
