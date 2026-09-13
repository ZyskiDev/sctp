"""VarInt encode/decode — same format Minecraft uses everywhere in its wire
protocol (packet length prefixes, string length prefixes, packet IDs)."""


def write_varint(value: int) -> bytes:
	out = bytearray()
	value &= 0xFFFFFFFF
	while True:
		if (value & ~0x7F) == 0:
			out.append(value)
			return bytes(out)
		out.append((value & 0x7F) | 0x80)
		value >>= 7


def read_varint_from_reader(read_exact) -> int:
	"""read_exact is a callable(n) -> bytes, from either a raw socket or a
	decrypting connection wrapper — see Connection.read_exact."""
	value = 0
	position = 0
	while True:
		byte = read_exact(1)[0]
		value |= (byte & 0x7F) << (7 * position)
		if not (byte & 0x80):
			break
		position += 1
		if position >= 5:
			raise ValueError("VarInt is too big")
	# Interpret as a signed 32-bit int, matching Java's int wraparound.
	if value & 0x80000000:
		value -= 0x100000000
	return value


def read_varint_from_buf(buf) -> int:
	value = 0
	position = 0
	while True:
		byte = buf.read(1)
		if not byte:
			raise ValueError("Ran out of bytes reading VarInt")
		byte = byte[0]
		value |= (byte & 0x7F) << (7 * position)
		if not (byte & 0x80):
			break
		position += 1
		if position >= 5:
			raise ValueError("VarInt is too big")
	if value & 0x80000000:
		value -= 0x100000000
	return value


def read_string_from_buf(buf) -> str:
	length = read_varint_from_buf(buf)
	return buf.read(length).decode("utf-8")


def write_string(s: str) -> bytes:
	b = s.encode("utf-8")
	return write_varint(len(b)) + b
