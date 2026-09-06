import { request } from '@/utils/request';
export type CuratedReviewStatus = 0 | 1 | 2;
export type CuratedSourceType = 'internal' | 'external';
export type CuratedScene = 'discover_top' | 'square_top' | 'all';
export type CuratedContentType = 'official' | 'news' | 'hot_post' | 'hot_topic' | 'new_song' | 'playlist' | 'collab_playlist' | 'vote';
export interface CuratedCarouselItem {
    id: string;
    scene: CuratedScene;
    contentType: CuratedContentType;
    title: string;
    description?: string;
    badge?: string;
    imageUrl?: string;
    fallbackImageUrl?: string;
    sourceType: CuratedSourceType;
    sourceName?: string;
    link?: string;
    fallbackLink?: string;
    targetType?: string;
    targetId?: string;
    priority?: number;
    sortOrder?: number;
    viewCount?: number;
    startTime?: string;
    endTime?: string;
    createTime?: string;
}
export interface CuratedCarouselAdminItem extends CuratedCarouselItem {
    status?: number;
    reviewStatus?: CuratedReviewStatus;
    reviewerId?: string | number;
    reviewTime?: string;
    reviewRemark?: string;
    operatorId?: string | number;
    updateTime?: string;
    deleted?: number;
}
export interface CuratedCarouselRequest {
    scene: CuratedScene;
    contentType: CuratedContentType;
    title: string;
    description?: string;
    badge?: string;
    imageUrl?: string;
    fallbackImageUrl?: string;
    sourceType: CuratedSourceType;
    sourceName?: string;
    link?: string;
    fallbackLink?: string;
    targetType?: string;
    targetId?: string;
    priority?: number;
    sortOrder?: number;
    startTime?: string;
    endTime?: string;
}
export interface CuratedNews {
    id?: string | number;
    pushId: string;
    type: 'news';
    title: string;
    description?: string;
    badge?: string;
    coverUrl?: string;
    link?: string;
    fallbackLink?: string;
    priority?: number;
    startTime?: string;
    endTime?: string;
    status?: number;
    createTime?: string;
    reviewStatus?: CuratedReviewStatus;
    reviewerId?: string | number;
    reviewTime?: string;
    reviewRemark?: string;
}
export interface CuratedBanner {
    id: string | number;
    title: string;
    description?: string;
    cover?: string;
    eventType?: string;
    eventDate?: string;
    source?: string;
    sourceUrl?: string;
    fallbackSourceUrl?: string;
    sourceType?: CuratedSourceType;
    viewCount?: number;
    isFeatured?: boolean;
    sortOrder?: number;
    isDeleted?: boolean;
    createTime?: string;
    reviewStatus?: CuratedReviewStatus;
    reviewerId?: string | number;
    reviewTime?: string;
    reviewRemark?: string;
}
export interface CuratedNewsRequest {
    title: string;
    description?: string;
    badge?: string;
    coverUrl?: string;
    link?: string;
    fallbackLink?: string;
    priority?: number;
    startTime?: string;
    endTime?: string;
}
export interface CuratedBannerRequest {
    title: string;
    description?: string;
    cover?: string;
    eventType?: string;
    eventDate?: string;
    source?: string;
    sourceUrl?: string;
    fallbackSourceUrl?: string;
    sourceType: CuratedSourceType;
    relatedArtists?: string;
    relatedSongs?: string;
    sortOrder?: number;
}
export interface CuratedReviewRequest {
    approved: boolean;
    remark?: string;
}
export function getCuratedCarousel(scene: CuratedScene, limit = 5) {
    return request<CuratedCarouselItem[]>({
        url: '/curated-content/carousel',
        method: 'GET',
        params: { scene, limit }
    });
}
export function incrementCuratedCarouselView(itemId: string) {
    return request<boolean>({
        url: `/curated-content/carousel/${itemId}/view`,
        method: 'POST'
    });
}
export function getCuratedCarouselAdminItems(scene?: CuratedScene, removed = false) {
    return request<CuratedCarouselAdminItem[]>({
        url: '/admin/curated-carousel/items',
        method: 'GET',
        params: {
            ...(scene ? { scene } : {}),
            removed
        }
    });
}
export function createCuratedCarousel(data: CuratedCarouselRequest) {
    return request<number>({
        url: '/admin/curated-carousel/items',
        method: 'POST',
        data
    });
}
export function updateCuratedCarousel(itemId: string | number, data: CuratedCarouselRequest) {
    return request<void>({
        url: `/admin/curated-carousel/items/${itemId}`,
        method: 'PUT',
        data
    });
}
export function reviewCuratedCarousel(itemId: string | number, data: CuratedReviewRequest) {
    return request<void>({
        url: `/admin/curated-carousel/items/${itemId}/review`,
        method: 'POST',
        data
    });
}
export function disableCuratedCarousel(itemId: string | number) {
    return request<void>({
        url: `/admin/curated-carousel/items/${itemId}`,
        method: 'DELETE'
    });
}
export function restoreCuratedCarousel(itemId: string | number) {
    return request<void>({
        url: `/admin/curated-carousel/items/${itemId}/restore`,
        method: 'POST'
    });
}
export function getCuratedNews() {
    return request<CuratedNews[]>({
        url: '/admin/curated-content/news',
        method: 'GET'
    });
}
export function createCuratedNews(data: CuratedNewsRequest) {
    return request<string>({
        url: '/admin/curated-content/news',
        method: 'POST',
        data
    });
}
export function updateCuratedNews(pushId: string, data: CuratedNewsRequest) {
    return request<void>({
        url: `/admin/curated-content/news/${pushId}`,
        method: 'PUT',
        data
    });
}
export function reviewCuratedNews(pushId: string, data: CuratedReviewRequest) {
    return request<void>({
        url: `/admin/curated-content/news/${pushId}/review`,
        method: 'POST',
        data
    });
}
export function disableCuratedNews(pushId: string) {
    return request<void>({
        url: `/admin/curated-content/news/${pushId}`,
        method: 'DELETE'
    });
}
export function getCuratedBanners() {
    return request<CuratedBanner[]>({
        url: '/admin/curated-content/banners',
        method: 'GET'
    });
}
export function createCuratedBanner(data: CuratedBannerRequest) {
    return request<string | number>({
        url: '/admin/curated-content/banners',
        method: 'POST',
        data
    });
}
export function updateCuratedBanner(id: string | number, data: CuratedBannerRequest) {
    return request<void>({
        url: `/admin/curated-content/banners/${id}`,
        method: 'PUT',
        data
    });
}
export function reviewCuratedBanner(id: string | number, data: CuratedReviewRequest) {
    return request<void>({
        url: `/admin/curated-content/banners/${id}/review`,
        method: 'POST',
        data
    });
}
export function disableCuratedBanner(id: string | number) {
    return request<void>({
        url: `/admin/curated-content/banners/${id}`,
        method: 'DELETE'
    });
}
