-- Self-service account registration via the standalone MC verification
-- server (see /verify at the repo root) — a pending row exists between
-- "player started registering" and "they finished picking a password".
-- mcUsername/mcUuid start NULL and only get filled in once the verify
-- server actually confirms a real Minecraft login (or, for Bedrock, once
-- worker_client.find_pending_code_for_username matches an incoming
-- Xbox-authenticated connection to this code).
CREATE TABLE pendingRegistrations (
	code TEXT PRIMARY KEY,
	claimedMcUsername TEXT NOT NULL,       -- whatever they typed in step 1, before verification
	mcUsername TEXT,                       -- filled in once verified (authoritative, from Mojang or the Bedrock bridge)
	mcUuid TEXT,
	verified INTEGER NOT NULL DEFAULT 0,
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL
);
CREATE INDEX idx_pendingRegistrations_mcUsername ON pendingRegistrations(mcUsername);
