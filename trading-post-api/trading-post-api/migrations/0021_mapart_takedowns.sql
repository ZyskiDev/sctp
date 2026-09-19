-- Owner-requested takedowns of claimed mapart. The owner files a request, a
-- head admin approves or denies it. The row keeps a snapshot of the piece
-- (it's deleted on approval). On approval the piece's lead map, every one of
-- its map ids and its exact picture are blocked so it can't come back through
-- a re-scan or a portal upload.
CREATE TABLE mapartTakedowns (
	id TEXT PRIMARY KEY,
	mapartId TEXT NOT NULL,
	accountId TEXT NOT NULL,
	title TEXT NOT NULL,
	artist TEXT,
	world TEXT NOT NULL,
	leadMapId INTEGER NOT NULL,
	imageHash TEXT,
	reason TEXT,
	status TEXT NOT NULL DEFAULT 'pending', -- pending | approved | denied | cancelled
	createdAt TEXT NOT NULL,
	resolvedAt TEXT,
	resolvedBy TEXT
);
CREATE INDEX idx_mapartTakedowns_status ON mapartTakedowns(status);
CREATE INDEX idx_mapartTakedowns_mapart ON mapartTakedowns(mapartId);
CREATE INDEX idx_mapartTakedowns_account ON mapartTakedowns(accountId);

CREATE TABLE mapartBlockedParts (
	world TEXT NOT NULL,
	mapId INTEGER NOT NULL,
	PRIMARY KEY (world, mapId)
);

CREATE TABLE mapartBlockedImages (
	imageHash TEXT PRIMARY KEY,
	blockedAt TEXT NOT NULL
);
