-- Additional instruments, bringing the catalogue to 25 symbols: the most Fauxnance
-- returns in one batched quote request (FauxnanceQuoteClient.MAX_BATCH_SIZE).
-- ON CONFLICT keeps this file safe to re-run against a database seeded before it existed.

INSERT INTO instruments (symbol, name, asset_class, currency, tradable, created_on) VALUES
    ('MSFT',         'Microsoft Corporation',          'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('GOOGL',        'Alphabet Inc.',                  'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('AMZN',         'Amazon.com Inc.',                'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('NVDA',         'NVIDIA Corporation',             'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('TSLA',         'Tesla Inc.',                     'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('META',         'Meta Platforms Inc.',            'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('JPM',          'JPMorgan Chase & Co.',           'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('NFLX',         'Netflix Inc.',                   'EQUITY', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('TCS.NS',       'Tata Consultancy Services',      'EQUITY', 'INR', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('HDFCBANK.NS',  'HDFC Bank Limited',              'EQUITY', 'INR', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('ICICIBANK.NS', 'ICICI Bank Limited',             'EQUITY', 'INR', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('WIPRO.NS',     'Wipro Limited',                  'EQUITY', 'INR', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('FX:GBPUSD',    'British Pound / US Dollar',      'FX',     'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('FX:USDJPY',    'US Dollar / Japanese Yen',       'FX',     'JPY', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('FX:USDINR',    'US Dollar / Indian Rupee',       'FX',     'INR', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('X:ETH-USD',    'Ethereum / US Dollar',           'CRYPTO', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('X:SOL-USD',    'Solana / US Dollar',             'CRYPTO', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00'),
    ('X:XRP-USD',    'XRP / US Dollar',                'CRYPTO', 'USD', TRUE, TIMESTAMP '2026-01-01 00:00:00+00')
ON CONFLICT (symbol) DO NOTHING;
