export const LEGACY_AUTH_BACKGROUND = '/mycartoon/登录页面背景.png';
export const FALLBACK_AUTH_BACKGROUND = '/mycartoon/auth-city-morning.png';
export async function configureAuthBackground(request: typeof fetch = fetch, root: HTMLElement = document.documentElement) {
    try {
        const response = await request(LEGACY_AUTH_BACKGROUND, {
            method: 'HEAD',
            cache: 'no-cache'
        });
        const contentType = response.headers.get('content-type') || '';
        if (!response.ok || !contentType.toLowerCase().startsWith('image/'))
            return false;
        root.classList.add('auth-background--legacy');
        return true;
    }
    catch {
        return false;
    }
}
