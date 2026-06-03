\set ON_ERROR_STOP on

CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    country_code CHAR(2) NOT NULL,
    device_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    marketing_channel TEXT NOT NULL
);

CREATE TABLE app_categories (
    category_id INTEGER PRIMARY KEY,
    category_name TEXT NOT NULL UNIQUE
);

CREATE TABLE apps (
    app_id BIGINT PRIMARY KEY,
    category_id INTEGER NOT NULL REFERENCES app_categories(category_id),
    developer_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    price NUMERIC(8, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL
);

CREATE TABLE app_downloads (
    download_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    app_id BIGINT NOT NULL REFERENCES apps(app_id),
    downloaded_at TIMESTAMPTZ NOT NULL,
    country_code CHAR(2) NOT NULL,
    device_type TEXT NOT NULL,
    app_version TEXT NOT NULL
);

CREATE TABLE app_reviews (
    review_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    app_id BIGINT NOT NULL REFERENCES apps(app_id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_purchases (
    purchase_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    app_id BIGINT NOT NULL REFERENCES apps(app_id),
    purchased_at TIMESTAMPTZ NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_status TEXT NOT NULL
);

CREATE TABLE subscriptions (
    subscription_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    app_id BIGINT NOT NULL REFERENCES apps(app_id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    plan_type TEXT NOT NULL,
    status TEXT NOT NULL
);

CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_apps_category_id ON apps(category_id);
CREATE INDEX idx_downloads_downloaded_at ON app_downloads(downloaded_at);
CREATE INDEX idx_downloads_user_id ON app_downloads(user_id);
CREATE INDEX idx_downloads_app_id ON app_downloads(app_id);
CREATE INDEX idx_reviews_app_id_reviewed_at ON app_reviews(app_id, reviewed_at);
CREATE INDEX idx_purchases_purchased_at ON app_purchases(purchased_at);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
