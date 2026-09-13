"""Bedrock verification path.

Server List Ping and Java's Handshake packet both let a client tell us
something about who/where they're trying to reach — RakNet (Bedrock's
transport) has no equivalent field, so we can't reuse the
"<code>.verify.sctp.nl" trick here the way java_server.py does.

Instead: this listener is NOT meant to be reachable from the internet at
all (bind it to 127.0.0.1 — see config.BEDROCK_BRIDGE_HOST). Geyser runs
locally alongside this process, does REAL Xbox Live authentication for the
connecting Bedrock player (that's Geyser's own job, and it's exactly as
unspoofable as Mojang's hasJoined check is for Java — you cannot make
Geyser believe you're a Bedrock account you don't own), and only then
forwards the connection here as a plain offline-mode Java login. Because
nothing else can reach this port, "a connection arrived here claiming
username X" already means "Geyser just finished proving, via Xbox Live,
that the real connecting player owns X" — no further crypto needed on our
end, which sidesteps needing to reverse-engineer Floodgate's own encrypted
data format (genuinely not confident of getting that byte-for-byte right
without live testing against it).

Since there's no per-connection code to read, we instead ask the Worker
which pending registration currently claims this exact (now-confirmed)
username — see worker_client.find_pending_code_for_username.

Setup: run Geyser standalone, `remote.auth-type: offline`,
`remote.address/port` pointed at 127.0.0.1:<this port> — see README.md.
"""

import json
import socket
import struct
import threading

import config
import worker_client
from protocol.connection import Connection
from protocol.varint import write_string, read_string_from_buf, read_varint_from_buf


def _disconnect(conn: Connection, message: str):
	reason = json.dumps({"text": message})
	conn.write_packet(0x00, write_string(reason))


def _handle_login(conn: Connection):
	packet_id, buf = conn.read_packet()  # Login Start
	if packet_id != 0x00:
		return
	gamertag = read_string_from_buf(buf)
	final_username = config.BEDROCK_USERNAME_PREFIX + gamertag

	code = worker_client.find_pending_code_for_username(final_username)
	if not code:
		_disconnect(conn, "No pending registration found for " + final_username
				+ " — start one at sctp.nl/register first, then join within a few minutes.")
		return

	ok = worker_client.report_verified(code, final_username, None)
	if ok:
		_disconnect(conn, "✅ Verified as " + final_username + "! Go back to sctp.nl to finish creating your account.")
	else:
		_disconnect(conn, "Verified, but couldn't reach sctp.nl to confirm it (your code may have expired) — please go back and try again.")


def _handle_connection(sock: socket.socket, addr):
	conn = Connection(sock)
	try:
		packet_id, buf = conn.read_packet()  # Handshake
		if packet_id != 0x00:
			return
		read_varint_from_buf(buf)  # protocol version — unused, offline mode doesn't care
		read_string_from_buf(buf)  # server address — unused here, see module docstring
		buf.read(2)  # server port
		next_state = read_varint_from_buf(buf)

		if next_state == 2:
			_handle_login(conn)
		# next_state == 1 (status) deliberately unhandled — Geyser itself
		# answers Bedrock-side pings, this port never needs to.
	except (ConnectionError, OSError, ValueError):
		pass
	finally:
		conn.close()


def serve():
	listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
	listener.bind((config.BEDROCK_BRIDGE_HOST, config.BEDROCK_BRIDGE_PORT))
	listener.listen(16)
	print(f"[bedrock-bridge] listening on {config.BEDROCK_BRIDGE_HOST}:{config.BEDROCK_BRIDGE_PORT} (Geyser only, must not be internet-facing)")
	while True:
		sock, addr = listener.accept()
		threading.Thread(target=_handle_connection, args=(sock, addr), daemon=True).start()
