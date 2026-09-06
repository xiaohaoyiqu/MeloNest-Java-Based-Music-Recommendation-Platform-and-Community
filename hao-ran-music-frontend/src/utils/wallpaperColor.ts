function parseColor(color: string): {
    r: number;
    g: number;
    b: number;
} | null {
    if (color.includes('gradient')) {
        const match = color.match(/#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3}|rgb\([^)]+\)/g);
        if (match && match[0]) {
            return parseColor(match[0]);
        }
        return null;
    }
    if (color.startsWith('#')) {
        let hex = color.slice(1);
        if (hex.length === 3) {
            hex = hex.split('').map(c => c + c).join('');
        }
        return {
            r: parseInt(hex.slice(0, 2), 16),
            g: parseInt(hex.slice(2, 4), 16),
            b: parseInt(hex.slice(4, 6), 16)
        };
    }
    const rgbMatch = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
    if (rgbMatch) {
        return {
            r: parseInt(rgbMatch[1]),
            g: parseInt(rgbMatch[2]),
            b: parseInt(rgbMatch[3])
        };
    }
    return null;
}
function getLuminance(r: number, g: number, b: number): number {
    return 0.299 * r + 0.587 * g + 0.114 * b;
}
function extractImageUrl(background: string): string {
    const match = background.match(/url\((['"]?)(.*?)\1\)/);
    return match?.[2] || background;
}
export async function analyzeWallpaperColors(background: string): Promise<{
    textPrimary: string;
    textSecondary: string;
    textTertiary: string;
    textShadow: string;
    cardBackground: string;
    cardBorder: string;
}> {
    const defaultConfig = {
        textPrimary: 'rgba(255, 255, 255, 0.95)',
        textSecondary: 'rgba(255, 255, 255, 0.7)',
        textTertiary: 'rgba(255, 255, 255, 0.5)',
        textShadow: '0 1px 3px rgba(0,0,0,0.8), 0 1px 2px rgba(0,0,0,0.6)',
        cardBackground: 'rgba(0, 0, 0, 0.35)',
        cardBorder: 'rgba(255, 255, 255, 0.15)'
    };
    if (background.includes('gradient')) {
        return generateConfigByLuminance(analyzeGradientLuminance(background));
    }
    if (background.startsWith('#') || background.startsWith('rgb')) {
        const color = parseColor(background);
        if (color) {
            const luminance = getLuminance(color.r, color.g, color.b);
            return generateConfigByLuminance(luminance);
        }
        return defaultConfig;
    }
    if (background.includes('url(') || background.startsWith('http')) {
        try {
            const imageUrl = extractImageUrl(background);
            const dominantColor = await Promise.race([
                getDominantColorFromImage(imageUrl),
                new Promise<null>((resolve) => setTimeout(() => resolve(null), 3000))
            ]);
            if (dominantColor) {
                const luminance = getLuminance(dominantColor.r, dominantColor.g, dominantColor.b);
                return generateConfigByLuminance(luminance);
            }
        }
        catch {
        }
    }
    return defaultConfig;
}
function generateConfigByLuminance(luminance: number) {
    const brightThreshold = 160;
    const darkThreshold = 80;
    if (luminance > brightThreshold) {
        return {
            textPrimary: 'rgba(30, 30, 30, 0.95)',
            textSecondary: 'rgba(60, 60, 60, 0.75)',
            textTertiary: 'rgba(100, 100, 100, 0.6)',
            textShadow: '0 1px 2px rgba(255,255,255,0.8), 0 1px 4px rgba(255,255,255,0.6)',
            cardBackground: 'rgba(255, 255, 255, 0.55)',
            cardBorder: 'rgba(0, 0, 0, 0.1)'
        };
    }
    else if (luminance < darkThreshold) {
        return {
            textPrimary: 'rgba(255, 255, 255, 0.95)',
            textSecondary: 'rgba(255, 255, 255, 0.7)',
            textTertiary: 'rgba(255, 255, 255, 0.5)',
            textShadow: '0 1px 3px rgba(0,0,0,0.8), 0 1px 2px rgba(0,0,0,0.6)',
            cardBackground: 'rgba(0, 0, 0, 0.35)',
            cardBorder: 'rgba(255, 255, 255, 0.15)'
        };
    }
    else {
        return {
            textPrimary: 'rgba(255, 255, 255, 0.95)',
            textSecondary: 'rgba(255, 255, 255, 0.7)',
            textTertiary: 'rgba(255, 255, 255, 0.5)',
            textShadow: '0 1px 2px rgba(0,0,0,0.7), 0 1px 3px rgba(0,0,0,0.4)',
            cardBackground: 'rgba(0, 0, 0, 0.25)',
            cardBorder: 'rgba(255, 255, 255, 0.12)'
        };
    }
}
export function getDominantColorFromImage(imageUrl: string, timeout = 5000): Promise<{
    r: number;
    g: number;
    b: number;
} | null> {
    return new Promise((resolve) => {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        let resolved = false;
        const doResolve = (value: null | {
            r: number;
            g: number;
            b: number;
        }) => {
            if (!resolved) {
                resolved = true;
                resolve(value);
            }
        };
        const timeoutId = setTimeout(() => {
            doResolve(null);
        }, timeout);
        img.onload = () => {
            clearTimeout(timeoutId);
            try {
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                if (!ctx) {
                    doResolve(null);
                    return;
                }
                const maxSize = 50;
                const scale = Math.min(maxSize / img.width, maxSize / img.height, 1);
                canvas.width = img.width * scale;
                canvas.height = img.height * scale;
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
                const pixels = imageData.data;
                let r = 0, g = 0, b = 0;
                let count = 0;
                for (let i = 0; i < pixels.length; i += 40) {
                    r += pixels[i];
                    g += pixels[i + 1];
                    b += pixels[i + 2];
                    count++;
                }
                if (count > 0) {
                    const result = {
                        r: Math.round(r / count),
                        g: Math.round(g / count),
                        b: Math.round(b / count)
                    };
                    doResolve(result);
                }
                else {
                    doResolve(null);
                }
            }
            catch {
                doResolve(null);
            }
        };
        img.onerror = () => {
            clearTimeout(timeoutId);
            doResolve(null);
        };
        img.src = imageUrl;
    });
}
export function analyzeGradientLuminance(gradient: string): number {
    const colors = gradient.match(/#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3}/g);
    if (!colors || colors.length === 0)
        return 128;
    let totalLuminance = 0;
    let validColors = 0;
    for (const color of colors) {
        const parsed = parseColor(color);
        if (parsed) {
            totalLuminance += getLuminance(parsed.r, parsed.g, parsed.b);
            validColors++;
        }
    }
    return validColors > 0 ? totalLuminance / validColors : 128;
}
