CREATE TABLE users
(
    id            SERIAL PRIMARY KEY,
    email         VARCHAR(255),
    accountNumber VARCHAR(20)
);

CREATE TABLE instruments
(
    id     SERIAL PRIMARY KEY,
    ticker VARCHAR(10),
    name   VARCHAR(255),
    type   VARCHAR(10)
);

CREATE TABLE orders
(
    id           SERIAL PRIMARY KEY,
    instrumentId INT REFERENCES instruments (id),
    userId       INT REFERENCES users (id),
    size         INT,
    price        NUMERIC(10, 2),
    type         VARCHAR(10),
    side         VARCHAR(10),
    status       VARCHAR(20),
    datetime     TIMESTAMP
);

CREATE TABLE marketdata
(
    id            SERIAL PRIMARY KEY,
    instrumentId  INT REFERENCES instruments (id),
    high          NUMERIC(10, 2),
    low           NUMERIC(10, 2),
    open          NUMERIC(10, 2),
    close         NUMERIC(10, 2),
    previousClose NUMERIC(10, 2),
    date          DATE
);
