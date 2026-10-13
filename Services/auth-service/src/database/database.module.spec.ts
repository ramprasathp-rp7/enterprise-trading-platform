import { mkdtempSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { databaseSsl } from './database.module';

describe('databaseSsl', () => {
  test('connects without TLS when PGSSLMODE is unset or disable', () => {
    expect(databaseSsl({})).toBe(false);
    expect(databaseSsl({ PGSSLMODE: 'disable' })).toBe(false);
  });

  test('no-verify encrypts without checking the certificate', () => {
    expect(databaseSsl({ PGSSLMODE: 'no-verify' })).toEqual({ rejectUnauthorized: false });
  });

  test('verify-full checks the certificate against PGSSLROOTCERT', () => {
    const dir = mkdtempSync(join(tmpdir(), 'db-ssl-'));
    const caPath = join(dir, 'ca.pem');
    writeFileSync(caPath, 'TEST CA');

    expect(databaseSsl({ PGSSLMODE: 'verify-full', PGSSLROOTCERT: caPath })).toEqual({ ca: 'TEST CA', rejectUnauthorized: true });
    rmSync(dir, { recursive: true, force: true });
  });
});
