-- Every custom name found on the frames of a (merged) mapart, as a JSON
-- array, lead first. Title and artist are often on different maps of one
-- piece, so detection needs all of them — and storing them lets the
-- "re-run artist detection" admin action re-derive without a rescan.
ALTER TABLE maparts ADD COLUMN allNames TEXT;
