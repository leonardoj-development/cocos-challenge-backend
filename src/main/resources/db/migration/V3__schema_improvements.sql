-- V3: mejoras de integridad e indices sobre el esquema base (V1) y los datos de referencia (V2).
--
-- Resumen de decisiones:
--  * Integridad: columnas obligatorias (NOT NULL), CHECK de dominio para side/status/type,
--    size > 0, price obligatorio solo para ordenes LIMIT, y UNIQUE en email, accountNumber,
--    ticker y (instrumentId, date) de marketdata.
--  * Precio de ordenes MARKET: no se exige NULL porque los datos de V2 (CASH_IN y MARKET)
--    traen precio; solo se exige para LIMIT.
--  * Tipos: NUMERIC(10,2) -> NUMERIC(18,2) en precios para evitar desbordes con montos altos en ARS.
--  * Sin renombrar columnas ni cambiar TIMESTAMP/SERIAL: las entidades JPA mapean userid,
--    instrumentid, accountnumber, previousclose y usan LocalDateTime. Hacerlo romperia el codigo.
--  * Indices:
--      - orders parcial (WHERE status = 'FILLED') con INCLUDE para calcular portfolio y cash
--        sin ir al heap.
--      - orders (userId, status) para listar/cancelar ordenes NEW.
--      - orders (instrumentId) para soportar la FK.
--      - marketdata (instrumentId, date DESC) unico: ultimo precio por instrumento.
--      - GIN trigram (pg_trgm) en ticker y name para busquedas ILIKE '%texto%'.
--  * Requiere permisos para CREATE EXTENSION pg_trgm.
--  * Si la migracion falla en un entorno con datos propios, revisar duplicados/NULLs previos
--    antes de aplicar los UNIQUE y NOT NULL.

-- Integridad: columnas obligatorias
ALTER TABLE users
    ALTER COLUMN email SET NOT NULL,
ALTER
COLUMN accountNumber SET NOT NULL;

ALTER TABLE instruments
    ALTER COLUMN ticker SET NOT NULL,
ALTER
COLUMN name SET NOT NULL,
    ALTER
COLUMN type SET NOT NULL;

ALTER TABLE orders
    ALTER COLUMN instrumentId SET NOT NULL,
ALTER
COLUMN userId SET NOT NULL,
    ALTER
COLUMN size SET NOT NULL,
    ALTER
COLUMN type SET NOT NULL,
    ALTER
COLUMN side SET NOT NULL,
    ALTER
COLUMN status SET NOT NULL,
    ALTER
COLUMN datetime SET NOT NULL,
    ALTER
COLUMN price TYPE NUMERIC(18, 2);

ALTER TABLE marketdata
    ALTER COLUMN instrumentId SET NOT NULL,
ALTER
COLUMN date SET NOT NULL,
    ALTER
COLUMN close SET NOT NULL,
    ALTER
COLUMN previousClose SET NOT NULL,
    ALTER
COLUMN high TYPE NUMERIC(18, 2),
    ALTER
COLUMN low TYPE NUMERIC(18, 2),
    ALTER
COLUMN open TYPE NUMERIC(18, 2),
    ALTER
COLUMN close TYPE NUMERIC(18, 2),
    ALTER
COLUMN previousClose TYPE NUMERIC(18, 2);

-- Integridad: dominios y reglas
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL', 'CASH_IN', 'CASH_OUT')),
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    ADD CONSTRAINT chk_orders_type CHECK (type IN ('MARKET', 'LIMIT')),
    ADD CONSTRAINT chk_orders_size CHECK (size > 0),
    ADD CONSTRAINT chk_orders_price CHECK (price IS NULL OR price >= 0),
    ADD CONSTRAINT chk_orders_limit_price CHECK (type <> 'LIMIT' OR price IS NOT NULL);

-- Unicidad
ALTER TABLE users
    ADD CONSTRAINT uq_users_email UNIQUE (email),
    ADD CONSTRAINT uq_users_account_number UNIQUE (accountNumber);

ALTER TABLE instruments
    ADD CONSTRAINT uq_instruments_ticker UNIQUE (ticker);

-- Un registro de marketdata por instrumento y fecha; sirve tambien para obtener el ultimo precio
CREATE UNIQUE INDEX uq_marketdata_instrument_date ON marketdata (instrumentId, date DESC);

-- Portfolio y cash: solo ordenes FILLED, con columnas cubiertas
CREATE INDEX idx_orders_user_instrument_filled
    ON orders (userId, instrumentId) INCLUDE (side, size, price)
    WHERE status = 'FILLED';

-- Listado y cancelacion de ordenes por usuario/estado
CREATE INDEX idx_orders_user_status ON orders (userId, status);

-- Soporte de la FK orders -> instruments
CREATE INDEX idx_orders_instrument ON orders (instrumentId);

-- Busqueda por ticker y/o nombre (ILIKE '%texto%')
CREATE
EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_instruments_ticker_trgm ON instruments USING gin (ticker gin_trgm_ops);
CREATE INDEX idx_instruments_name_trgm ON instruments USING gin (name gin_trgm_ops);
