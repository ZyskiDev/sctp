-- Secret Santa (Firefly only): /secret-santa/ sign-up form, /secret-santa/reveal/
-- personal reveal pages and the separate /secret-santa/admin/ page (permission
-- "secretSanta"). See the "Secret Santa" section in worker.js.

-- One row per year. Dates are calendar days in Dutch time (Europe/Amsterdam):
-- sign-ups run from the start of signupOpenDate to the end of signupCloseDate,
-- gifts are due by the end of giftsDueDate and get opened on openDate.
CREATE TABLE santaEvents (
	id TEXT PRIMARY KEY,                 -- the year, e.g. '2026'
	world TEXT NOT NULL DEFAULT 'Firefly',
	signupOpenDate TEXT NOT NULL,
	signupCloseDate TEXT NOT NULL,
	giftsDueDate TEXT NOT NULL,
	openDate TEXT NOT NULL,
	dropOff TEXT,                        -- where gifts go, e.g. '/pw xmas'
	closedEarlyAt TEXT,                  -- an admin closed sign-ups before signupCloseDate (or generated pairs)
	pairsGeneratedAt TEXT,
	createdAt TEXT NOT NULL,
	updatedAt TEXT NOT NULL
);

-- One sign-up per player per year. wishlist is JSON:
--   [{kind:'rare', id, name, texture} | {kind:'mapart', id, name, imageHash}
--    | {kind:'item', baseItem, name, texture} | {kind:'text', text}]
-- excludes is a JSON list of lowercased Minecraft usernames (both directions).
CREATE TABLE santaEntries (
	id TEXT PRIMARY KEY,
	eventId TEXT NOT NULL,
	mcUsername TEXT NOT NULL,
	accountId TEXT,                      -- set when they signed up signed in (they can edit until sign-ups close)
	mcVerified INTEGER NOT NULL DEFAULT 0,
	discord TEXT NOT NULL,
	category TEXT NOT NULL,              -- 'high' (48 DB+) | 'low' (16 DB+) | 'both'
	excludes TEXT NOT NULL DEFAULT '[]',
	wishlist TEXT NOT NULL DEFAULT '[]',
	about TEXT,
	revealToken TEXT NOT NULL UNIQUE,    -- the personal reveal link; stays the same when pairs are regenerated
	addedBy TEXT,                        -- admin username when an admin added the entry
	createdAt TEXT NOT NULL,
	updatedAt TEXT NOT NULL
);
CREATE UNIQUE INDEX idx_santaEntries_name ON santaEntries(eventId, lower(mcUsername));
CREATE INDEX idx_santaEntries_account ON santaEntries(eventId, accountId);

-- Who makes a gift for whom, per value pool. Each pool is one loop, so everyone
-- in it gives exactly one gift and gets exactly one. Someone in 'both' has a
-- row in each pool. status: unsubmitted -> confirmed (we checked they still
-- want to play) -> submitted (we have the gift) -> placed (under the tree).
CREATE TABLE santaGifts (
	eventId TEXT NOT NULL,
	pool TEXT NOT NULL,                  -- 'high' | 'low'
	giverId TEXT NOT NULL,
	recipientId TEXT NOT NULL,
	status TEXT NOT NULL DEFAULT 'unsubmitted',
	statusUpdatedAt TEXT,
	PRIMARY KEY (eventId, pool, giverId)
);
CREATE INDEX idx_santaGifts_recipient ON santaGifts(eventId, recipientId);

INSERT INTO santaEvents (id, world, signupOpenDate, signupCloseDate, giftsDueDate, openDate, dropOff, createdAt, updatedAt)
VALUES ('2026', 'Firefly', '2026-11-01', '2026-11-30', '2026-12-20', '2026-12-25', '/pw xmas', '2026-10-12T00:00:00.000Z', '2026-10-12T00:00:00.000Z');
