---
title: "Product lines"
description: "Glossary terms of the Product lines context, imported from the core glossary. The product lines of Spec Kitty Inc.: Build Kitty, Brain Kitty and Team Kitty."
doc_status: active
updated: 2026-10-14
type: Glossary
kind: Glossary
glossary_context: "Product lines"
audience:
  - profile: charter-service-contributor
    guidance_text: "Read this for the vocabulary the Java domain must use unchanged."
  - profile: pack-author
    guidance_text: "Read this for the pack and activation terms you write against."
source_ref: 9b9bf5c8ff
---

# Product lines

The product lines of Spec Kitty Inc.: Build Kitty, Brain Kitty and Team Kitty.

{#brain-kitty}

## Brain Kitty

The open-core governance and doctrine line: the charter, a producer-side open-core unit that is being extracted into its own repository so open-core consumers can depend on it without the whole CLI.

{#build-kitty}

## Build Kitty

The open-core producer line for governed mission delivery: the free CLI and its Mission Status Read API, the stable entry point to ledger and event data that consumers read across a public contract boundary.

{#team-kitty}

## Team Kitty

The consumer line, framed as "Multiplayer Mode": the UI and multiplayer surfaces (mission-ui, the pack composer) that consume the open-core Mission Status Read API across its public contract, never reaching into CLI internals. The hosted collaboration product behind it is no longer actively supported; its model is in Team Kitty and Zeitgeist.
