-- Module: stock. Administrators register securities (preferably from the market-data provider's metadata) and
-- activate/deactivate them; rows are never hard-deleted because watchlists and price history reference them.
-- source records where the metadata came from: CATALOGUE (seed data), MANUAL (typed by an administrator) or the id
-- of the market-data provider that supplied it (e.g. twelve-data).
ALTER TABLE stocks ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'CATALOGUE';
