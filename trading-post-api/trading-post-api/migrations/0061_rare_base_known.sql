-- Last known base item of every rare (worker.js handleRareBaseItems), so a rare that
-- nobody has listed right now keeps its place in the library's Base item filter.
CREATE TABLE rareBaseKnown (
	rareId TEXT PRIMARY KEY,
	baseItem TEXT NOT NULL,
	firstSeenAt TEXT NOT NULL,
	seenAt TEXT NOT NULL
);
CREATE INDEX idx_rareBaseKnown_base ON rareBaseKnown (baseItem);
