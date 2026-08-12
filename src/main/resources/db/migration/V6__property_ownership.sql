-- V6: Give every property an owner.
--
-- Authorization needs an ownership edge in the schema. Without one there is no
-- way to express "the properties belonging to this user" as a query, and the
-- only alternative is loading a row and then checking it in application code --
-- which leaks data whenever someone forgets the check.

-- Added nullable first. A NOT NULL column cannot be added to a table that
-- already has rows without a default, and there is no sensible default owner:
-- picking one arbitrarily would silently reassign someone's property.
ALTER TABLE properties
    ADD COLUMN owner_id BIGINT;

-- Refuse to continue if existing rows cannot be assigned an owner. Deciding who
-- owns an existing property is a business decision, not something a migration
-- may guess, so this fails loudly with instructions rather than inventing an
-- answer or dropping the rows.
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count FROM properties WHERE owner_id IS NULL;

    IF orphan_count > 0 THEN
        RAISE EXCEPTION
            'Cannot apply V6: % propert(ies) have no owner. Assign one before migrating, e.g. UPDATE properties SET owner_id = <user id> WHERE owner_id IS NULL; then re-run. For a throwaway development database, recreate it with: docker compose down -v',
            orphan_count;
    END IF;
END $$;

ALTER TABLE properties
    ALTER COLUMN owner_id SET NOT NULL;

-- ON DELETE RESTRICT, not CASCADE. Deleting a user must not silently delete
-- their properties and, through them, the financial history attached to those
-- properties. Refusing the delete surfaces as a 409 and forces the caller to
-- deal with the dependent records explicitly -- the correct behaviour for
-- auditable records, where accidental deletion is far more costly than an
-- inconvenient error.
ALTER TABLE properties
    ADD CONSTRAINT fk_properties_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;

-- PostgreSQL does not index foreign keys automatically.
--
-- Supports: SELECT ... FROM properties WHERE owner_id = ?, which is now the
-- shape of EVERY property read, because listings are scoped to the caller.
-- It also backs the referential check PostgreSQL runs when a user is deleted;
-- without it, that check scans the whole properties table.
CREATE INDEX ix_properties_owner_id ON properties (owner_id);
