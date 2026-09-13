"""Thin wrapper around a raw socket that adds Minecraft's length-prefixed
packet framing, and can be switched into AES/CFB8 encrypted mode partway
through a connection (exactly when real Minecraft servers do it — right
after the client's Encryption Response is processed)."""

import io
import socket

from .varint import write_varint, read_varint_from_reader, read_varint_from_buf


class Connection:
	def __init__(self, sock: socket.socket):
		self.sock = sock
		self._encryptor = None
		self._decryptor = None
		self._recv_buf = b""

	def enable_encryption(self, encryptor, decryptor):
		self._encryptor = encryptor
		self._decryptor = decryptor

	def _recv_raw(self, n: int) -> bytes:
		while len(self._recv_buf) < n:
			chunk = self.sock.recv(4096)
			if not chunk:
				raise ConnectionError("Connection closed by peer")
			self._recv_buf += chunk
		data, self._recv_buf = self._recv_buf[:n], self._recv_buf[n:]
		return data

	def read_exact(self, n: int) -> bytes:
		data = self._recv_raw(n)
		if self._decryptor is not None:
			data = self._decryptor.update(data)
		return data

	def send_raw(self, data: bytes):
		if self._encryptor is not None:
			data = self._encryptor.update(data)
		self.sock.sendall(data)

	def read_packet(self):
		"""Returns (packet_id, io.BytesIO positioned right after the packet ID)."""
		length = read_varint_from_reader(self.read_exact)
		data = self.read_exact(length)
		buf = io.BytesIO(data)
		packet_id = read_varint_from_buf(buf)
		return packet_id, buf

	def write_packet(self, packet_id: int, payload: bytes):
		body = write_varint(packet_id) + payload
		self.send_raw(write_varint(len(body)) + body)

	def close(self):
		try:
			self.sock.close()
		except OSError:
			pass
