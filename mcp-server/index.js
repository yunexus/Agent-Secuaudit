#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const BACKEND_URL = process.env.SECUAUDIT_URL || "http://localhost:8080";
const DEFAULT_USER_ID = process.env.SECUAUDIT_USER_ID || "claude_code";

async function callBackend(endpoint, body) {
  const url = BACKEND_URL + endpoint;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Backend ${res.status}: ${text}`);
  }
  return res.json();
}

const server = new Server(
  {
    name: "secuaudit-mcp-server",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// List available tools
server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "read_file",
      description: "Read a file through SecuAudit. The system audits data integrity and metadata compliance before returning file contents. Requires the file to be previously uploaded and signed via the SecuAudit Web Dashboard.",
      inputSchema: {
        type: "object",
        properties: {
          file_id: {
            type: "string",
            description: "The file ID (filename) to read, e.g. 'data/report.txt'",
          },
          user_id: {
            type: "string",
            description: "The user ID making the request (default: 'claude_code')",
            default: "claude_code",
          },
          challenge_blocks: {
            type: "integer",
            description: "Number of blocks to challenge in the audit (default: 30)",
            default: 30,
          },
        },
        required: ["file_id"],
      },
    },
    {
      name: "query_db",
      description: "Execute a database query through SecuAudit. The system performs integrity audit before returning query results.",
      inputSchema: {
        type: "object",
        properties: {
          query: {
            type: "string",
            description: "SQL query to execute against audited database",
          },
          user_id: {
            type: "string",
            description: "The user ID making the request",
            default: "claude_code",
          },
        },
        required: ["query"],
      },
    },
    {
      name: "audit_status",
      description: "Get the current SecuAudit system status including total calls, pass/fail counts, and average latency.",
      inputSchema: {
        type: "object",
        properties: {},
      },
    },
  ],
}));

// Handle tool calls
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    switch (name) {
      case "read_file": {
        const result = await callBackend("/api/mcp/read_file", {
          file_id: args.file_id,
          user_id: args.user_id || DEFAULT_USER_ID,
          challenge_blocks: args.challenge_blocks || 30,
        });
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  tool: "read_file",
                  file: args.file_id,
                  audit_status: result.audit_status,
                  latency_ms: result.latency_ms,
                  data: result.file_data,
                  verified_by: "SecuAudit Threshold PDP",
                  timestamp: result.timestamp,
                },
                null,
                2
              ),
            },
          ],
        };
      }

      case "query_db": {
        const result = await callBackend("/api/mcp/query_db", {
          query: args.query,
          user_id: args.user_id || DEFAULT_USER_ID,
        });
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  tool: "query_db",
                  query: args.query,
                  audit_status: result.audit_status,
                  latency_ms: result.latency_ms,
                  result: result.result,
                  verified_by: "SecuAudit Threshold PDP",
                },
                null,
                2
              ),
            },
          ],
        };
      }

      case "audit_status": {
        const res = await fetch(BACKEND_URL + "/api/audit-log/mcp-stats");
        const stats = await res.json();
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  tool: "audit_status",
                  ...stats,
                  server: "SecuAudit MCP v1.0.0",
                  connected_to: BACKEND_URL,
                },
                null,
                2
              ),
            },
          ],
        };
      }

      default:
        throw new Error(`Unknown tool: ${name}`);
    }
  } catch (error) {
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              error: error.message,
              tool: name,
              args: args,
            },
            null,
            2
          ),
        },
      ],
      isError: true,
    };
  }
});

// Start server
const transport = new StdioServerTransport();
await server.connect(transport);
console.error("SecuAudit MCP Server started. Backend:", BACKEND_URL);
