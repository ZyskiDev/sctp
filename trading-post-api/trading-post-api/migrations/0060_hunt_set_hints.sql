-- Personal hint overrides for a puzzle set (JSON array of up to 3; null keeps the shared hint)
ALTER TABLE huntSetChapters ADD COLUMN hints TEXT;
