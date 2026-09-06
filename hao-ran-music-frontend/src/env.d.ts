/// <reference types="vite/client" />
import 'axios';
declare global {
    interface ImportMetaEnv {
        readonly VITE_API_URL?: string;
        readonly VITE_API_PROXY_URL?: string;
        readonly VITE_APP_TITLE?: string;
        readonly VITE_APP_BASE_URL?: string;
    }
    interface ImportMeta {
        readonly env: ImportMetaEnv;
    }
    interface Window {
    }
}
declare module 'axios' {
    export interface InternalAxiosRequestConfig<D = any> {
        metadata?: {
            requestId?: string;
            abortController?: AbortController;
        };
    }
}
export {};
