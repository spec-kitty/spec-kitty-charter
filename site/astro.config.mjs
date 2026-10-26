// @ts-check
import { defineConfig } from 'astro/config';
import { defineDocKittyIntegrations } from '@spec-kitty/doc-toolkit/config';
import { specKittyTheme } from '@spec-kitty/doc-toolkit/themes/spec-kitty/index.ts';

// The docs tree lives at the repository root (../docs); this site/ project only
// renders it. GitHub Pages serves a project site under /<repo>, so BASE is that
// path. SITE and BASE can be overridden for a local preview at the domain root.
const SITE = process.env.DK_SITE ?? 'https://spec-kitty.github.io';
const BASE = process.env.DK_BASE ?? '/spec-kitty-charter';

export default defineConfig({
  site: SITE,
  base: BASE,
  integrations: defineDocKittyIntegrations({
    title: 'spec-kitty-charter',
    description: 'The Spec Kitty charter: a Java service and its OpenAPI contract, the Brain Kitty governance producer.',
    // MUST equal the site `base` above.
    base: BASE,
    // MUST equal the loader `base` in src/content.config.ts. Without it the toolkit
    // looks for site/docs, ignores docs/_meta/sections.yaml and lists drafts in the sitemap.
    docsDir: '../docs',
    // Brain Kitty brand mark (olive bow tie) over the Spec Kitty theme.
    theme: { extends: specKittyTheme, assets: { logo: './src/assets/brain-kitty.png' } },
    markua: true,
    // Mermaid fences render in the browser (doc-kitty's client renderer); no PlantUML
    // or Chromium is needed at build time. PlantUML diagrams are committed as SVGs.
    diagrams: true,
    // Render ```gherkin fences as structured scenarios and warn on imperative steps
    // (silence a step with @dk:allow=imperative). Fences only: no generated feature
    // pages — the contracts/charter/scenarios/*.feature pipeline is a separate choice.
    gherkin: { fences: true, lint: { imperativeSteps: true } },
    // Label a section's index entry that repeats the group label (since doc-kitty 0.4.0).
    sidebar: { indexLabel: 'Overview' },
    // Starlight escape hatch: show a clickable anchor on every heading (since 0.4.0).
    starlight: { markdown: { headingLinks: true } },
    // MUST match the loader in src/content.config.ts.
    indexBasename: ['README'],
  }),
});
