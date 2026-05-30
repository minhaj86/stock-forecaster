-- Module: identity. "Remember me": persistent sessions survive browser restarts (cookie with Max-Age);
-- non-persistent sessions use a browser-session cookie. The flag is carried across token rotations.
ALTER TABLE refresh_tokens
    ADD COLUMN persistent BOOLEAN NOT NULL DEFAULT TRUE;
