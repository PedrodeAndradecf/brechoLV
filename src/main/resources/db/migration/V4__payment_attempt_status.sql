



















ALTER TABLE payments ADD COLUMN status    varchar(30);
ALTER TABLE payments ADD COLUMN amount    numeric(10,2);
ALTER TABLE payments ADD COLUMN order_nsu varchar(64);





UPDATE payments p
   SET status    = CASE WHEN p.pix_copy_paste IS NOT NULL THEN 'OPEN'
                        ELSE 'PENDING_CONFIRMATION' END,
       amount    = o.subtotal + o.shipping_cost,
       order_nsu = p.order_id::text
  FROM orders o
 WHERE o.id = p.order_id;

ALTER TABLE payments ALTER COLUMN status    SET NOT NULL;
ALTER TABLE payments ALTER COLUMN amount    SET NOT NULL;
ALTER TABLE payments ALTER COLUMN order_nsu SET NOT NULL;




ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING_CONFIRMATION', 'OPEN', 'NOT_CREATED'));



ALTER TABLE payments ADD CONSTRAINT ck_payments_status_artefato
    CHECK ((status = 'OPEN') = (pix_copy_paste IS NOT NULL));






ALTER TABLE payments DROP CONSTRAINT uk_payments_order;
CREATE UNIQUE INDEX uk_payments_order ON payments (order_id)
    WHERE status <> 'NOT_CREATED';




ALTER TABLE payments ADD COLUMN reviewed_by    uuid;
ALTER TABLE payments ADD COLUMN reviewed_at    timestamptz;
ALTER TABLE payments ADD COLUMN review_outcome varchar(30);

ALTER TABLE payments ADD CONSTRAINT fk_payments_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES users (id);
ALTER TABLE payments ADD CONSTRAINT ck_payments_review_completo
    CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)
       AND (reviewed_at IS NULL) = (review_outcome IS NULL));
ALTER TABLE payments ADD CONSTRAINT ck_payments_review_outcome
    CHECK (review_outcome IN ('NOT_CREATED', 'OPEN'));






ALTER TABLE payments DROP CONSTRAINT fk_payments_order;
ALTER TABLE payments ADD CONSTRAINT fk_payments_order
    FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT;









