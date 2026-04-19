# AGENTS.md

Guidelines for AI agents working in this repository.

---

## Project overview

A Model Context Protocol (MCP) server exposing two tools — `lookup_ip` and `get_my_ip` — backed by the ipinfo.io REST API. Packaged as a fat JAR and launched via stdio transport (JSON-RPC 2.0).

**Key tech:** Java 25, MCP Java SDK 1.1.1, Jackson 3, Maven Shade Plugin, CycloneDX Maven Plugin, JUnit 5, Mockito 5.

---

## Java 25 specifics

- `maven.compiler.release` is set to `25`. Do not lower it or add `--release` overrides.
- Use `var` for local variables where the type is obvious from context.
- Prefer records, sealed interfaces, and pattern matching where they reduce boilerplate.
- Do **not** use `--add-opens` / `--add-exports` workarounds. If a library requires them, find a compatible version instead.

### Mockito on Java 25

- The inline mock maker **cannot** instrument JDK module classes on Java 25.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` must contain `mock-maker-subclass` — do not change this.
- Avoid mocking JDK types (e.g., `HttpClient`, `HttpResponse`, `InputStream`). Use real implementations or thin wrappers instead.

---

## Jackson 3 API

This project uses Jackson 3 (`tools.jackson.*` package prefix), **not** Jackson 2 (`com.fasterxml.jackson.*`).

| Jackson 2 | Jackson 3 replacement |
|---|---|
| `asText()` | `asString()` |
| `textValue()` | `asString()` |
| `ObjectMapper` | `JsonMapper` |

Never import from `com.fasterxml.jackson`. Always import from `tools.jackson`.

---

## Security best practices

### Input validation
- `IpInfoClient.lookupIp()` rejects internal/private IP addresses before making any network call. This is enforced via `InetAddress` checks — do not remove or weaken them.
- Blocked address classes (IPv4 and IPv6):
  - Loopback: `127.0.0.0/8`, `::1`
  - Private (RFC 1918): `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
  - Link-local: `169.254.0.0/16`, `fe80::/10`
  - Any-local: `0.0.0.0`
  - IPv6 unique-local (fc00::/7) — covered by `isSiteLocalAddress()`
- The validation throws `IllegalArgumentException` (not `RuntimeException`) so callers can distinguish bad input from API failures.
- Never interpolate user-supplied strings into shell commands, SQL, or log messages without sanitisation.

### Token handling
- The API token is read from the `IPINFO_TOKEN` environment variable only. It must never be hard-coded, committed to source, logged, or included in exception messages.
- Treat the token as a secret: do not add it to error messages, stack traces, or test fixtures.
- If you add token handling logic, ensure blank/null tokens are treated identically (see `IpInfoClient.buildUrl`).

### HTTP security
- Use `java.net.http.HttpClient` (built-in). Do not add third-party HTTP libraries unless explicitly approved.
- Always set `connectTimeout` and request `timeout` (currently 10 s each). Do not remove these.
- Never disable hostname verification or certificate validation.
- Do not follow redirects to non-HTTPS URLs.

### Dependency management
- Keep the dependency list minimal. Every new dependency must be justified in the PR description.
- Prefer `<scope>runtime</scope>` or `<scope>test</scope>` for non-API dependencies.
- Do not add snapshot or locally-published dependencies.

### MCP tool handlers
- Tool call handlers must never throw unchecked exceptions to the MCP framework. Catch `Exception`, return `isError(true)` with a safe, user-facing message, and do not include internal stack traces or token values in the content text.

---

## Testing requirements

All business logic **must** have unit test coverage. "Business logic" includes:

- URL construction (path, query string, token presence/absence)
- HTTP response handling (2xx success, 4xx/5xx errors, body inclusion in error messages)
- Network error propagation
- MCP protocol handshake (initialize, tools/list, tools/call)
- Tool handler happy path and error path (`isError` flag, content text)
- Token edge cases: null, empty string, blank/whitespace-only

### Test style

- Test class names: `<Subject>Test` in the same package as the subject.
- One assertion concept per test; name tests as `method_condition_expectedBehavior`.
- Use `@BeforeEach`/`@AfterEach` for setup/teardown; do not share mutable state across tests.
- For HTTP-layer tests: spin up a JDK `com.sun.net.httpserver.HttpServer` on port 0 (ephemeral). Do not mock `HttpClient` or any JDK networking class.
- For MCP server tests: use `PipedInputStream`/`PipedOutputStream` to simulate the client side in-process. Always call the `buildServer(transport, jsonMapper, client)` factory — not `main()`.
- Add `Thread.sleep(100)` after `buildServer()` to let Reactor subscribe; increase only if tests become flaky on slow CI, do not remove it.
- `readResponse()` must time out (≤ 5 s) rather than block forever.

### Running tests

```bash
mvn test
```

All 35 tests must pass before merging. No skipped tests.

---

## MCP SDK API reference (v1.1.1)

The SDK JAR is `io.modelcontextprotocol.sdk:mcp:1.1.1`. Key packages:

| Type | Fully-qualified name |
|---|---|
| Schema types (Tool, CallToolResult, …) | `io.modelcontextprotocol.spec.McpSchema` |
| Sync server builder | `io.modelcontextprotocol.server.McpServer` |
| Sync server type | `io.modelcontextprotocol.server.McpSyncServer` |
| Tool spec builder | `io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification` |
| Stdio transport | `io.modelcontextprotocol.server.transport.StdioServerTransportProvider` |
| JSON mapper | `io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper` |

`CallToolResult` is built via `McpSchema.CallToolResult.builder().addTextContent(text).isError(bool).build()` — there is no constructor that takes `(String, boolean)`.

When adding new tools, copy the existing `SyncToolSpecification` pattern and provide the JSON Schema inline as a text block.

---

## Adding a new tool

1. Add a `SyncToolSpecification` constant in `IpInfoMcpServer.buildServer`.
2. Add a corresponding public method to `IpInfoClient` and a matching package-private overload that accepts `baseUrl` for testability.
3. Write tests in `IpInfoClientTest` (URL construction + response handling) and `IpInfoMcpServerTest` (happy path + error path).
4. Run `mvn test` — all tests must pass before opening a PR.

---

## What not to do

- Do not add Spring Boot, Spring Framework, or any DI framework.
- Do not add a logging framework beyond `slf4j-nop`. Log output must not appear on stdout (it corrupts the MCP stdio stream).
- Do not catch `Throwable` or suppress `InterruptedException` without re-interrupting the thread.
- Do not add `System.out.println` anywhere in main-source code.
- Do not store bearer tokens or secrets in test fixtures, even as obviously fake values — use `"testtoken"` patterns only for token-format tests, never real credentials.
