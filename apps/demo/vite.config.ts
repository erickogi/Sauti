import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import basicSsl from '@vitejs/plugin-basic-ssl';

const port = process.env.SAUTI_DEMO_PORT ?? '8787';
const target = `http://127.0.0.1:${port}`;

export default defineConfig({
  plugins: [react(), basicSsl()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/token': { target, changeOrigin: true },
      '/ws': { target, ws: true, changeOrigin: true }
    }
  }
});
