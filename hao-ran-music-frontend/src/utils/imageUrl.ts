import { STATIC_RESOURCE_URL } from '@/config';
import { logger as frontendLogger } from '@/utils/logger';
const DEFAULT_COVER_PATH = '/default-cover.png';
const DEFAULT_AVATAR_PATH = DEFAULT_COVER_PATH;
const DEFAULT_CAROUSEL_COVER_PATH = DEFAULT_COVER_PATH;
const LEGACY_DEFAULT_IMAGE_PATHS = new Set([
    '/default-avatar.png',
    '/default-base.png',
    '/images/default-avatar.png',
    '/images/default-base.png',
    '/images/default-cover.png'
]);
function getStaticUrl(path: string): string {
    if (STATIC_RESOURCE_URL && path.startsWith('/')) {
        return STATIC_RESOURCE_URL + path;
    }
    return path;
}
export function processImageUrl(url: string | undefined): string {
    if (!url || url === 'null' || url === 'undefined') {
        return getStaticUrl(DEFAULT_COVER_PATH);
    }
    if (typeof url !== 'string') {
        frontendLogger.capture('warn', '[processImageUrl] 非字符串URL: ', typeof url, url);
        return getStaticUrl(DEFAULT_COVER_PATH);
    }
    const trimmedUrl = url.trim();
    if (trimmedUrl.length === 0 || trimmedUrl.length > 2000) {
        frontendLogger.capture('warn', '[processImageUrl] URL长度异常:', trimmedUrl.length);
        return getStaticUrl(DEFAULT_COVER_PATH);
    }
    if (trimmedUrl.includes('undefined') || trimmedUrl.includes('NULL')) {
        frontendLogger.capture('warn', '[processImageUrl] URL包含无效关键字:', trimmedUrl);
        return getStaticUrl(DEFAULT_COVER_PATH);
    }
    if (trimmedUrl.startsWith('http://') || trimmedUrl.startsWith('https://')) {
        try {
            const protocolEnd = trimmedUrl.indexOf('://');
            if (protocolEnd === -1) {
                return getStaticUrl(DEFAULT_COVER_PATH);
            }
            const pathStart = trimmedUrl.indexOf('/', protocolEnd + 3);
            if (pathStart === -1) {
                return getStaticUrl(DEFAULT_COVER_PATH);
            }
            const relativePath = trimmedUrl.substring(pathStart);
            return relativePath.startsWith('/') ? relativePath : '/' + relativePath;
        }
        catch (e) {
            frontendLogger.capture('error', '[processImageUrl] URL解析失败: ', trimmedUrl, e);
            return getStaticUrl(DEFAULT_COVER_PATH);
        }
    }
    if (LEGACY_DEFAULT_IMAGE_PATHS.has(trimmedUrl)) {
        return getStaticUrl(DEFAULT_COVER_PATH);
    }
    if (trimmedUrl.startsWith('/')) {
        return getStaticUrl(trimmedUrl);
    }
    frontendLogger.capture('warn', '[processImageUrl] 未知URL格式:', trimmedUrl);
    return getStaticUrl(DEFAULT_COVER_PATH);
}
export function processAvatarUrl(url: string | undefined): string {
    const avatar = getStaticUrl(DEFAULT_AVATAR_PATH);
    if (!url || url === 'null' || url === 'undefined') {
        return avatar;
    }
    const processed = processImageUrl(url);
    return processed === getStaticUrl(DEFAULT_COVER_PATH) ? avatar : processed;
}
export function getDefaultCover(): string {
    return getStaticUrl(DEFAULT_COVER_PATH);
}
export function setDefaultCoverOnError(event: Event): void {
    const image = event.currentTarget as HTMLImageElement | null;
    if (!image)
        return;
    if (image.dataset.defaultCoverApplied === 'true') {
        image.onerror = null;
        return;
    }
    image.dataset.defaultCoverApplied = 'true';
    image.src = getDefaultCover();
}
export function getDefaultCarouselCover(): string {
    return getStaticUrl(DEFAULT_CAROUSEL_COVER_PATH);
}
export function getDefaultAvatar(): string {
    return getStaticUrl(DEFAULT_AVATAR_PATH);
}
