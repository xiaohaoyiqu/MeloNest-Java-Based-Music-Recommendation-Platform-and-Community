const MUSIC_SQUARE_PREFIXES = ['/square', '/music-square'] as const;
export function isMusicSquareThemeRoute(path: string): boolean {
    return MUSIC_SQUARE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
}
export function isTownDailyThemeRoute(path: string): boolean {
    return !path.startsWith('/admin') && !isMusicSquareThemeRoute(path);
}
