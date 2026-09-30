ALTER TABLE orders

DROP CONSTRAINT ck_orders_executed_on_iff_terminal;



ALTER TABLE orders

ADD CONSTRAINT ck_orders_executed_on_iff_terminal

CHECK (

    (

        status IN ('FILLED', 'REJECTED')

        AND executed_on IS NOT NULL

    )

    OR

    (

        status IN ('NEW', 'CANCELLED')

        AND executed_on IS NULL

    )

);



ALTER TABLE orders

DROP CONSTRAINT ck_orders_rejected_reason_iff_rejected;



ALTER TABLE orders

ADD CONSTRAINT ck_orders_rejected_reason_iff_rejected

CHECK (

    (

        status = 'REJECTED'

        AND rejected_reason IS NOT NULL

    )

    OR

    (

        status IN ('NEW', 'FILLED', 'CANCELLED')

        AND rejected_reason IS NULL

    )

);