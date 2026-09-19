-- Mapart catalog + admin-generated verification links.
--
-- maparts: one row per (possibly multi-frame) piece of map art, keyed by the
-- world + the *leading* map's id (the frame whose map item carries the
-- title/artist as its custom name). width/height are the rectangle of item
-- frames it spans. The stitched full picture lives in R2 under
-- mapart/<id>.png (imageHash is only for cache-busting).
--
-- claimedByAccountId is set when a verified account owns the piece (auto for
-- verified accounts whose mcUsername matches the decoded artist, or via an
-- explicit claim). locked = title/artist were hand-edited, so a rescan must
-- not re-derive them from the map's custom name.
CREATE TABLE maparts (
	id TEXT PRIMARY KEY,
	slug TEXT NOT NULL UNIQUE,
	world TEXT NOT NULL,
	leadMapId INTEGER NOT NULL,
	rawName TEXT,
	title TEXT NOT NULL,
	artist TEXT,
	whereToBuy TEXT,
	notForSale INTEGER NOT NULL DEFAULT 0,
	category TEXT,
	width INTEGER NOT NULL DEFAULT 1,
	height INTEGER NOT NULL DEFAULT 1,
	imageHash TEXT,
	claimedByAccountId TEXT,
	claimedAt TEXT,
	autoClaimBlocked INTEGER NOT NULL DEFAULT 0,
	locked INTEGER NOT NULL DEFAULT 0,
	createdAt TEXT NOT NULL,
	updatedAt TEXT NOT NULL,
	lastSeen TEXT NOT NULL,
	UNIQUE (world, leadMapId)
);
CREATE INDEX idx_maparts_claimed ON maparts(claimedByAccountId);

-- Every map id that's part of a mapart (leading or not), so a later scan of
-- a single frame can be recognised as belonging to an existing piece.
CREATE TABLE mapartParts (
	world TEXT NOT NULL,
	mapId INTEGER NOT NULL,
	mapartId TEXT NOT NULL,
	PRIMARY KEY (world, mapId)
);
CREATE INDEX idx_mapartParts_mapart ON mapartParts(mapartId);

-- Every slug a mapart has ever had, so old /mapart/<title> links keep
-- working after a rename.
CREATE TABLE mapartSlugs (
	slug TEXT PRIMARY KEY,
	mapartId TEXT NOT NULL
);
CREATE INDEX idx_mapartSlugs_mapart ON mapartSlugs(mapartId);

-- Admin deleted this one on purpose — scans must not resurrect it.
CREATE TABLE mapartBlocked (
	world TEXT NOT NULL,
	leadMapId INTEGER NOT NULL,
	blockedAt TEXT NOT NULL,
	PRIMARY KEY (world, leadMapId)
);

-- Admin-generated, single-use, expiring links that verify an MC username
-- (either by registering a new account or linking an existing one).
CREATE TABLE verificationLinks (
	token TEXT PRIMARY KEY,
	mcUsername TEXT NOT NULL,
	createdBy TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL,
	usedAt TEXT,
	usedByAccountId TEXT
);

-- Forward-compatible hook: the real shop scanner doesn't record filled-map
-- ids yet, so this stays NULL today; once it does, listings can be matched
-- to maparts by exact map id (see handleGetListings / mergeMapartListings).
ALTER TABLE listings ADD COLUMN mapId INTEGER;
