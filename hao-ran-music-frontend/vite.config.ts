import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve, dirname } from 'path';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import { fileURLToPath } from 'url';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const elementPlusIconNames = new Set(Object.keys(ElementPlusIconsVue));
const ElementPlusIconResolver = (name: string) => elementPlusIconNames.has(name)
    ? { name, from: '@element-plus/icons-vue' }
    : undefined;
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    return {
        plugins: [
            vue(),
            AutoImport({
                resolvers: [ElementPlusResolver(), ElementPlusIconResolver],
                imports: ['vue', 'vue-router', 'pinia'],
                dts: 'src/auto-imports.d.ts'
            }),
            Components({
                resolvers: [ElementPlusResolver()],
                dts: 'src/components.d.ts'
            })
        ],
        resolve: {
            alias: {
                '@': resolve(__dirname, 'src')
            }
        },
        server: {
            host: '0.0.0.0',
            port: 3000,
            strictPort: true,
            watch: {
                ignored: ['**/test-results/**', '**/playwright-report/**']
            },
            proxy: {
                '/api': {
                    target: env.VITE_API_PROXY_URL,
                    changeOrigin: true,
                    ws: true
                },
                '/ws': {
                    target: env.VITE_API_PROXY_URL,
                    changeOrigin: true,
                    ws: true
                },
                '/songs/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/mvs/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/covers/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/images/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/avatars/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/playlist-covers/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/music/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/decorations/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                },
                '/emojis/': {
                    target: env.VITE_STATIC_PROXY_URL,
                    changeOrigin: true
                }
            }
        },
        optimizeDeps: {
            include: ['vue', 'pinia', 'vue-router', 'element-plus', '@element-plus/icons-vue']
        },
        css: {
            preprocessorOptions: {
                scss: {
                    api: 'modern-compiler'
                }
            }
        },
        build: {
            outDir: 'dist',
            assetsDir: 'assets',
            sourcemap: false,
            minify: 'terser',
            terserOptions: {
                compress: {
                    drop_console: true,
                    drop_debugger: true
                }
            },
            rollupOptions: {
                output: {
                    manualChunks(id) {
                        if (id.includes('/node_modules/vue/') || id.includes('/node_modules/vue-router/') || id.includes('/node_modules/pinia/'))
                            return 'vue-vendor';
                        if (id.includes('/node_modules/zrender/'))
                            return 'charts-renderer';
                        if (id.includes('/node_modules/echarts/'))
                            return 'charts';
                    }
                }
            }
        }
    };
});
