-- G10 file half: metadata can be marked MISSING when the object is gone.
-- This is not G6 restore and does not invent object bytes.
ALTER TABLE files.file_object DROP CONSTRAINT files_file_status;
ALTER TABLE files.file_object
    ADD CONSTRAINT files_file_status CHECK (status IN ('UPLOADED', 'DELETED', 'MISSING'));
