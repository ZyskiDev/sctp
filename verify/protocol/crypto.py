"""RSA keypair (for the login encryption handshake) and the specific,
slightly-unusual SHA1-as-signed-BigInteger hex digest Mojang's join protocol
uses for the server-hash sent to hasJoined. Also the AES/CFB8 cipher setup —
Minecraft uses the 16-byte shared secret as BOTH the AES key and the IV,
which is unusual but required for client compatibility."""

import os

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes


def load_or_create_keypair(path: str):
	"""Persists across restarts so we're not regenerating a 1024-bit RSA key
	on every launch — cheap either way, but no reason not to cache it."""
	if os.path.exists(path):
		with open(path, "rb") as f:
			private_key = serialization.load_pem_private_key(f.read(), password=None)
	else:
		private_key = rsa.generate_private_key(public_exponent=65537, key_size=1024)
		with open(path, "wb") as f:
			f.write(private_key.private_bytes(
				encoding=serialization.Encoding.PEM,
				format=serialization.PrivateFormat.PKCS8,
				encryption_algorithm=serialization.NoEncryption(),
			))
	public_key_der = private_key.public_key().public_bytes(
		encoding=serialization.Encoding.DER,
		format=serialization.PublicFormat.SubjectPublicKeyInfo,
	)
	return private_key, public_key_der


def rsa_decrypt(private_key, data: bytes) -> bytes:
	return private_key.decrypt(data, padding.PKCS1v15())


def java_hex_digest(digest_bytes: bytes) -> str:
	"""Matches `new BigInteger(sha1Digest).toString(16)` in Java — a signed,
	two's-complement interpretation of the 20 raw SHA1 bytes, not a plain hex
	dump. This exact quirk is why a naive hashlib.sha1().hexdigest() comparison
	against Mojang's serverId parameter fails for roughly half of all hashes."""
	n = int.from_bytes(digest_bytes, byteorder="big", signed=True)
	return format(n, "x") if n >= 0 else "-" + format(-n, "x")


def make_aes_cfb8(shared_secret: bytes):
	"""Returns (encryptor, decryptor). Key and IV are both the same 16-byte
	shared secret — that's correct, not a mistake; it's what the real
	protocol does."""
	encryptor = Cipher(algorithms.AES(shared_secret), modes.CFB8(shared_secret)).encryptor()
	decryptor = Cipher(algorithms.AES(shared_secret), modes.CFB8(shared_secret)).decryptor()
	return encryptor, decryptor
