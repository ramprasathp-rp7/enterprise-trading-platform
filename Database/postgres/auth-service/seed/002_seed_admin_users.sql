-- Seed demo admin user
-- This is an operations-only seed script that runs once during deployment.
-- Admins are created here only, never through the public registration endpoint.
--
-- Demo Admin Credentials:
--   Username: demo.admin
--   Password: Admin@123456 (change in production)
--   Account: 0 (Operator account)
--   Roles: ADMIN
--
-- Password Hash Details:
--   Algorithm: argon2id (Argon2id v=19, m=19456, t=2, p=1), matching PasswordService.hashPassword
--   Pre-hashed for demo purposes. Regenerate for production:
--   node -e "const argon2 = require('argon2'); argon2.hash('Admin@123456').then(console.log)"
--
INSERT INTO auth.users (id, username, password_hash, account_id, roles, created_on, last_updated)
VALUES (
    'f47ac10b-58cc-4372-a567-0e02b2c3d479'::uuid,
    'demo.admin',
    -- Argon2id hash of 'Admin@123456'
    '$argon2id$v=19$m=19456,p=1,t=2$c2ZxubGrZ8k4f+hKbJF+7Q$NNYk6c2M1a1TOWu0GQbp0daXw0cb6EFaamevzNLIuEQ',
    0,
    ARRAY['ADMIN'],
    TIMESTAMP '2026-01-01 00:00:00+00',
    TIMESTAMP '2026-01-01 00:00:00+00'
)
ON CONFLICT (username) DO NOTHING;

-- Optional: Seed additional demo users for testing
INSERT INTO auth.users (username, password_hash, account_id, roles)
VALUES (
    'demo.customer',
    -- Argon2id hash of 'Customer@123456'
    '$argon2id$v=19$m=19456,p=1,t=2$qJRj+eYz2mVA4XcRYtJt2g$dtPxgMKPReUkvHHVgJeF1xOwTgkBNKj4XRSUxYnnYx4',
    2,
    ARRAY['CUSTOMER']
)
ON CONFLICT (username) DO NOTHING;
