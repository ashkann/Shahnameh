CREATE TABLE google_ocid (
    id SERIAL PRIMARY KEY,
    email text NOT NULL UNIQUE,
    picture text NOT NULL,
    name text NOT NULL
);