"""The real verification path — a direct Java Edition client connects,
completes the actual online-mode login handshake (RSA key exchange, then
Mojang's hasJoined check), and we read the verification code straight out of
the server address they typed (e.g. "AB12CD.verify.sctp.nl"), no separate
in-game step needed.

Deliberately does NOT implement anything past the Login state — no Play
state, no world, no chunks. We only need enough of the protocol to
authenticate who's connecting, then send a Disconnect packet with the
result and close the socket. This is the same "kick during login with a
message" mechanism real servers use for bans/whitelists.

Known limitation: does not send the newer "should authenticate" boolean
field some 1.20.5+ clients' Encryption Request expects. The core
handshake/encryption/hasJoined sequence has been stable across a very wide
version range, but this specific detail hasn't been tested against a real
client — if very recent clients fail here, this is the first thing to check.
"""

import json
import os
import socket
import struct
import threading

import config
import worker_client
from protocol.connection import Connection
from protocol.crypto import load_or_create_keypair, rsa_decrypt, make_aes_cfb8
from protocol.mojang import server_hash, has_joined
from protocol.varint import write_varint, write_string, read_string_from_buf, read_varint_from_buf

PRIVATE_KEY, PUBLIC_KEY_DER = load_or_create_keypair(config.RSA_KEY_PATH)


def _extract_code(server_address: str) -> str:
	# Whatever the player typed, e.g. "AB12CD.verify.sctp.nl" -> "AB12CD".
	# Also tolerates them just typing the raw code with no domain at all.
	first_label = server_address.split(".")[0]
	return first_label.strip().upper()


def _disconnect(conn: Connection, message: str):
	reason = json.dumps({"text": message})
	conn.write_packet(0x00, write_string(reason))


def _handle_status(conn: Connection):
	# Not strictly required, but nice: without this, a player just adding the
	# server to their list would see a blank/failed entry instead of a sane
	# description while they're not actively joining yet.
	packet_id, buf = conn.read_packet()  # Status Request, empty body
	status = {
		"version": {"name": "SCTP Verify", "protocol": 0},
		"players": {"max": 1, "online": 0, "sample": []},
		"description": {"text": "SCTP account verification — join to link your account, then check the website."},
	}
	conn.write_packet(0x00, write_string(json.dumps(status)))
	# Clients may follow up with a Ping Request (0x01, one long payload) and
	# expect it echoed back — handle it if it comes, but don't require it.
	try:
		packet_id, buf = conn.read_packet()
		if packet_id == 0x01:
			payload = buf.read(8)
			conn.write_packet(0x01, payload)
	except (ConnectionError, OSError):
		pass


def _handle_login(conn: Connection, server_address: str, protocol_version: int):
	packet_id, buf = conn.read_packet()  # Login Start
	if packet_id != 0x00:
		return
	claimed_username = read_string_from_buf(buf)
	# Ignore whatever else Login Start carries (UUID/signature fields vary by
	# version) — we don't need any of it, the real UUID comes from Mojang.

	code = _extract_code(server_address)

	verify_token = os.urandom(4)
	enc_request = (
		write_string("")  # server ID — always empty, we don't need IP-binding
		+ write_varint(len(PUBLIC_KEY_DER)) + PUBLIC_KEY_DER
		+ write_varint(len(verify_token)) + verify_token
	)
	# Confirmed by real-world testing: 1.20.5+ clients (protocol 766+) add a
	# trailing "should authenticate" boolean to this packet and fail to
	# decode it without one. Older clients don't expect this extra byte, so
	# it has to be conditional on what the client itself declared in the
	# Handshake, not sent unconditionally either way.
	if protocol_version >= 766:
		enc_request += b"\x01"  # true — yes, verify via Mojang (that's the whole point)
	conn.write_packet(0x01, enc_request)

	packet_id, buf = conn.read_packet()  # Encryption Response
	if packet_id != 0x01:
		return
	secret_len = read_varint_from_buf(buf)
	encrypted_secret = buf.read(secret_len)
	token_len = read_varint_from_buf(buf)
	encrypted_token = buf.read(token_len)

	try:
		shared_secret = rsa_decrypt(PRIVATE_KEY, encrypted_secret)
		returned_token = rsa_decrypt(PRIVATE_KEY, encrypted_token)
	except ValueError:
		# No usable shared secret at all here — by this point in the real
		# protocol a genuine client already expects everything from us to be
		# encrypted, so a plaintext message would just fail to decode on
		# their end anyway. Only reachable for a malformed/malicious client,
		# never a normal player, so closing silently is fine.
		return
	if returned_token != verify_token:
		return

	encryptor, decryptor = make_aes_cfb8(shared_secret)
	conn.enable_encryption(encryptor, decryptor)

	digest = server_hash(shared_secret, PUBLIC_KEY_DER)
	mojang_result = has_joined(claimed_username, digest)

	if mojang_result is None:
		_disconnect(conn, "Couldn't verify — this only works with a real, logged-in Java Edition account (not offline/cracked mode).")
		return

	real_username = mojang_result["name"]
	real_uuid = mojang_result["id"]

	ok = worker_client.report_verified(code, real_username, real_uuid)
	if ok:
		_disconnect(conn, "✅ Verified as " + real_username + "! Go back to sctp.nl to finish creating your account.")
	else:
		_disconnect(conn, "Verified, but couldn't reach sctp.nl to confirm it (your code may have expired) — please go back and try again.")


def _handle_connection(sock: socket.socket, addr):
	conn = Connection(sock)
	try:
		packet_id, buf = conn.read_packet()  # Handshake
		if packet_id != 0x00:
			return
		protocol_version = read_varint_from_buf(buf)
		server_address = read_string_from_buf(buf)
		server_port = struct.unpack(">H", buf.read(2))[0]
		next_state = read_varint_from_buf(buf)

		if next_state == 1:
			_handle_status(conn)
		elif next_state == 2:
			_handle_login(conn, server_address, protocol_version)
	except (ConnectionError, OSError, ValueError):
		pass
	finally:
		conn.close()


def serve():
	listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
	listener.bind(("0.0.0.0", config.JAVA_PORT))
	listener.listen(16)
	print(f"[java] listening on 0.0.0.0:{config.JAVA_PORT}")
	while True:
		sock, addr = listener.accept()
		threading.Thread(target=_handle_connection, args=(sock, addr), daemon=True).start()
