import { request } from '@/utils/request';
import logger from '@/utils/logger';
const PROXY_PORT = 7777;
const PROXY_URL = `http://localhost:${PROXY_PORT}`;
const PROXY_TIMEOUT = 1500;
const FILE_INFO_CACHE_TTL = 10 * 1000;
let proxyUrl: string | null = null;
let proxyChecked = false;
let proxyAccessToken: string | null = null;
const fileInfoCache = new Map<string, {
    expiresAt: number;
    value: FileInfo;
}>();
export interface LocalFile {
    path: string;
    type: 'audio' | 'video' | 'unknown';
    format: string;
    size: number;
    modified: string;
    duration?: number;
}
export interface AudioMetadata {
    duration: number;
    bitrate: number;
    codec: string;
    quality: 'lossless' | 'high' | 'standard';
    formatName: string;
    title?: string;
    artist?: string;
    album?: string;
    year?: number;
    genre?: string[];
    track?: {
        no: number;
        of: number;
    };
    sampleRate?: number;
    bitsPerSample?: number;
    numberOfChannels?: number;
    formatQuality?: string;
}
export interface FileInfo {
    path: string;
    name: string;
    size: number;
    modified: string;
    created: string;
    mime: string;
    format: string;
    audio?: AudioMetadata;
}
export interface ScanResult {
    path: string;
    total: number;
    audio: number;
    video: number;
    files: LocalFile[];
}
export interface FileCheckResult {
    path: string;
    exists: boolean;
    size?: number;
    modified?: string;
    error?: string;
}
async function checkProxy(): Promise<boolean> {
    if (proxyChecked && proxyUrl) {
        return true;
    }
    try {
        const response = await fetch(`${PROXY_URL}/health`, {
            method: 'GET',
            signal: AbortSignal.timeout(PROXY_TIMEOUT)
        });
        if (response.ok) {
            const health = await response.json() as {
                status?: unknown;
            };
            if (health.status !== 'ok')
                throw new Error('本地代理健康状态异常');
            const sessionResponse = await fetch(`${PROXY_URL}/session`, {
                method: 'POST',
                signal: AbortSignal.timeout(PROXY_TIMEOUT)
            });
            if (!sessionResponse.ok)
                throw new Error('本地代理会话授权失败');
            const session = await sessionResponse.json() as {
                accessToken?: unknown;
            };
            if (typeof session.accessToken !== 'string' || !session.accessToken) {
                throw new Error('本地代理未提供会话授权');
            }
            proxyUrl = PROXY_URL;
            proxyAccessToken = session.accessToken;
            proxyChecked = true;
            logger.info('local_proxy_connected', { port: PROXY_PORT });
            return true;
        }
    }
    catch {
    }
    proxyUrl = null;
    proxyAccessToken = null;
    proxyChecked = true;
    return false;
}
function proxyHeaders(json = false): HeadersInit {
    const headers: Record<string, string> = {};
    if (proxyAccessToken)
        headers['X-HaoRan-Local-Token'] = proxyAccessToken;
    if (json)
        headers['Content-Type'] = 'application/json';
    return headers;
}
async function getProxyResponseError(response: Response, fallback: string): Promise<string> {
    if (response.status === 403) {
        return '本地代理无法读取该路径，请先选择文件夹授权，或检查 LOCAL_PROXY_ALLOWED_ROOTS 配置';
    }
    if (response.status === 415) {
        return '本地代理仅支持导入和播放音频、视频文件';
    }
    try {
        const body = await response.json() as {
            error?: unknown;
        };
        if (typeof body.error === 'string' && body.error.trim())
            return body.error;
    }
    catch {
    }
    return fallback;
}
export async function getProxyUrl(): Promise<string | null> {
    const isRunning = await checkProxy();
    return isRunning ? proxyUrl : null;
}
export async function scanLocalDirectory(dirPath: string, recursive = true): Promise<ScanResult | null> {
    const url = await getProxyUrl();
    if (!url) {
        throw new Error('本地代理未运行，请先启动代理服务');
    }
    try {
        const response = await fetch(`${url}/api/scan?path=${encodeURIComponent(dirPath)}&recursive=${recursive}`, {
            method: 'GET',
            headers: proxyHeaders()
        });
        if (!response.ok) {
            throw new Error(await getProxyResponseError(response, '扫描失败'));
        }
        return await response.json();
    }
    catch (error: any) {
        logger.error('local_proxy_scan_failed', error);
        throw error;
    }
}
export async function getDefaultPaths(): Promise<string[]> {
    const url = await getProxyUrl();
    if (!url) {
        return [];
    }
    try {
        const response = await fetch(`${url}/api/default-paths`, { headers: proxyHeaders() });
        if (response.ok) {
            const data = await response.json();
            return data.paths || [];
        }
    }
    catch (error) {
        logger.error('local_proxy_defaults_failed', error);
    }
    return [];
}
export async function selectLocalDirectory(): Promise<string | null> {
    const url = await getProxyUrl();
    if (!url) {
        throw new Error('本地代理未运行，请先启动代理服务');
    }
    const response = await fetch(`${url}/api/select-folder`, {
        method: 'POST',
        headers: proxyHeaders()
    });
    if (!response.ok) {
        throw new Error(await getProxyResponseError(response, '打开文件夹选择器失败'));
    }
    const data = await response.json() as {
        path?: unknown;
    };
    return typeof data.path === 'string' && data.path.trim() ? data.path : null;
}
export async function checkFilesExist(paths: string[]): Promise<FileCheckResult[]> {
    const url = await getProxyUrl();
    if (!url) {
        return paths.map(p => ({ path: p, exists: false }));
    }
    try {
        const response = await fetch(`${url}/api/check`, {
            method: 'POST',
            headers: {
                ...proxyHeaders(true)
            },
            body: JSON.stringify(paths)
        });
        if (response.ok) {
            const data = await response.json();
            return data.results || [];
        }
    }
    catch (error) {
        logger.error('local_proxy_file_check_failed', error);
    }
    return paths.map(p => ({ path: p, exists: false }));
}
export async function getLocalFileUrl(filePath: string): Promise<string | null> {
    const url = await getProxyUrl();
    if (!url) {
        return null;
    }
    try {
        await getFileInfo(filePath);
        const response = await fetch(`${url}/api/file-ticket`, {
            method: 'POST',
            headers: proxyHeaders(true),
            body: JSON.stringify({ path: filePath })
        });
        if (!response.ok)
            return null;
        const result = await response.json() as {
            ticket?: unknown;
        };
        if (typeof result.ticket !== 'string' || !result.ticket)
            return null;
        return `${url}/api/file?path=${encodeURIComponent(filePath)}&ticket=${encodeURIComponent(result.ticket)}`;
    }
    catch {
        return null;
    }
}
export async function getFileInfo(filePath: string): Promise<FileInfo> {
    const cached = fileInfoCache.get(filePath);
    if (cached && cached.expiresAt > Date.now())
        return cached.value;
    const url = await getProxyUrl();
    if (!url) {
        throw new Error('本地代理未运行');
    }
    const response = await fetch(`${url}/api/file-info?path=${encodeURIComponent(filePath)}`, {
        headers: proxyHeaders()
    });
    if (!response.ok) {
        throw new Error(await getProxyResponseError(response, '获取文件信息失败'));
    }
    const value = await response.json() as FileInfo;
    fileInfoCache.set(filePath, { value, expiresAt: Date.now() + FILE_INFO_CACHE_TTL });
    return value;
}
export function resetProxyStatus() {
    proxyUrl = null;
    proxyChecked = false;
    proxyAccessToken = null;
    fileInfoCache.clear();
}
export async function isProxyAvailable(): Promise<boolean> {
    return await checkProxy();
}
export const proxyStatus = {
    get url() { return proxyUrl; },
    get checked() { return proxyChecked; },
    get available() { return proxyUrl !== null; }
};
