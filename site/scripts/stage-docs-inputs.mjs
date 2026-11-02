// Stages the build inputs the docsite reads from outside docs/ (run as `predev` and `prebuild`).
//   1. contracts/packs/schemas/*.json -> site/schemas/        (read by the `kind: Schematic` pages)
//   2. build/contracts/contract/charter-openapi.yaml -> site/openapi/   (read by the `openapi` option)
//   3. every schema has a page and every page names a schema that exists (fails closed otherwise).
// Both target directories are ignored by git: the schemas stay the single source of truth.
// Node 22, plain ESM, no dependencies. Run from site/ (npm scripts do).
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const schemasSrc = '../contracts/packs/schemas';
const bundleSrc = '../build/contracts/contract/charter-openapi.yaml';
const pagesDir = '../docs/architecture/pack-schemas';
const problems = [];

// 1. The pack schemas.
rmSync('schemas', { recursive: true, force: true });
mkdirSync('schemas', { recursive: true });
const schemas = readdirSync(schemasSrc).filter((f) => f.endsWith('.json')).sort();
for (const f of schemas) cpSync(join(schemasSrc, f), join('schemas', f));

// 2. The contract bundle.
rmSync('openapi', { recursive: true, force: true });
if (existsSync(bundleSrc)) {
  mkdirSync('openapi', { recursive: true });
  cpSync(bundleSrc, 'openapi/charter-openapi.yaml');
} else {
  problems.push('contract bundle missing: run ./gradlew :contracts:bundleContract (CI: the contract-bundle artifact)');
}

// 3. Every schema has a page; every page's schematic_path names a staged file.
const named = new Set();
for (const f of readdirSync(pagesDir).filter((p) => p.endsWith('.md') && p !== 'README.md')) {
  const front = /^---\r?\n([\s\S]*?)\r?\n---/.exec(readFileSync(join(pagesDir, f), 'utf8'))?.[1] ?? '';
  const path = /^schematic_path:\s*["']?([^"'\r\n]+?)["']?\s*$/m.exec(front)?.[1];
  if (!path) continue;
  named.add(path);
  if (!existsSync(path)) problems.push(`page ${f}: schematic_path "${path}" names a file that does not exist`);
}
const unpaged = schemas.filter((f) => !named.has(`schemas/${f}`));
if (unpaged.length) problems.push(`schemas with no page in docs/architecture/pack-schemas/: ${unpaged.join(', ')}`);

if (problems.length) {
  for (const p of problems) console.error(`stage-docs-inputs: ${p}`);
  process.exit(1);
}
console.log(`stage-docs-inputs: ${schemas.length} schemas, ${named.size} pages, contract bundle staged`);
