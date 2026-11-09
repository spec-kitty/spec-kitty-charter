import { agentIndexRoute } from '@spec-kitty/doc-toolkit/routes';

// Advertise the generic stdio MCP client command; no per-site server is deployed.
export const GET = agentIndexRoute({ title: 'spec-kitty-charter', links: { mcp: true } });
