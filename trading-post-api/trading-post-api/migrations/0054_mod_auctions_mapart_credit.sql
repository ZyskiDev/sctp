-- Mapart: credit for the original art a piece is based on (set by its owner or
-- a mapart manager), separate from `artist` (who built the map).
ALTER TABLE maparts ADD COLUMN originalArtist TEXT;

-- Auctions hosted in-game with Shop Logger (see "in-game auction hosting" in
-- worker.js). The host (auctions.hostName) gets access by their Minecraft
-- name; anyone else adds an auction with its code from the auction admin page.
ALTER TABLE auctions ADD COLUMN accessCode TEXT;
UPDATE auctions SET accessCode = upper(hex(randomblob(4))) WHERE accessCode IS NULL;
CREATE UNIQUE INDEX idx_auctions_accessCode ON auctions(accessCode);

-- "Deleted" auctions only leave the admin menu: they're set to finished and
-- their entries and results stay (price estimates and My Auctions use them).
ALTER TABLE auctions ADD COLUMN deletedAt TEXT;

-- Auctions a Minecraft player added in-game with a code.
CREATE TABLE auctionModAccess (
	auctionId TEXT NOT NULL,
	mcKey TEXT NOT NULL,                 -- lowercased Minecraft username
	addedAt TEXT NOT NULL,
	PRIMARY KEY (auctionId, mcKey)
);

-- Sign-ins from the mod for auction hosting: trusted by Minecraft name, no
-- sctp.nl account needed.
CREATE TABLE modSessions (
	token TEXT PRIMARY KEY,
	mcUsername TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL
);
