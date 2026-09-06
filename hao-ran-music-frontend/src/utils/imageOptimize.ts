export enum ImageSize {
    ORIGINAL = 'original',
    THUMB = 'thumb',
    SMALL = 'small',
    MEDIUM = 'medium',
    LARGE = 'large'
}
const IMAGE_SIZE_CONFIG: Record<ImageSize, number> = {
    [ImageSize.ORIGINAL]: 0,
    [ImageSize.THUMB]: 100,
    [ImageSize.SMALL]: 200,
    [ImageSize.MEDIUM]: 400,
    [ImageSize.LARGE]: 800,
};
export function getOptimizedImageUrl(url: string | undefined | null, size: ImageSize = ImageSize.MEDIUM, format?: string): string {
    if (!url) {
        return '/default-cover.png';
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
        if (url.includes('/api/file/')) {
            const sizeValue = IMAGE_SIZE_CONFIG[size];
            const separator = url.includes('?') ? '&' : '?';
            let optimizedUrl = `${url}${separator}size=${sizeValue}`;
            if (format) {
                optimizedUrl += `&format=${format}`;
            }
            return optimizedUrl;
        }
        return url;
    }
    return url;
}
export function getThumbImageUrl(url: string | undefined | null): string {
    return getOptimizedImageUrl(url, ImageSize.THUMB);
}
export function getSmallImageUrl(url: string | undefined | null): string {
    return getOptimizedImageUrl(url, ImageSize.SMALL);
}
export function getMediumImageUrl(url: string | undefined | null): string {
    return getOptimizedImageUrl(url, ImageSize.MEDIUM);
}
export function getLargeImageUrl(url: string | undefined | null): string {
    return getOptimizedImageUrl(url, ImageSize.LARGE);
}
export function selectImageSize(containerWidth: number): ImageSize {
    if (containerWidth <= 150)
        return ImageSize.THUMB;
    if (containerWidth <= 300)
        return ImageSize.SMALL;
    if (containerWidth <= 600)
        return ImageSize.MEDIUM;
    return ImageSize.LARGE;
}
export const imageLazyLoadConfig = {
    loading: '/images/loading-placeholder.svg',
    error: '/default-cover.png',
    preloadDistance: 200,
    attempt: 3,
};
let webpSupported: boolean | null = null;
export function checkWebPSupport(): Promise<boolean> {
    if (webpSupported !== null) {
        return Promise.resolve(webpSupported);
    }
    return new Promise((resolve) => {
        const webP = new Image();
        webP.onload = webP.onerror = () => {
            webpSupported = webP.height === 2;
            resolve(webpSupported);
        };
        webP.src = 'data:image/webp;base64,UklGRjoAAABXRUJQVlA4IC4AAACyAgCdASoCAAIALmk0mk0iIiIiIgBoSygABc6WWgAA/veff/0PP8bA//LwYAAA';
    });
}
export async function getBestImageFormat(): Promise<string> {
    const supportsWebP = await checkWebPSupport();
    return supportsWebP ? 'webp' : 'jpeg';
}
