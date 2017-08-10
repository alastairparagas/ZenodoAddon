CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS keyword_raw (
    id              UUID DEFAULT uuid_generate_v4(),
    keyword         TEXT NOT NULL UNIQUE,
    keyword_vector  tsvector,
    PRIMARY KEY (id)
);


CREATE OR REPLACE FUNCTION create_keyword(
    keyword TEXT
) RETURNS VOID AS
$$
BEGIN
    INSERT INTO keyword_raw(keyword, keyword_vector) VALUES(
        keyword, to_tsvector('english', keyword)
    );
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION search_keyword_matches(
    TEXT
) RETURNS TABLE(keyword TEXT, rank NUMERIC(11,10)) AS
$$
DECLARE
    search_keyword ALIAS FOR $1;
BEGIN
    RETURN QUERY SELECT
        keyword AS keyword,
        ts_rank_cd(text_vector, query, 2 | 32) AS rank
    FROM keyword_raw, plainto_tsquery('english', search_keyword) AS query
    WHERE query @@ text_vector
    ORDER BY rank DESC
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;
