
CREATE TABLE IF NOT EXISTS public.contest_join_meta (
    contest_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    join_meta_data VARCHAR(255),
    position_id INTEGER,
    PRIMARY KEY (position_id));
