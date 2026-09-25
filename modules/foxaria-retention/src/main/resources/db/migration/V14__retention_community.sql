CREATE TABLE IF NOT EXISTS fx_vote_claims (
    player_uuid VARCHAR(36) NOT NULL,
    site_key VARCHAR(64) NOT NULL,
    vote_date VARCHAR(16) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, site_key, vote_date)
);

CREATE TABLE IF NOT EXISTS fx_referrals (
    referred_uuid VARCHAR(36) PRIMARY KEY,
    referrer_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL
);
