import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

// base: './' — относительные пути в собранном index.html, чтобы приложение
// работало из любой подпапки. Если при развёртывании нужен корень сайта,
// поменять на '/'.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: '../config/frontend/support',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: false,
  },
  server: { port: 5182 },
});
