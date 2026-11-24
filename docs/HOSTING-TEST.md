# Pre-Flight Hosting IP Verification Guide

Before deploying Savyor on a cloud provider (such as Oracle Cloud Infrastructure / OCI Always Free VMs, Hetzner, DigitalOcean, or AWS), it is crucial to test whether the VM's assigned public IP address is flagged or blocked by Instagram.

Instagram aggressively blocks datacenter IP subnets, forcing downloads to fail with "Login required", "Rate limited", or empty streams. Running this test upfront avoids spending time setting up on a flagged machine.

---

## 1. How to Run on a New VM

### Prerequisites:
1. Clone the repository or copy `scripts/hosting_test.py` and `requirements.txt`.
2. Ensure Python 3.10+ is installed:
   ```bash
   sudo apt update && sudo apt install -y python3 python3-pip python3-venv
   ```
3. Set up a virtual environment and install dependencies:
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install yt-dlp
   ```

### Running the Test:
1. Create a `urls.txt` file in the project root containing 10–20 varied public Instagram reel URLs:
   ```bash
   cat << 'EOF' > urls.txt
   https://www.instagram.com/reel/Df-OIaqgGZy/
   https://www.instagram.com/reel/DfBEEFBo52M/
   # Add 10-15 more public reels from different creators
   EOF
   ```
2. Run the test script:
   ```bash
   python3 scripts/hosting_test.py
   ```

---

## 2. Recommended Test Size

- **10 to 20 diverse public reels** from different accounts (cooking, coding, sports, comedy).
- Avoid private accounts or restricted audio.
- The test runs without cookies, proxies, or credentials to simulate pure unauthenticated extraction.

---

## 3. Interpreting Results

| Success Rate | Status | Action |
| :--- | :--- | :--- |
| **≥ 80%** | **Clean IP** | Safe to proceed with Savyor installation. The IP range is not flagged by Instagram. |
| **50% – 79%** | **Moderate Risk** | Occasional rate-limiting or soft blocks. Direct uploads will work as a fallback. |
| **< 50%** | **Flagged IP** | Instagram has blacklisted this hosting provider's IP range. Downloads will frequently fail. |

### Failure Categories:
- **login required**: High indication of datacenter IP reputation block.
- **rate limited**: The IP has hit Instagram's unauthenticated query throttle.
- **network or timeout**: Latency/firewall issues reaching Instagram CDN edge servers.
- **private or unavailable**: Reel was deleted or creator account is set to private.

---

## 4. What to Do If Your Hosting IP is Flagged

1. **Re-roll Ephemeral Public IP**:
   - In Oracle Cloud / AWS console, detach the Public IP or terminate and recreate the VNIC to receive a new IP address from a different pool.
2. **Launch in a Different Availability Domain (AD) or Region**:
   - Different subnets or regions (e.g. Frankfurt vs Amsterdam, US-Ashburn vs US-Phoenix) have distinct IP block reputations.
3. **Use Direct Video Upload**:
   - Savyor supports direct `.mp4` / `.mov` file uploads via the UI. If a URL cannot be downloaded, users can download the video on their phone/browser and upload it directly into Savyor for AI extraction.
4. **Configure a Residential Proxy**:
   - If hosting in a strictly flagged datacenter, route `yt-dlp` traffic through an unblocked proxy.
