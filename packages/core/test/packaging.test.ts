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
    else if (entry.name.endsWith('.ts')) out.push(full);
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
    execFileSync('grep', ['-riE', 'driver|passenger|trip|rider', '--include=*.ts', '-r', 'src'], {
      cwd: pkgRoot
    });
    return true;
  } catch {
    return false;
  }
}

describe('packaging', () => {
  it('has no comments anywhere in source [PKG-01]', () => {
    for (const file of files) {
      const stripped = stripStrings(readFileSync(file, 'utf8'));
      expect(stripped.includes('//')).toBe(false);
      expect(stripped.includes('/*')).toBe(false);
      expect(stripped.includes('*/')).toBe(false);
    }
  });

  it('ships a purity script wired into build that fails on a banned word [PKG-02]', () => {
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

  it('is framework-agnostic and depends only on @sauti/protocol [PKG-03]', () => {
    const pkg = JSON.parse(readFileSync(join(pkgRoot, 'package.json'), 'utf8')) as {
      dependencies: Record<string, string>;
    };
    expect(Object.keys(pkg.dependencies)).toEqual(['@sauti/protocol']);
    for (const file of files) {
      const body = readFileSync(file, 'utf8');
      expect(/from ['"]react['"]/.test(body)).toBe(false);
      expect(/from ['"]vue['"]/.test(body)).toBe(false);
      expect(/from ['"]svelte['"]/.test(body)).toBe(false);
      expect(/from ['"]@angular/.test(body)).toBe(false);
    }
  });

  it('never leaks a raw RTCPeerConnection or WebSocket across the public snapshot [PKG-04]', async () => {
    const { connect, readyFrame } = await import('./harness.js');
    const { participant } = await import('./fakes.js');
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const snap = conn.call.getSnapshot();
    expect(() => JSON.stringify(snap)).not.toThrow();
    for (const p of snap.participants) {
      expect(Object.keys(p).sort()).toEqual(
        ['connectionState', 'metadata', 'muted', 'onHold', 'participantId', 'quality'].sort()
      );
    }
  });

  it('enforces 95% coverage thresholds in both packages [PKG-05]', () => {
    const coreCfg = readFileSync(join(pkgRoot, 'vitest.config.ts'), 'utf8');
    const reactCfg = readFileSync(join(pkgRoot, '..', 'react', 'vitest.config.ts'), 'utf8');
    for (const cfg of [coreCfg, reactCfg]) {
      expect(cfg).toContain('lines: 95');
      expect(cfg).toContain('branches: 95');
    }
  });

  it('separates jsdom fakes from real-browser interop [PKG-06]', () => {
    const cfg = readFileSync(join(pkgRoot, 'vitest.config.ts'), 'utf8');
    expect(cfg).toContain("environment: 'jsdom'");
    const readme = readFileSync(join(pkgRoot, 'README.md'), 'utf8').toLowerCase();
    expect(readme).toContain('playwright');
    const fakes = readFileSync(join(pkgRoot, 'test', 'fakes.ts'), 'utf8');
    expect(fakes).toContain('FakePeerConnection');
    expect(fakes).toContain('FakeWebSocket');
  });

  it('reuses protocol helpers rather than redefining them [PKG-07]', () => {
    const peer = readFileSync(join(srcDir, 'peer.ts'), 'utf8');
    expect(peer).toMatch(/import\s*\{[^}]*isPolite[^}]*\}\s*from '@sauti\/protocol'/);
    const signaling = readFileSync(join(srcDir, 'signaling.ts'), 'utf8');
    expect(signaling).toMatch(/parseServerFrame/);
    for (const file of files) {
      const body = readFileSync(file, 'utf8');
      expect(/function isPolite/.test(body)).toBe(false);
      expect(/const isPolite\s*=/.test(body)).toBe(false);
    }
  });

  it('computes politeness only through isPolite, never inline id comparison [NEG-01]', () => {
    for (const file of files) {
      const stripped = stripStrings(readFileSync(file, 'utf8'));
      expect(/\b(selfId|peerId|participantId)\s*[<>]=?/.test(stripped)).toBe(false);
      expect(/politePeer/.test(stripped)).toBe(false);
    }
  });

  it('never infers mute from audio energy [MH-01]', () => {
    for (const file of files) {
      const body = readFileSync(file, 'utf8').toLowerCase();
      expect(body.includes('audiolevel')).toBe(false);
      expect(body.includes('analyser')).toBe(false);
      expect(body.includes('getbytefrequency')).toBe(false);
    }
  });
});
