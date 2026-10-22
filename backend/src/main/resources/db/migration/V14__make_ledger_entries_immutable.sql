-- Prevent modification or deletion of historical ledger entries.
CREATE OR REPLACE FUNCTION prevent_ledger_entry_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Ledger entries are immutable; create a compensating entry instead.'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_ledger_entries_immutable
BEFORE UPDATE OR DELETE
ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_ledger_entry_mutation();