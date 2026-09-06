import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': resolve(__dirname, 'src')
        }
    },
    css: {
        preprocessorOptions: {
            scss: {
                api: 'modern-compiler'
            }
        }
    },
    test: {
        environment: 'happy-dom',
        setupFiles: ['./src/test/setup.ts'],
        include: ['src/**/*.{test,spec}.ts'],
        clearMocks: true,
        restoreMocks: true
    }
});
