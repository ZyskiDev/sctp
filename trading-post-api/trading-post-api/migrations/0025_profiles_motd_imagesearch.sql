-- Commission info shown on an artist's profile (entered on the mapart
-- management page): whether they take commissions, free-text prices/details
-- and a Discord contact.
ALTER TABLE admins ADD COLUMN commissionOpen INTEGER NOT NULL DEFAULT 0;
ALTER TABLE admins ADD COLUMN commissionInfo TEXT;
ALTER TABLE admins ADD COLUMN commissionDiscord TEXT;

-- "Was this a commission?": when set, artist = who built it and
-- commissionedBy = who it was built for.
ALTER TABLE maparts ADD COLUMN commissioned INTEGER NOT NULL DEFAULT 0;
ALTER TABLE maparts ADD COLUMN commissionedBy TEXT;

-- 256-bit difference hash of the stitched picture (64 hex chars) for
-- reverse image search and near-duplicate detection. NULL until indexed.
ALTER TABLE maparts ADD COLUMN phash TEXT;

-- Small key/value store for site-wide state (mapart of the day, ...).
CREATE TABLE IF NOT EXISTS siteSettings (
	key TEXT PRIMARY KEY,
	value TEXT NOT NULL
);
