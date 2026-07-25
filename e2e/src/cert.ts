import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export interface Certificate {
  cert: string;
  key: string;
}

export function generateCert(): Certificate {
  const dir = mkdtempSync(join(tmpdir(), 'sauti-e2e-cert-'));
  const keyPath = join(dir, 'key.pem');
  const certPath = join(dir, 'cert.pem');
  execFileSync(
    'openssl',
    [
      'req',
      '-x509',
      '-newkey',
      'rsa:2048',
      '-keyout',
      keyPath,
      '-out',
      certPath,
      '-days',
      '1',
      '-nodes',
      '-subj',
      '/CN=localhost',
      '-addext',
      'subjectAltName=DNS:localhost,IP:127.0.0.1'
    ],
    { stdio: 'ignore' }
  );
  return {
    cert: readFileSync(certPath, 'utf8'),
    key: readFileSync(keyPath, 'utf8')
  };
}
