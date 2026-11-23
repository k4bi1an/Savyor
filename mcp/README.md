# Project MCP

## 1. Setup
    python -m venv .venv
    .venv\Scripts\activate        (Windows)   |   source .venv/bin/activate (Mac/Linux)
    pip install -r requirements.txt
Edit `projects.json` with your real project paths and the named commands you allow.

## 2. Antigravity (stdio)
Add to `~/.gemini/config/mcp_config.json` (or `<workspace>/.agents/mcp_config.json`):

    {
      "mcpServers": {
        "projects": {
          "command": "C:/path/to/project-mcp/.venv/Scripts/python.exe",
          "args": ["C:/path/to/project-mcp/server.py"]
        }
      }
    }
Then Settings > Customizations > Installed MCP Servers > Refresh (CLI: `agy`, then `/mcp`).

## 3. Gemini web (HTTP + tunnel)
    python server.py --http          # prints http://127.0.0.1:8765/<secret>/mcp
    cloudflared tunnel --url http://127.0.0.1:8765     # or: ngrok http 8765
In Gemini: gemini.google.com/devmode > Create new > Connect to an MCP server,
URL = https://<tunnel-host>/<secret>/mcp
Treat that URL like a password. Stop the tunnel when you're done.
