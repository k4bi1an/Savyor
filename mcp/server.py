"""Local project-control MCP server.

Modes:
  python server.py            -> stdio  (Antigravity IDE / CLI)
  python server.py --http     -> HTTP   (Gemini web, via a tunnel)

Safety model:
  * Only projects listed in projects.json are reachable.
  * All file paths are confined to the project root.
  * No arbitrary shell: run_command runs only commands you named in projects.json.
  * HTTP mode serves on a secret URL path (token from MCP_TOKEN or .token file).
"""
import json
import os
import secrets
import subprocess
import sys
from pathlib import Path

from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings

HERE = Path(__file__).parent
SELF_DIR = HERE.resolve()
CONFIG = HERE / "projects.json"
MAX_READ = 200_000          # bytes returned from read_file
MAX_OUT = 20_000            # chars returned from commands
SKIP_DIRS = {".git", "node_modules", ".venv", "__pycache__", "build", ".pio", "dist"}
# Files that hold secrets: never readable, writable, listed or searched.
SECRET_GLOBS = [".env", ".env.*", "*.pem", "*.key", "*.p12", "*.pfx", "id_rsa*",
                "id_ed25519*", ".token", "*credentials*", "*secret*", ".npmrc", ".netrc"]
GIT_EXCLUDES = [f":(exclude,glob)**/{g}" for g in SECRET_GLOBS] + \
               [f":(exclude,glob){g}" for g in SECRET_GLOBS]


def _is_secret(p: Path) -> bool:
    return any(p.match(g) for g in SECRET_GLOBS)


def _token() -> str:
    t = os.environ.get("MCP_TOKEN")
    if t:
        return t
    f = HERE / ".token"
    if not f.exists():
        f.write_text(secrets.token_urlsafe(24))
    return f.read_text().strip()


HTTP = "--http" in sys.argv
PORT = int(os.environ.get("MCP_PORT", "8765"))
mcp = FastMCP(
    "projects",
    host="127.0.0.1",
    port=PORT,
    streamable_http_path=f"/{_token()}/mcp" if HTTP else "/mcp",
    # The tunnel sends its own hostname (e.g. xyz.trycloudflare.com), which the default
    # localhost-only host check rejects. The secret URL path is the protection instead.
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)


def _projects() -> dict:
    return json.loads(CONFIG.read_text())["projects"]


def _root(project: str) -> Path:
    p = _projects().get(project)
    if not p:
        raise ValueError(f"Unknown project '{project}'. Use list_projects.")
    return Path(p["path"]).expanduser().resolve()


def _safe(project: str, rel: str) -> Path:
    root = _root(project)
    target = (root / rel).resolve()
    if target != root and root not in target.parents:
        raise ValueError("Path escapes the project root.")
    if _is_secret(target):
        raise ValueError("Blocked: this looks like a secrets file.")
    return target


def _run(cmd, cwd: Path, shell=False, timeout=120) -> str:
    try:
        r = subprocess.run(cmd, cwd=cwd, shell=shell, capture_output=True,
                           text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return f"Timed out after {timeout}s."
    out = (r.stdout + r.stderr).strip()
    if len(out) > MAX_OUT:
        out = out[:MAX_OUT] + "\n...[truncated]"
    return f"exit={r.returncode}\n{out}"


@mcp.tool()
def list_projects() -> str:
    """List configured projects, their paths, and the named commands each allows."""
    return json.dumps(
        {k: {"path": v["path"], "commands": list(v.get("commands", {}))}
         for k, v in _projects().items()}, indent=2)


@mcp.tool()
def project_tree(project: str, subpath: str = ".", depth: int = 2) -> str:
    """Show the directory tree of a project (skips .git, node_modules, etc.)."""
    base = _safe(project, subpath)
    lines = []

    def walk(d: Path, level: int):
        if level > depth:
            return
        for e in sorted(d.iterdir(), key=lambda x: (x.is_file(), x.name)):
            if e.name in SKIP_DIRS or _is_secret(e):
                continue
            lines.append("  " * level + e.name + ("/" if e.is_dir() else ""))
            if e.is_dir():
                walk(e, level + 1)

    walk(base, 0)
    return "\n".join(lines[:500]) or "(empty)"


@mcp.tool()
def read_file(project: str, path: str) -> str:
    """Read a text file inside a project."""
    f = _safe(project, path)
    data = f.read_bytes()[:MAX_READ]
    return data.decode("utf-8", errors="replace")


@mcp.tool()
def write_file(project: str, path: str, content: str) -> str:
    """Create or overwrite a text file inside a project. Overwrites without backup;
    use git_diff afterwards to review."""
    f = _safe(project, path)
    if ".git" in f.parts:
        raise ValueError("Refusing to write inside .git.")
    if f == SELF_DIR or SELF_DIR in f.parents:
        raise ValueError("Blocked: the MCP server's own folder is read-only to the AI.")
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(content, encoding="utf-8")
    return f"Wrote {len(content)} chars to {path}"


@mcp.tool()
def search_in_project(project: str, query: str) -> str:
    """Case-insensitive text search across a project's files (git grep if a repo, else grep)."""
    root = _root(project)
    if (root / ".git").exists():
        return _run(["git", "grep", "-n", "-i", "-I", "-e", query, "--", "."] + GIT_EXCLUDES, root)
    ex = [f"--exclude={g}" for g in SECRET_GLOBS]
    return _run(["grep", "-rniI", "--exclude-dir=.git", "--exclude-dir=node_modules"]
                + ex + ["-e", query, "."], root)


@mcp.tool()
def git_status(project: str) -> str:
    """git status (short, with branch) for a project."""
    return _run(["git", "status", "-sb"], _root(project))


@mcp.tool()
def git_diff(project: str, staged: bool = False) -> str:
    """git diff of working tree changes (or staged changes)."""
    return _run(["git", "diff"] + (["--staged"] if staged else []) + ["--", "."] + GIT_EXCLUDES,
                _root(project))


@mcp.tool()
def git_log(project: str, count: int = 10) -> str:
    """Recent commits, one line each."""
    return _run(["git", "log", "--oneline", f"-{min(count, 50)}"], _root(project))


@mcp.tool()
def git_commit(project: str, message: str) -> str:
    """Stage all changes and commit with the given message."""
    root = _root(project)
    a = _run(["git", "add", "-A", "--", "."] + GIT_EXCLUDES, root)
    return a + "\n" + _run(["git", "commit", "-m", message], root)


@mcp.tool()
def run_command(project: str, name: str) -> str:
    """Run a NAMED command defined for the project in projects.json (e.g. 'test', 'build',
    'flash'). Arbitrary shell commands are not accepted."""
    cmds = _projects()[project].get("commands", {})
    if name not in cmds:
        return f"Unknown command '{name}'. Allowed: {list(cmds)}"
    return _run(cmds[name], _root(project), shell=True, timeout=300)


if __name__ == "__main__":
    if HTTP:
        print(f"Serving on http://127.0.0.1:{PORT}/{_token()}/mcp", file=sys.stderr)
        mcp.run(transport="streamable-http")
    else:
        mcp.run()
