-- Adds a stable, opaque id to every listings row so it can be linked/shared
-- directly (see the site's Share button) without exposing the rowKey (which
-- encodes seller/item/position and changes shape whenever that key format
-- changes — see migrate-rowkeys-bulk-bundled.sql for a past example of that).
-- Existing rows are backfilled in the same migration so old listings are
-- shareable immediately too, not just ones uploaded after this deploys.
ALTER TABLE listings ADD COLUMN id TEXT;
UPDATE listings SET id = lower(hex(randomblob(16))) WHERE id IS NULL;
CREATE UNIQUE INDEX idx_listings_id ON listings(id);
