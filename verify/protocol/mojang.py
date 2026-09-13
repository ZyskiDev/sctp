"""The actual proof of Minecraft account ownership — this is the exact same
hasJoined check every real online-mode Java Edition server performs during
login. A 200 response with a matching username IS cryptographic confirmation
that the connecting client authenticated with Mojang/Microsoft as that
account moments ago; nothing about this is spoofable without owning it."""

import hashlib

import requests

from .crypto import java_hex_digest

HAS_JOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined"


def server_hash(shared_secret: bytes, public_key_der: bytes) -> str:
	# server_id is always the empty string for us — we don't need Mojang's
	# join-prevents-proxy IP binding, just confirmation of who they are.
	sha = hashlib.sha1()
	sha.update(b"")
	sha.update(shared_secret)
	sha.update(public_key_der)
	return java_hex_digest(sha.digest())


def has_joined(username: str, server_id_hash: str):
	"""Returns {"id": "<uuid, no dashes>", "name": "<real, authoritative username>"} on
	success, or None if this wasn't a genuine authenticated join attempt."""
	try:
		resp = requests.get(HAS_JOINED_URL, params={"username": username, "serverId": server_id_hash}, timeout=10)
	except requests.RequestException:
		return None
	if resp.status_code != 200:
		return None
	try:
		data = resp.json()
	except ValueError:
		return None
	if "id" not in data or "name" not in data:
		return None
	return data
