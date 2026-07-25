import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['test/**/*.test.tsx', 'test/**/*.test.ts'],
    environment: 'jsdom',
    setupFiles: ['test/setup.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.ts', 'src/**/*.tsx'],
      exclude: ['src/index.ts', 'src/hooks/index.ts', 'src/styles.css'],
      thresholds: {
        lines: 95,
        branches: 95,
        functions: 95,
        statements: 95
      }
    }
  }
});
