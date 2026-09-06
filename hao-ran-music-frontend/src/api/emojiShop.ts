import { request } from '@/utils/request';
import type { EmojiPackage as LegacyEmojiPackage } from './emoji';
export interface EmojiPackageVO {
    id: string | number;
    name: string;
    packageId?: string | number;
    packageName?: string;
    description?: string;
    coverUrl: string;
    coverEmojiId?: string | number;
    type: string;
    typeName?: string;
    category?: string;
    categoryName?: string;
    price?: number;
    purchaseMode?: 'free' | 'points' | 'cash';
    cashPrice?: string | number;
    pointsCost?: number;
    isFree: boolean;
    downloadCount: number;
    itemCount?: number;
    itemLimit?: 8 | 16 | 24 | 32 | 40;
    remainingCount?: number;
    previewItems?: EmojiPreviewItem[];
    isPurchased?: boolean;
    isFavorited?: boolean;
    createTime?: string;
    emojis?: Array<{
        emojiId?: string | number;
        staticUrl?: string;
        gifUrl?: string;
        imageUrl?: string;
    }>;
}
export interface EmojiPreviewItem {
    id: string | number;
    emojiId?: string;
    name?: string;
    imageUrl: string;
}
export interface EmojiItemVO {
    id: string | number;
    emojiPackageId: string;
    itemName: string;
    itemCode: string;
    imageUrl: string;
    gifUrl?: string;
    category?: string;
    sortOrder?: number;
}
export interface EmojiPackageDetailVO extends EmojiPackageVO {
    items: EmojiItemVO[];
    creator?: {
        id: string | number;
        nickname: string;
        packageId?: string | number;
        packageName?: string;
    };
}
export interface EmojiShopHomeVO {
    recommended: EmojiPackageVO[];
    hot: EmojiPackageVO[];
    latest: EmojiPackageVO[];
    free: EmojiPackageVO[];
    myEmojis: EmojiPackageVO[];
}
export interface PageResult<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
    pages: number;
}
export function getEmojiShopHome() {
    return request<EmojiShopHomeVO>({
        url: '/emoji-shop/home',
        method: 'GET'
    });
}
export function getEmojiShopPackages(params?: {
    type?: string;
    category?: string;
    page?: number;
    size?: number;
}) {
    return request<PageResult<EmojiPackageVO>>({
        url: '/emoji-shop/packages',
        method: 'GET',
        params
    });
}
export function getEmojiShopPackageDetail(id: string | number) {
    return request<EmojiPackageDetailVO>({
        url: `/emoji-shop/package/${id}`,
        method: 'GET'
    });
}
export function purchaseEmojiPackage(id: string | number) {
    return request<void>({
        url: `/emoji-shop/package/${id}/purchase`,
        method: 'POST'
    });
}
export function getMyEmojiShopPackages() {
    return request<EmojiPackageVO[]>({
        url: '/emoji-shop/my',
        method: 'GET'
    });
}
export function favoriteEmojiPackage(id: string | number) {
    return request<void>({
        url: `/emoji-shop/package/${id}/favorite`,
        method: 'POST'
    });
}
export function unfavoriteEmojiPackage(id: string | number) {
    return request<void>({
        url: `/emoji-shop/package/${id}/favorite`,
        method: 'DELETE'
    });
}
export const emojiShopApi = {
    getEmojiShopHome,
    getEmojiShopPackages,
    getEmojiShopPackageDetail,
    purchaseEmojiPackage,
    getMyEmojiShopPackages,
    favoriteEmojiPackage,
    unfavoriteEmojiPackage
};
export function getEnabledPackages() {
    return request<LegacyEmojiPackage[]>({
        url: '/emoji/packages/enabled',
        method: 'GET'
    });
}
