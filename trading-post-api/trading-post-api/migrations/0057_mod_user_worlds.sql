-- Firefly / Honeybee split of mod users, without any mod change: the join ping
-- (modUserPings) and shop-scan uploads come from the same connection, so each
-- keeps an HMAC of the connection's address (never the address itself), and a
-- mod user's world is the world their connection uploads scans for.
ALTER TABLE modUserPings ADD COLUMN lastIpHash TEXT;
CREATE TABLE uploadIpWorlds (
	ipHash TEXT NOT NULL,
	world TEXT NOT NULL,
	lastSeenAt TEXT NOT NULL,
	PRIMARY KEY (ipHash, world)
);
CREATE INDEX idx_modUserPings_ip ON modUserPings(lastIpHash);
DROP TABLE IF EXISTS modUserWorlds;
