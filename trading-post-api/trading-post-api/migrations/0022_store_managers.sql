-- Lets an account manage ALL manual listings of another seller name through
-- "Manage my store" (e.g. a shared plot warp run by a different player).
-- The listings keep showing the original seller.
CREATE TABLE storeManagers (
	sellerKey TEXT NOT NULL,      -- lowercase seller name (bare, no leading dot)
	sellerName TEXT NOT NULL,     -- display casing, used when adding new listings for that seller
	accountId TEXT NOT NULL,
	grantedBy TEXT NOT NULL,
	grantedAt TEXT NOT NULL,
	PRIMARY KEY (sellerKey, accountId)
);
CREATE INDEX idx_storeManagers_account ON storeManagers(accountId);

-- pwJuls' manual listings are managed by tinatina5252.
INSERT INTO storeManagers (sellerKey, sellerName, accountId, grantedBy, grantedAt)
SELECT 'pwjuls', 'pwJuls', id, 'maxolotled', '2026-09-19T00:00:00Z' FROM admins WHERE lower(username) = 'tinatina5252';
