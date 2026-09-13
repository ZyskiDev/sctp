import os

# Public port real Java Edition clients connect to directly. Runs the full
# RSA/AES/Mojang-hasJoined login sequence — see java_server.py.
JAVA_PORT = int(os.environ.get("VERIFY_JAVA_PORT", "25565"))

# NOT meant to be internet-facing — bind this to 127.0.0.1 (see server.py).
# Geyser (running alongside, doing real Xbox Live auth for Bedrock players)
# forwards into this port as a plain offline-mode Java client. Anything that
# can reach this port directly is implicitly trusted as "already
# Xbox-authenticated by Geyser", so it must never be reachable from outside
# this machine — see bedrock_bridge.py's docstring for the full reasoning.
BEDROCK_BRIDGE_PORT = int(os.environ.get("VERIFY_BEDROCK_BRIDGE_PORT", "25567"))
BEDROCK_BRIDGE_HOST = os.environ.get("VERIFY_BEDROCK_BRIDGE_HOST", "127.0.0.1")

# Matches Floodgate's default `username-prefix` setting on the real Snailcraft
# server — every Bedrock account gets this prepended so it can never collide
# with a real Java username, same convention as the live server.
BEDROCK_USERNAME_PREFIX = os.environ.get("VERIFY_BEDROCK_PREFIX", ".")

RSA_KEY_PATH = os.environ.get("VERIFY_RSA_KEY_PATH", "verify_rsa_key.pem")

WORKER_API_BASE = os.environ.get(
	"VERIFY_WORKER_API_BASE",
	"https://snailcraft-trading-post.snailcraft-trading-post.workers.dev",
)

# Shared secret between this process and the Worker (see worker.js's
# VERIFY_SERVER_SECRET) — set via a real env var, never hardcoded here.
WORKER_SHARED_SECRET = os.environ.get("VERIFY_WORKER_SHARED_SECRET", "")

if not WORKER_SHARED_SECRET:
	raise RuntimeError(
		"VERIFY_WORKER_SHARED_SECRET is not set. Generate one, set it as this "
		"process's env var, AND set the same value as the Worker's "
		"VERIFY_SERVER_SECRET (wrangler secret put VERIFY_SERVER_SECRET)."
	)
