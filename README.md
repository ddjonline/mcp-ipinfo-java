# IPInfo Java MCP Server

A Model Context Protocol (MCP) server that provides IP geolocation and network information via the [ipinfo.io](https://ipinfo.io) API. This server allows AI agents to look up details about specific IP addresses or identify the public IP of the current machine.

## Features

- **lookup_ip**: Get city, region, country, coordinates, ISP/org, postal code, and timezone for any public IPv4 or IPv6 address.
- **get_my_ip**: Retrieve geolocation and network information for the host machine's current public IP.
- **Security**: Built-in validation to prevent the lookup of internal, private, or loopback IP addresses.

## Prerequisites

- **Java 25**: This project is configured for Java 25.
- **Maven**: Used for building and dependency management.
- **IPInfo Token**: An API token from [ipinfo.io](https://ipinfo.io/signup). While the API has a free tier, a token is recommended to avoid strict rate limiting.

## Building the Project

To package the application into a "fat" JAR (including all dependencies), run:

```bash
mvn clean package
```

The executable JAR will be generated at `target/mcp-ipinfo-java-1.0.0.jar`, along with a Software Bill of Materials (SBOM) at `target/mcp-ipinfo-java-YYYYMMDD-HHMMSS-sbom.xml`.

## Running Tests

The project includes a comprehensive suite of unit tests covering URL construction, security validation, and MCP protocol compliance.

```bash
mvn test
```

## Local testing using MCP Inspector

The easiest and most visually appealing way to test the MCP Server is to use the [MCP Inspector](https://github.com/ModelContextProtocol/mcp-inspector).

Install the latest LTS version of Node and NPM which should contain the `npx` command line tool which will download and run the package inline.

```bash
npx @modelcontextprotocol/inspector java -jar target/mcp-ipinfo-java-1.0.0.jar 
```

This should automatically open the inspector tool at [http://localhost:6274](http://localhost:6274)

Click the "Connect" button in the left side panel.

Click "List Tools" in the middle "Tools" panel and verify the tools are correct.

Click on the `lookup_ip` tool instance and enter an external IP in the right side panel's input field.

## Configuration and Running

The server communicates via standard input/output (`stdio`) using JSON-RPC 2.0.

### Environment Variables

- `IPINFO_TOKEN`: Your ipinfo.io API token.

### Manual Execution

```bash
mvn -U clean package
export IPINFO_TOKEN=your_token_here
java -jar target/mcp-ipinfo-java-1.0.0.jar
```

Example initialize

```json
{
  "method": "initialize"
}
```

Example tools list

```json
{
  "method": "tools/list",
  "params": {}
}
```

Example `lookup_ip` tools call

```json
{
  "method": "tools/call",
  "params": {
    "name": "lookup_ip",
    "arguments": {
      "ip": "some.random.ip.address"
    },
    "_meta": {
      "progressToken": 0
    }
  }
}
```

### Usage with Claude Desktop

To use this server with Claude Desktop, add the following entry to your `claude_desktop_config.json` (typically located at `~/Library/Application Support/Claude/claude_desktop_config.json` on macOS or `%APPDATA%\Claude\claude_desktop_config.json` on Windows):

```json 
{
  "mcpServers": {
    "ipinfo": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-ipinfo/target/mcp-ipinfo-java-1.0.0.jar"
      ],
      "env": {
        "IPINFO_TOKEN": "your_token_here"
      }
    }
  }
}
```

## Tool Specifications

### `lookup_ip`
Provides geolocation and network data for a provided IP.
- **Arguments**:
  - `ip` (string, required): The IPv4 or IPv6 address to investigate.

### `get_my_ip`
Provides geolocation and network data for the machine running the server.
- **Arguments**: None.

## Tech Stack

- MCP Java SDK (v1.1.1)
- Jackson 3 for JSON processing
- JUnit 5 and Mockito for testing