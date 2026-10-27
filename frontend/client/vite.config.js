import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import {fileURLToPath} from 'node:url';

// base: './' — относительные пути в собранном index.html, чтобы приложение
// работало из любой подпапки. Если при развёртывании нужен корень сайта,
// поменять на '/'.
export default defineConfig({
  plugins: [react()],
  resolve: {
    // Общие компоненты и стили обоих кабинетов: import … from '@shared/…'.
    // Их зависимости (react-markdown и др.) берутся из общего node_modules
    // workspace (frontend/node_modules), поэтому указаны в package.json кабинетов.
    alias: { '@shared': fileURLToPath(new URL('../shared', import.meta.url)) },
    // Один экземпляр React на приложение, даже если файл импортирован из shared.
    dedupe: ['react', 'react-dom'],
  },
  base: './',
  build: {
    outDir: '../../config/frontend/client',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: false,
  },
  server: { port: 5182 },
});
