import { describe, it, expect } from 'vitest';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const pkgRoot = process.cwd();
const srcDir = join(pkgRoot, 'src');

function srcFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...srcFiles(full));
    else if (entry.name.endsWith('.ts') || entry.name.endsWith('.tsx')) out.push(full);
  }
  return out;
}

function stripStrings(code: string): string {
  return code
    .replace(/`(?:\\.|[^`\\])*`/g, '""')
    .replace(/'(?:\\.|[^'\\])*'/g, '""')
    .replace(/"(?:\\.|[^"\\])*"/g, '""');
}

const files = srcFiles(srcDir);

function purityHits(): boolean {
  try {
    execFileSync('grep', ['-riE', 'driver|passenger|trip|rider', '--include=*.ts', '--include=*.tsx', '-r', 'src'], {
      cwd: pkgRoot
    });
    return true;
  } catch {
    return false;
  }
}

describe('react packaging', () => {
  it('is a thin binding with no transport or media logic [REACT-04]', () => {
    for (const file of files) {
      const body = readFileSync(file, 'utf8');
      expect(/RTCPeerConnection/.test(body)).toBe(false);
      expect(/WebSocket/.test(body)).toBe(false);
      expect(/getUserMedia/.test(body)).toBe(false);
      expect(/mediaDevices/.test(body)).toBe(false);
    }
    const pkg = JSON.parse(readFileSync(join(pkgRoot, 'package.json'), 'utf8')) as {
      dependencies: Record<string, string>;
    };
    expect(Object.keys(pkg.dependencies)).toEqual(['@sauti/core']);
  });

  it('imports react only for the binding and delegates the rest to core [REACT-04]', () => {
    const hook = readFileSync(join(srcDir, 'useSautiCall.ts'), 'utf8');
    expect(hook).toContain("from 'react'");
    expect(hook).toContain("from '@sauti/core'");
  });

  it('has no comments in source [PKG-01]', () => {
    for (const file of files) {
      const stripped = stripStrings(readFileSync(file, 'utf8'));
      expect(stripped.includes('//')).toBe(false);
      expect(stripped.includes('/*')).toBe(false);
    }
  });

  it('ships a purity script wired into build [PKG-02]', () => {
    expect(purityHits()).toBe(false);
    const probe = join(srcDir, 'purity-probe.ts');
    try {
      writeFileSync(probe, 'export const seat = "passenger";\n');
      expect(purityHits()).toBe(true);
    } finally {
      rmSync(probe, { force: true });
    }
    const pkg = JSON.parse(readFileSync(join(pkgRoot, 'package.json'), 'utf8')) as {
      scripts: Record<string, string>;
    };
    expect(pkg.scripts.purity).toContain('grep');
    expect(pkg.scripts.build).toBe('pnpm run purity && tsc -p tsconfig.json');
  });
});
