CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS keyword_raw (
    id              UUID DEFAULT uuid_generate_v4(),
    keyword         TEXT NOT NULL UNIQUE,
    keyword_vector  tsvector,
    PRIMARY KEY (id)
);


CREATE OR REPLACE FUNCTION create_keyword(
    keyword_text TEXT
) RETURNS VOID AS
$$
BEGIN
    INSERT INTO keyword_raw (keyword, keyword_vector) VALUES (
        keyword_text, to_tsvector('english', keyword_text)
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
    RETURN QUERY (SELECT
        keyword_raw.keyword,
        ts_rank_cd(keyword_vector, query, 2 | 32) :: NUMERIC(11,10)
    FROM keyword_raw, plainto_tsquery('english', search_keyword) AS query
    WHERE query @@ keyword_vector
    ORDER BY rank DESC
    LIMIT 1);
END;
$$ LANGUAGE plpgsql;
