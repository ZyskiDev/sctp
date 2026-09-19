-- Mapart uploaded by hand from the mapart management page (a verified account
-- picks/crops an image) rather than scanned in-game. Such pieces have a
-- synthetic negative leadMapId and no mapartParts rows, so scans never touch
-- them; uploadedByAccountId lets the uploader delete their own uploads.
ALTER TABLE maparts ADD COLUMN uploadedByAccountId TEXT;
