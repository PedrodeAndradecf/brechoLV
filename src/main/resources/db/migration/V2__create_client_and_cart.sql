














ALTER TABLE users ADD COLUMN phone varchar(20);







CREATE TABLE addresses (
    id          uuid         PRIMARY KEY,
    user_id     uuid         NOT NULL,

    
    
    
    zip_code    varchar(8)   NOT NULL,

    street      varchar(150) NOT NULL,
    
    number      varchar(20)  NOT NULL,
    complement  varchar(80),
    district    varchar(80)  NOT NULL,
    city        varchar(80)  NOT NULL,

    
    
    
    
    
    
    
    uf          varchar(2)   NOT NULL,

    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,

    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_addresses_uf CHECK (uf IN (
        'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
        'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'))
);



CREATE INDEX idx_addresses_user ON addresses (user_id);












CREATE TABLE carts (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_carts_user UNIQUE (user_id)
);









CREATE TABLE cart_items (
    id         uuid        PRIMARY KEY,
    cart_id    uuid        NOT NULL,
    product_id uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id)
        REFERENCES carts (id) ON DELETE CASCADE,
    
    
    
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_cart_items_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);


























ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'SELLER', 'CLIENT'));

ALTER TABLE products
    ADD CONSTRAINT ck_products_condition
    CHECK (product_condition IN ('NEW_WITH_TAG', 'LIKE_NEW', 'VERY_GOOD', 'GOOD'));
