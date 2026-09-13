"""Reports a completed verification back to the SCTP Worker, which is the
only thing that actually knows about pending registration codes."""

import requests

import config


def report_verified(code: str, mc_username: str, mc_uuid):
	try:
		resp = requests.post(
			config.WORKER_API_BASE + "/account/register/verify-callback",
			json={"code": code, "mcUsername": mc_username, "mcUuid": mc_uuid},
			headers={"Authorization": "Bearer " + config.WORKER_SHARED_SECRET},
			timeout=10,
		)
		return resp.status_code == 200
	except requests.RequestException:
		return False


def find_pending_code_for_username(mc_username: str):
	"""Bedrock path only — there's no per-connection code to read (RakNet has
	no equivalent of Java's client-typed server-address field), so instead we
	ask the Worker which pending registration currently claims this exact
	(already Xbox-authenticated, via Geyser) username. See bedrock_bridge.py."""
	try:
		resp = requests.get(
			config.WORKER_API_BASE + "/account/register/find-pending",
			params={"mcUsername": mc_username},
			headers={"Authorization": "Bearer " + config.WORKER_SHARED_SECRET},
			timeout=10,
		)
		if resp.status_code != 200:
			return None
		data = resp.json()
		return data.get("code")
	except requests.RequestException:
		return None
