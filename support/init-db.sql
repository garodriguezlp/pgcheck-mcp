CREATE SCHEMA IF NOT EXISTS store;

CREATE TABLE IF NOT EXISTS store.customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS store.orders (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES store.customers(id),
    total DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO store.customers (name, email) VALUES
('John Doe', 'john.doe@example.com'),
('Jane Smith', 'jane.smith@example.com'),
('Alice Brown', 'alice.brown@example.com');

INSERT INTO store.orders (customer_id, total, status) VALUES
(1, 150.50, 'COMPLETED'),
(1, 45.00, 'PENDING'),
(2, 99.99, 'COMPLETED'),
(3, 250.00, 'SHIPPED');
