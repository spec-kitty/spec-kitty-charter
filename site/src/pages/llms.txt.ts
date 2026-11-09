import { llmsTxtRoute } from '@spec-kitty/doc-toolkit/routes';

export const GET = llmsTxtRoute({
  title: 'spec-kitty-charter',
  description: 'The Spec Kitty charter: a Java service and its OpenAPI contract, the Brain Kitty governance producer.',
  // Name the same generic MCP client command in the human-readable index.
  links: { mcp: true },
});
