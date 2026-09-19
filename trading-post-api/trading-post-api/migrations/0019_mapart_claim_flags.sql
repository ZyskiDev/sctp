-- The "verified by artist" check on a mapart should mean the artist actually
-- engaged, not just that a name in the title matched a verified account:
--   claimedManually = 1 when the owner clicked Claim (or an admin assigned it)
--   ownerEdited     = 1 when the owner saved any edit
-- (category / where-to-buy / not-for-sale already being set also counts — see
-- mapartPublic in worker.js — which covers pieces edited before this existed.)
ALTER TABLE maparts ADD COLUMN claimedManually INTEGER NOT NULL DEFAULT 0;
ALTER TABLE maparts ADD COLUMN ownerEdited INTEGER NOT NULL DEFAULT 0;
