import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @see docs/setup.md — required for dev server behind Cloudflare/ngrok HTTPS tunnels (fixes white screen). */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '');
  const tunnelHost = env.DEV_TUNNEL_HOST?.trim();

  return {
    // sockjs-client (STOMP over SockJS) expects Node's `global` — missing in browser → white screen.
    define: {
      global: 'globalThis',
    },
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      // Without this, remote browsers hit WSS on wrong host/port → blank page + Vite client errors in console.
      hmr: tunnelHost
        ? { protocol: 'wss', host: tunnelHost, clientPort: 443 }
        : undefined,
      allowedHosts: [
        'localhost',
        '127.0.0.1',
        '.trycloudflare.com',
        '.ngrok-free.app',
        '.ngrok.app',
        '.ngrok.io',
        '.localhost.run',
      ],
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
          changeOrigin: true,
        },
        '/ws': {
          target: env.VITE_API_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
          ws: true,
          changeOrigin: true,
        },
      },
    },
    build: {
      sourcemap: true,
      target: 'es2022',
    },
  };
});
