import socket
import ipaddress
import urllib.parse
from typing import Dict, Any, List, Optional
import requests

def is_safe_ip(ip_str: str) -> bool:
    """Verifies that an IP address is not private, loopback, link-local, or reserved (SSRF protection)."""
    try:
        ip = ipaddress.ip_address(ip_str)
        return not (
            ip.is_private or
            ip.is_loopback or
            ip.is_link_local or
            ip.is_reserved or
            ip.is_multicast or
            ip.is_unspecified
        )
    except ValueError:
        return False

def is_safe_host(hostname: str) -> bool:
    """Resolves hostname and verifies that all resolved IPs are safe external addresses."""
    if not hostname:
        return False
    hostname_clean = hostname.strip().lower()
    if hostname_clean in ("localhost", "127.0.0.1", "0.0.0.0", "::1", "localhost.localdomain"):
        return False
    try:
        infos = socket.getaddrinfo(hostname_clean, None)
        for _, _, _, _, sockaddr in infos:
            ip = sockaddr[0]
            if not is_safe_ip(ip):
                return False
        return True
    except Exception:
        return False

def verify_single_url(url: str) -> Dict[str, Any]:
    """
    Performs safe HTTP verification for an external link:
    - Normalizes URL and allows only http/https.
    - Applies SSRF protection against localhost and private IP ranges at each hop.
    - Follows at most 3 redirects.
    - 5s timeout, HEAD with GET fallback.
    - If github.com repo, queries unauthenticated GitHub API for stars and description.
    """
    if not url or not url.strip():
        return {"link_status": "unverified", "final_url": ""}

    cleaned_url = url.strip()
    parsed = urllib.parse.urlparse(cleaned_url)
    if parsed.scheme.lower() not in ("http", "https"):
        return {"link_status": "unreachable", "final_url": cleaned_url, "error": "Invalid protocol"}

    current_url = cleaned_url
    redirect_count = 0
    max_redirects = 3
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Savyor/2.0"}

    while redirect_count <= max_redirects:
        p = urllib.parse.urlparse(current_url)
        if p.scheme.lower() not in ("http", "https"):
            return {"link_status": "unreachable", "final_url": current_url, "error": "Non-HTTP redirect"}
        if not is_safe_host(p.hostname):
            return {"link_status": "unreachable", "final_url": current_url, "error": "Blocked private address"}

        try:
            resp = requests.head(current_url, headers=headers, timeout=5, allow_redirects=False)
            if resp.status_code in (405, 501):
                resp = requests.get(current_url, headers=headers, timeout=5, stream=True, allow_redirects=False)

            if resp.is_redirect or resp.status_code in (301, 302, 303, 307, 308):
                location = resp.headers.get("Location")
                if not location:
                    break
                current_url = urllib.parse.urljoin(current_url, location)
                redirect_count += 1
                continue

            if 200 <= resp.status_code < 400:
                result: Dict[str, Any] = {"link_status": "verified", "final_url": current_url}
                # Check for GitHub repository
                gh_parse = urllib.parse.urlparse(current_url)
                if gh_parse.netloc in ("github.com", "www.github.com"):
                    parts = [pt for pt in gh_parse.path.strip("/").split("/") if pt]
                    if len(parts) >= 2 and parts[0] not in ("features", "pricing", "topics", "collections", "trending", "explore", "search"):
                        owner, repo = parts[0], parts[1].replace(".git", "")
                        try:
                            gh_resp = requests.get(
                                f"https://api.github.com/repos/{owner}/{repo}",
                                headers={"User-Agent": "Savyor-App"},
                                timeout=4
                            )
                            if gh_resp.status_code == 200:
                                gh_data = gh_resp.json()
                                result["github_stars"] = gh_data.get("stargazers_count")
                                result["github_desc"] = gh_data.get("description")
                        except Exception:
                            pass
                return result
            else:
                return {"link_status": "unreachable", "final_url": current_url, "status_code": resp.status_code}

        except Exception as e:
            return {"link_status": "unreachable", "final_url": current_url, "error": str(e)}

    return {"link_status": "unreachable", "final_url": current_url, "error": "Too many redirects"}

def verify_reel_resources(resources: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Verifies all URLs in a list of extracted resources."""
    verified_list = []
    for res in resources:
        item = dict(res)
        raw_url = item.get("url", "")
        if raw_url and raw_url.strip():
            ver_result = verify_single_url(raw_url)
            item["link_status"] = ver_result.get("link_status", "unverified")
            if ver_result.get("final_url"):
                item["final_url"] = ver_result.get("final_url")
            if "github_stars" in ver_result:
                item["github_stars"] = ver_result["github_stars"]
            if "github_desc" in ver_result:
                item["github_desc"] = ver_result["github_desc"]
        else:
            item["link_status"] = "unverified"
        verified_list.append(item)
    return verified_list
