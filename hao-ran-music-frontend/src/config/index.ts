const isDev = import.meta.env.DEV;
const isProd = import.meta.env.PROD;
export const STATIC_RESOURCE_URL = import.meta.env.VITE_CDN_URL || '';
export const API_BASE_URL = '/api';
export const UPLOAD_URL = '/api/file/upload';
export const WEBSOCKET_URL = import.meta.env.VITE_WS_URL || '';
export const CDN_URL = import.meta.env.VITE_CDN_URL || '';
const config = {
    apiBaseUrl: API_BASE_URL,
    uploadUrl: UPLOAD_URL,
    cdnUrl: CDN_URL || STATIC_RESOURCE_URL,
    websocketUrl: WEBSOCKET_URL
};
export default config;
export function getDefaultCover(): string {
    return '/default-cover.png';
}
export function getDefaultAvatar(): string {
    return '/default-cover.png';
}
