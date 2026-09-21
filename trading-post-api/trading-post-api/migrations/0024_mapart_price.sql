-- Optional asking price for a mapart, free text ("5 diamonds", "2 stacks of iron")
-- since sellers quote in different currencies. NULL = no price given.
ALTER TABLE maparts ADD COLUMN price TEXT;
