CREATE TABLE favorite
(
    member_id    uuid                        NOT NULL,
    restaurant_id uuid                       NOT NULL,
    favorited_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_favorite PRIMARY KEY (member_id, restaurant_id),
    CONSTRAINT fk_favorite__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_favorite__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE recent_restaurant_view
(
    member_id      uuid                        NOT NULL,
    restaurant_id  uuid                        NOT NULL,
    last_viewed_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_recent_restaurant_view PRIMARY KEY (member_id, restaurant_id),
    CONSTRAINT fk_recent_restaurant_view__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recent_restaurant_view__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX ix_favorite__member_favorited
    ON favorite (member_id, favorited_at DESC, restaurant_id);

CREATE INDEX ix_recent_restaurant_view__member_viewed
    ON recent_restaurant_view (member_id, last_viewed_at DESC, restaurant_id);

CREATE INDEX ix_recent_restaurant_view__cleanup_viewed
    ON recent_restaurant_view (last_viewed_at);
