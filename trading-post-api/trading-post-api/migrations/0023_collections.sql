-- Personal collections: an account ticks off rare items (from data/rare-items.json)
-- and mapart (from the maparts table) it owns, separately per world.
-- itemId is the rare-items.json id ("rare-1-hour-fly-token") for kind 'rare'
-- and maparts.id for kind 'mapart'. No FK on purpose: a mapart that gets
-- deleted just stops counting (the client ignores ids it can't find).
CREATE TABLE collectionItems (
	accountId TEXT NOT NULL,
	kind TEXT NOT NULL,          -- 'rare' | 'mapart'
	itemId TEXT NOT NULL,
	world TEXT NOT NULL,         -- 'Firefly' | 'Honeybee'
	addedAt TEXT NOT NULL,
	PRIMARY KEY (accountId, kind, itemId, world)
);
CREATE INDEX idx_collectionItems_item ON collectionItems(kind, itemId);

-- Collections are public by default (/collection/<username>); flip to hide.
ALTER TABLE admins ADD COLUMN collectionPrivate INTEGER NOT NULL DEFAULT 0;
