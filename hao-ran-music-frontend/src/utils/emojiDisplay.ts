export interface EmojiDisplaySource {
    emojiName?: string;
    name?: string;
    gifUrl?: string;
    imageUrl?: string;
    staticUrl?: string;
}
export interface EmojiPackageDisplaySource {
    packageName?: string;
    name?: string;
}
export interface EmojiTextPart {
    type: 'text' | 'emoji';
    text: string;
    code?: string;
}
const EMOJI_TOKEN = /\[emoji:([A-Za-z0-9_-]{1,50})\]/g;
export function extractEmojiCodes(content: string, limit = 50): string[] {
    const result = new Set<string>();
    for (const match of String(content || '').matchAll(EMOJI_TOKEN)) {
        result.add(match[1]);
        if (result.size >= limit)
            break;
    }
    return [...result];
}
export function parseEmojiText(content: string, availableCodes: ReadonlySet<string>): EmojiTextPart[] {
    const value = String(content || '');
    const parts: EmojiTextPart[] = [];
    let lastIndex = 0;
    for (const match of value.matchAll(EMOJI_TOKEN)) {
        const code = match[1];
        const index = match.index ?? 0;
        if (index > lastIndex)
            parts.push({ type: 'text', text: value.slice(lastIndex, index) });
        if (availableCodes.has(code)) {
            parts.push({ type: 'emoji', text: match[0], code });
        }
        else {
            parts.push({ type: 'text', text: match[0] });
        }
        lastIndex = index + match[0].length;
    }
    if (lastIndex < value.length)
        parts.push({ type: 'text', text: value.slice(lastIndex) });
    return parts.length > 0 ? parts : [{ type: 'text', text: value }];
}
const MANAGED_EMOJI_PATH = '/emojis/';
const IMAGE_EXTENSION = /\.(?:jpe?g|png|gif|webp)$/i;
const PACKAGE_DISPLAY_NAMES: Readonly<Record<string, string>> = {
    rabbit_and_fox: '兔与狐'
};
export function getEmojiPackageDisplayName(pkg?: EmojiPackageDisplaySource | null): string {
    const internalName = pkg?.packageName?.trim() || pkg?.name?.trim() || '表情包';
    return PACKAGE_DISPLAY_NAMES[internalName] || internalName;
}
export function getEmojiDisplayName(emoji?: EmojiDisplaySource | null): string {
    const fallback = emoji?.emojiName?.trim() || emoji?.name?.trim() || '表情';
    const rawUrl = emoji?.staticUrl || emoji?.gifUrl || emoji?.imageUrl || '';
    const path = rawUrl.split(/[?#]/, 1)[0].replace(/\\/g, '/');
    if (!path.includes(MANAGED_EMOJI_PATH))
        return fallback;
    const encodedFileName = path.slice(path.lastIndexOf('/') + 1);
    if (!encodedFileName)
        return fallback;
    try {
        const displayName = decodeURIComponent(encodedFileName)
            .replace(IMAGE_EXTENSION, '')
            .trim();
        return displayName || fallback;
    }
    catch {
        return fallback;
    }
}
