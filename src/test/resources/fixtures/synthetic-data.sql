-- SYNTHETIC TEST DATA: not derived from database.sql nor from real accounts.
INSERT INTO users (id, email, accountNumber)
VALUES (1, 'synthetic@example.test', 'SYNTH-0001');

INSERT INTO instruments (id, ticker, name, type)
VALUES (1, 'ARS', 'Peso argentino', 'MONEDA'),
       (2, 'SYNA', 'Synthetic Asset A', 'ACCIONES'),
       (3, 'SYNB', 'Synthetic Asset B', 'ACCIONES');

INSERT INTO orders (id, instrumentId, userId, side, size, price, type, status, datetime)
VALUES (1, 1, 1, 'CASH_IN', 10000, NULL, NULL, 'FILLED', '2026-01-01T10:00:00Z'),
       (2, 2, 1, 'BUY', 10, 100, 'MARKET', 'FILLED', '2026-01-02T10:00:00Z'),
       (3, 3, 1, 'BUY', 5, 50, 'LIMIT', 'NEW', '2026-01-03T10:00:00Z');

INSERT INTO marketdata (id, instrumentId, high, low, open, close, previousClose, date)
VALUES (1, 2, 110, 95, 100, 105, 100, '2026-01-02'),
       (2, 3, 55, 48, 50, 52, 50, '2026-01-02');

SELECT setval(pg_get_serial_sequence('users', 'id'), 1);
SELECT setval(pg_get_serial_sequence('instruments', 'id'), 3);
SELECT setval(pg_get_serial_sequence('orders', 'id'), 3);
SELECT setval(pg_get_serial_sequence('marketdata', 'id'), 2);
