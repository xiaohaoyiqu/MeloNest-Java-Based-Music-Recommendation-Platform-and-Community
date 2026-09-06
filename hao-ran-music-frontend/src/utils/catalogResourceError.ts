export type CatalogResourceKind = 'song' | 'album' | 'artist' | 'playlist';
const unavailableCodes = new Set([403, 404, 410]);
function errorCode(error: unknown): number | undefined {
    if (!error || typeof error !== 'object')
        return undefined;
    const candidate = error as {
        code?: unknown;
        response?: {
            status?: unknown;
            data?: {
                code?: unknown;
            };
        };
    };
    const value = candidate.code ?? candidate.response?.data?.code ?? candidate.response?.status;
    const code = Number(value);
    return Number.isFinite(code) ? code : undefined;
}
export function isCatalogResourceUnavailable(error: unknown): boolean {
    const code = errorCode(error);
    return code !== undefined && unavailableCodes.has(code);
}
export function catalogResourceUnavailableMessage(kind: CatalogResourceKind): string {
    const messages: Record<CatalogResourceKind, string> = {
        song: '该歌曲已删除、下架或暂不可访问',
        album: '该专辑已删除、下架或暂不可访问',
        artist: '该歌手已删除、隐藏或暂不可访问',
        playlist: '该歌单已删除、转为私密或暂不可访问'
    };
    return messages[kind];
}
