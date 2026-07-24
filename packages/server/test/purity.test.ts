import { describe, it, expect } from 'vitest';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const pkgRoot = process.cwd();

function srcFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...srcFiles(full));
    else if (entry.name.endsWith('.ts')) out.push(full);
  }
  return out;
}

function grepHitsBannedWords(): boolean {
  try {
    execFileSync(
      'grep',
      ['-riE', 'driver|passenger|trip|rider', '--include=*.ts', '-r', 'src'],
      { cwd: pkgRoot }
    );
    return true;
  } catch {
    return false;
  }
}

describe('purity gate', () => {
  it('passes on clean server source', () => {
    expect(grepHitsBannedWords()).toBe(false);
  });

  it('fails when a banned host-domain word is injected into source', () => {
    const probe = join(pkgRoot, 'src', 'purity-probe.ts');
    try {
      writeFileSync(probe, 'export const seat = "passenger";\n');
      expect(grepHitsBannedWords()).toBe(true);
    } finally {
      rmSync(probe, { force: true });
    }
    expect(grepHitsBannedWords()).toBe(false);
  });

  it('exposes a purity script wired as a package script for both packages', () => {
    const serverPkg = JSON.parse(
      readFileSync(join(pkgRoot, 'package.json'), 'utf8')
    ) as { scripts: Record<string, string> };
    expect(serverPkg.scripts.purity).toContain('grep');
    const protocolPkg = JSON.parse(
      readFileSync(join(pkgRoot, '..', 'protocol', 'package.json'), 'utf8')
    ) as { scripts: Record<string, string> };
    expect(protocolPkg.scripts.purity).toContain('grep');
  });
});

describe('framework-agnostic, port-only dependency surface', () => {
  const files = srcFiles(join(pkgRoot, 'src'));

  it('imports no web framework in the core', () => {
    for (const file of files) {
      const body = readFileSync(file, 'utf8');
      expect(/from ['"]@nestjs/.test(body)).toBe(false);
      expect(/from ['"]express['"]/.test(body)).toBe(false);
    }
  });

  it('imports no concrete redis client, only the RedisPort interface', () => {
    for (const file of files) {
      const body = readFileSync(file, 'utf8');
      expect(/from ['"]ioredis['"]/.test(body)).toBe(false);
      expect(/from ['"]redis['"]/.test(body)).toBe(false);
    }
  });

  it('contains no emoji or em-dash and no block comments in source', () => {
    for (const file of files) {
      const body = readFileSync(file, 'utf8');
      expect(body.includes('\u2014')).toBe(false);
      expect(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test(body)).toBe(false);
      expect(body.includes('/*')).toBe(false);
    }
  });
});
