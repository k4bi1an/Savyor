import os
import sys
import time
import socket
import subprocess

# Configure test environment variables for all e2e tests
os.environ["SAVYOR_PASSCODE"] = "test_custom_passcode_2026"
os.environ["SAVYOR_SECRET_KEY"] = "test_custom_secret_key_99999"
os.environ["SAVYOR_TEST_BASE_URL"] = "http://127.0.0.1:8001"

import pytest
import requests

def is_port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(('127.0.0.1', port)) == 0

@pytest.fixture(scope="session", autouse=True)
def ensure_test_server():
    server_proc = None
    if not is_port_in_use(8001):
        test_server_path = os.path.join(os.path.dirname(__file__), "test_server.py")
        server_proc = subprocess.Popen(
            [sys.executable, test_server_path],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        # Wait up to 10 seconds for server to become ready
        for _ in range(50):
            try:
                r = requests.get("http://127.0.0.1:8001/healthz", timeout=1)
                if r.status_code == 200:
                    break
            except Exception:
                time.sleep(0.2)
                
    yield
    
    if server_proc:
        server_proc.terminate()
        try:
            server_proc.wait(timeout=3)
        except Exception:
            server_proc.kill()
