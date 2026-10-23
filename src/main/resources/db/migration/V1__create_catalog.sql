






CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    name          varchar(120) NOT NULL,
    email         varchar(180) NOT NULL,
    password_hash varchar(255) NOT NULL,
    
    
    role          varchar(20)  NOT NULL,
    deleted_at    timestamptz,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE categories (
    id         uuid         PRIMARY KEY,
    name       varchar(80)  NOT NULL,
    slug       varchar(100) NOT NULL,
    
    
    
    size_type  varchar(10)  NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_size_type CHECK (size_type IN ('ALPHA', 'NUMERIC'))
);

CREATE TABLE products (
    id                uuid          PRIMARY KEY,
    seller_id         uuid          NOT NULL,
    category_id       uuid          NOT NULL,
    title             varchar(150)  NOT NULL,
    description       text          NOT NULL,
    brand             varchar(80)   NOT NULL,
    
    
    
    size              varchar(10)   NOT NULL,
    
    size_label        varchar(30),
    color             varchar(40)   NOT NULL,
    product_condition varchar(20)   NOT NULL,
    price             numeric(10,2) NOT NULL,
    
    
    
    deleted_at        timestamptz,
    version           bigint,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT fk_products_seller   FOREIGN KEY (seller_id)   REFERENCES users (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

CREATE TABLE product_photos (
    id          uuid         PRIMARY KEY,
    product_id  uuid         NOT NULL,
    
    
    storage_key varchar(300) NOT NULL,
    
    
    
    cover       boolean      NOT NULL DEFAULT false,
    position    integer      NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT fk_product_photos_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE CASCADE
);



CREATE UNIQUE INDEX uk_product_photos_cover
    ON product_photos (product_id) WHERE cover IS TRUE;


CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_seller   ON products (seller_id);
CREATE INDEX idx_products_brand    ON products (brand);
CREATE INDEX idx_products_color    ON products (color);
CREATE INDEX idx_products_size     ON products (size);



CREATE INDEX idx_products_showcase
    ON products (price) WHERE deleted_at IS NULL;
