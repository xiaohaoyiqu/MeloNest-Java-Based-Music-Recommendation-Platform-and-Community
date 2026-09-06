export interface MVDisplaySource {
    artistName?: string | null;
    artistNames?: string | null;
    publishDate?: string | null;
    publishTime?: string | null;
    createTime?: string | null;
}
function firstText(values: Array<string | null | undefined>): string {
    return values.map(value => value?.trim()).find(Boolean) || '';
}
export function getMVArtistName(mv: MVDisplaySource): string {
    return firstText([mv.artistNames, mv.artistName]) || '未署名';
}
export function getMVDateSource(mv: MVDisplaySource): string {
    return firstText([mv.publishDate, mv.publishTime, mv.createTime]);
}
export function formatMVPublishDate(mv: MVDisplaySource): string {
    const source = getMVDateSource(mv);
    if (!source)
        return '日期待补充';
    const date = new Date(source);
    if (Number.isNaN(date.getTime()))
        return '日期待补充';
    return date.toLocaleDateString('zh-CN');
}
