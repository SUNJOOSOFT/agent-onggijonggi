import path from 'node:path';
import { defineConfig } from 'vitest/config';

/** tsconfig.json의 @/* 경로 별칭을 그대로 맞춘다 — 별도 값이 아니라 그 파일이 진실 원천이다. */
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, '.'),
    },
  },
  test: {
    environment: 'node',
  },
});
