export function artistKindName(artist: {
    artistKindName?: string;
    artistKind?: string;
    type?: string | number;
    gender?: number;
}): string {
    if (artist.artistKindName)
        return artist.artistKindName;
    if (artist.artistKind === 'group' || Number(artist.type) === 2 || artist.gender === 3)
        return '乐队 / 组合';
    if (artist.artistKind === 'person' || Number(artist.type) === 1 || artist.gender === 1 || artist.gender === 2)
        return '个人音乐人';
    return '类型待补充';
}
export function profileSourceName(artist: {
    profileSourceName?: string;
    profileSource?: string;
    isCreator?: boolean | number;
}): string {
    if (artist.profileSourceName)
        return artist.profileSourceName;
    return artist.profileSource === 'town_creator' || artist.isCreator === true || artist.isCreator === 1
        ? '小镇创作者'
        : '曲库收录';
}
