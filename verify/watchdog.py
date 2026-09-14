# Local liveness check for the two services running on this Pi
# (java_server on VERIFY_JAVA_PORT, Geyser/Bedrock on VERIFY_BEDROCK_PORT).
# Meant to run periodically (systemd timer, see watchdog-setup below) and
# post a Discord webhook message only on a state *change* — first time a
# service is seen down, and again when it comes back — not on every tick,
# so it doesn't spam the channel.
#
# This only proves the process is actually listening; it does not prove the
# service is reachable from the internet (router/ISP/DNS issues can still
# take it offline for everyone else while this check stays green). Pair it
# with an external uptime monitor (see verify/README.md) for that half.
import json
import os
import socket
import sys
import time
import urllib.request

DISCORD_WEBHOOK_URL = os.environ.get("WATCHDOG_DISCORD_WEBHOOK_URL", "")
JAVA_PORT = int(os.environ.get("VERIFY_JAVA_PORT", "25565"))
BEDROCK_PORT = int(os.environ.get("VERIFY_BEDROCK_PORT", "19132"))
STATE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".watchdog_state.json")

RAKNET_MAGIC = bytes([0x00, 0xFF, 0xFF, 0x00, 0xFE, 0xFE, 0xFE, 0xFE, 0xFD, 0xFD, 0xFD, 0xFD, 0x12, 0x34, 0x56, 0x78])


def check_tcp(host: str, port: int, timeout: float = 5.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def check_raknet(host: str, port: int, timeout: float = 5.0) -> bool:
    # Unconnected Ping (0x01) -> expect Unconnected Pong (0x1C) back.
    packet = bytes([0x01]) + int(time.time() * 1000).to_bytes(8, "big") + RAKNET_MAGIC + os.urandom(8)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(timeout)
    try:
        sock.sendto(packet, (host, port))
        data, _ = sock.recvfrom(2048)
        return len(data) > 0 and data[0] == 0x1C
    except OSError:
        return False
    finally:
        sock.close()


def load_state() -> dict:
    try:
        with open(STATE_PATH, "r") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_state(state: dict) -> None:
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def notify(message: str) -> None:
    if not DISCORD_WEBHOOK_URL:
        print("[watchdog] no WATCHDOG_DISCORD_WEBHOOK_URL set, skipping notify:", message)
        return
    body = json.dumps({"content": message}).encode("utf-8")
    req = urllib.request.Request(DISCORD_WEBHOOK_URL, data=body, headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req, timeout=10)
    except OSError as e:
        print("[watchdog] failed to post Discord webhook:", e)


def main() -> int:
    checks = {
        "java_server (verify, port %d)" % JAVA_PORT: check_tcp("127.0.0.1", JAVA_PORT),
        "geyser (bedrock bridge, port %d)" % BEDROCK_PORT: check_raknet("127.0.0.1", BEDROCK_PORT),
    }
    state = load_state()
    changed = False
    for name, is_up in checks.items():
        was_up = state.get(name, True)  # assume up on first run, don't alert immediately
        print("[watchdog]", name, "->", "UP" if is_up else "DOWN")
        if is_up != was_up:
            changed = True
            if is_up:
                notify("✅ SCTP verify server: **%s** is back up." % name)
            else:
                notify("⚠️ SCTP verify server: **%s** appears to be DOWN." % name)
        state[name] = is_up
    if changed:
        save_state(state)
    return 0


if __name__ == "__main__":
    sys.exit(main())
