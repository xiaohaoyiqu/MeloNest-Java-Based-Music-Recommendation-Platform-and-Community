import { request } from '@/utils/request';
export type EmojiCategory = 'emotion' | 'object' | 'symbol' | 'custom';
export interface EmojiPackage {
    packageId: string | number;
    id?: string | number;
    packageName: string;
    description: string;
    coverUrl: string;
    iconUrl?: string;
    coverEmojiId?: string | number;
    category: string;
    type: string;
    isSystem: boolean;
    isEnabled: boolean;
    createTime: string;
    emojis?: Emoji[];
    price?: number;
    pointsCost?: number;
    purchaseMode?: 'free' | 'points' | 'cash';
    cashPrice?: number;
    creatorId?: string | number;
    itemCount?: number;
    itemLimit?: 8 | 16 | 24 | 32 | 40;
    remainingCount?: number;
    reviewStatus?: 'draft' | 'pending' | 'approved' | 'rejected';
    submitTime?: string;
    reviewTime?: string;
    reviewReason?: string;
}
export interface Emoji {
    id: string;
    emojiId: string;
    emojiCode?: string;
    emojiName: string;
    name?: string;
    packageId: string | number;
    category: string;
    gifUrl: string;
    imageUrl?: string;
    staticUrl?: string;
    isSystem: boolean;
    usageCount: number;
    isEnabled: boolean;
    createTime: string;
}
export interface EmojiUploadBatchResponse {
    batchId: string | number;
    added: number;
    emojiIds: Array<string | number>;
}
export interface EmojiDisplayItem {
    emojiId: string;
    emojiName: string;
    imageUrl: string;
}
export const EMOJI_PACKAGE_CAPACITIES = [8, 16, 24, 32, 40] as const;
export interface EmojiStatistics {
    totalPackages: number;
    totalEmojis: number;
    todayUsage: number;
    hotEmojis: Emoji[];
}
export function getAllPackages() {
    return request<EmojiPackage[]>({
        url: '/emoji/packages',
        method: 'GET'
    });
}
export function getEnabledPackages() {
    return request<EmojiPackage[]>({
        url: '/emoji/packages/enabled',
        method: 'GET'
    });
}
export function getPackageDetail(packageId: string | number) {
    return request<EmojiPackage>({
        url: `/emoji/package/${packageId}`,
        method: 'GET'
    });
}
export function getPackageReviewDetail(packageId: string | number) {
    return request<EmojiPackage>({
        url: `/emoji/package/${packageId}/review-detail`,
        method: 'GET'
    });
}
export function getSystemEmojis() {
    return request<Emoji[]>({
        url: '/emoji/system',
        method: 'GET'
    });
}
export function getEmojisByCategory(category: EmojiCategory) {
    return request<Emoji[]>({
        url: `/emoji/category/${category}`,
        method: 'GET'
    });
}
export function searchEmojis(keyword: string) {
    return request<Emoji[]>({
        url: '/emoji/search',
        method: 'GET',
        params: { keyword }
    });
}
export function resolveEmojiDisplay(codes: string[]) {
    return request<EmojiDisplayItem[]>({
        url: '/emoji/render',
        method: 'GET',
        params: { codes: codes.join(',') }
    });
}
export function getHotEmojis(limit = 50) {
    return request<Emoji[]>({
        url: '/emoji/hot',
        method: 'GET',
        params: { limit }
    });
}
export function pagePackages(page = 1, size = 20) {
    return request<{
        records: EmojiPackage[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/emoji/packages/page',
        method: 'GET',
        params: { page, size }
    });
}
export function getMyPackages() {
    return request<EmojiPackage[]>({
        url: '/emoji/packages/my',
        method: 'GET'
    });
}
export function uploadPackageEmojis(packageId: string | number, files: File[], idempotencyKey: string) {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return request<EmojiUploadBatchResponse>({
        url: `/emoji/package/${packageId}/emojis/upload`,
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data',
            'Idempotency-Key': idempotencyKey
        },
        timeout: 600000
    });
}
export function createPackage(emojiPackage: Partial<EmojiPackage>) {
    return request<number>({
        url: '/emoji/package/create',
        method: 'POST',
        data: emojiPackage
    });
}
export function updatePackage(packageId: string | number, emojiPackage: Partial<EmojiPackage>) {
    return request<void>({ url: `/emoji/package/${packageId}`, method: 'PUT', data: emojiPackage });
}
export function submitEmojiPackage(packageId: string | number) {
    return request<void>({
        url: `/emoji/package/${packageId}/submit`,
        method: 'POST'
    });
}
export function setEmojiPackageCover(packageId: string | number, emojiId: string | number) {
    return request<void>({
        url: `/emoji/package/${packageId}/cover`,
        method: 'PUT',
        data: { emojiId }
    });
}
export function recordUsage(emojiId: string) {
    return request<void>({
        url: `/emoji/${emojiId}/usage`,
        method: 'POST'
    });
}
export function deleteEmoji(emojiId: string) {
    return request<boolean>({
        url: `/emoji/${emojiId}`,
        method: 'DELETE'
    });
}
export function deleteEmojiPackage(packageId: string | number) {
    return request<boolean>({
        url: `/emoji/package/${packageId}`,
        method: 'DELETE'
    });
}
export function getEmojiStatistics() {
    return request<EmojiStatistics>({
        url: '/emoji/statistics',
        method: 'GET'
    });
}
export const emojiApi = {
    getAllPackages,
    getEnabledPackages,
    getPackageDetail,
    getPackageReviewDetail,
    getSystemEmojis,
    getEmojisByCategory,
    searchEmojis,
    resolveEmojiDisplay,
    getHotEmojis,
    pagePackages,
    uploadPackageEmojis,
    getMyPackages,
    createPackage,
    updatePackage,
    submitEmojiPackage,
    setEmojiPackageCover,
    recordUsage,
    deleteEmoji,
    deleteEmojiPackage,
    getEmojiStatistics
};
