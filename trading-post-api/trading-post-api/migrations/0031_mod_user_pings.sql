-- Unique mod-user tracking, piggybacking on a call every mod install already
-- makes on every join (WatchlistJoinCheck -> GET /marketplace/notifications/
-- for-mc) — no mod update needed. usernameHash is HMAC-SHA256(mcUsername,
-- MOD_USER_HASH_SECRET); the raw username is never stored.
CREATE TABLE modUserPings (
	usernameHash TEXT PRIMARY KEY,
	firstSeenAt TEXT NOT NULL,
	lastSeenAt TEXT NOT NULL,
	pingCount INTEGER NOT NULL DEFAULT 1
);
