































CREATE TABLE orders (
    id             uuid          PRIMARY KEY,
    user_id        uuid          NOT NULL,
    status         varchar(20)   NOT NULL,
    subtotal       numeric(10,2) NOT NULL,
    shipping_cost  numeric(10,2) NOT NULL,
    

    
    
    
    ship_recipient_name varchar(120) NOT NULL,
    ship_phone     varchar(20)   NOT NULL,
    ship_zip_code  varchar(8)    NOT NULL,
    ship_street    varchar(150)  NOT NULL,
    ship_number    varchar(20)   NOT NULL,
    ship_complement varchar(80),
    ship_district  varchar(80)   NOT NULL,
    ship_city      varchar(80)   NOT NULL,
    ship_uf        varchar(2)    NOT NULL,

    
    expires_at     timestamptz   NOT NULL,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,

    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    
    
    
    
    CONSTRAINT ck_orders_status CHECK (status IN ('AWAITING_PAYMENT', 'EXPIRED')),
    
    
    CONSTRAINT ck_orders_ship_uf CHECK (ship_uf IN (
        'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
        'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'))
);

CREATE INDEX idx_orders_user ON orders (user_id);








CREATE UNIQUE INDEX uk_orders_awaiting_per_user ON orders (user_id)
    WHERE status = 'AWAITING_PAYMENT';





CREATE TABLE order_items (
    id         uuid          PRIMARY KEY,
    order_id   uuid          NOT NULL,
    product_id uuid          NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    title      varchar(150)  NOT NULL,
    created_at timestamptz   NOT NULL,
    updated_at timestamptz   NOT NULL,

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_order_items_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);







CREATE TABLE payments (
    id             uuid        PRIMARY KEY,
    order_id       uuid        NOT NULL,
    txid           varchar(80),
    pix_copy_paste text,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,

    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    
    CONSTRAINT ck_payments_charge_pareada CHECK ((txid IS NULL) = (pix_copy_paste IS NULL))
);



CREATE UNIQUE INDEX uk_payments_txid ON payments (txid) WHERE txid IS NOT NULL;












