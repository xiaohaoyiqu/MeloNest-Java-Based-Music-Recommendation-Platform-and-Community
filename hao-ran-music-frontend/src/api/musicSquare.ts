import { request } from '@/utils/request';
export type PostType = 'text' | 'music' | 'album' | 'playlist' | 'mv' | 'diary' | 'review' | 'video';
export type ResourceType = 'song' | 'album' | 'playlist' | 'mv';
export type Visibility = 'public' | 'followers' | 'private';
export type TimeRange = 'today' | '3days' | '7days' | '30days' | 'all';
export interface MusicResource {
    type: ResourceType;
    id: string;
    title: string;
    coverUrl?: string;
    artist?: string;
    duration?: number;
    unavailable?: boolean;
}
export interface ListenData {
    songCount: number;
    duration: number;
    topSong?: {
        id: string;
        name: string;
        playCount: number;
    };
}
export interface PostVO {
    id: string;
    userId: string;
    userName: string;
    userAvatar: string;
    userLevel: number;
    isCreator: boolean;
    isVip: boolean;
    content: string;
    images: string[];
    imageInfo?: any[];
    videoInfo?: any;
    musicResources: MusicResource[];
    likeCount: number;
    commentCount: number;
    shareCount: number;
    isLiked: boolean;
    allowComment?: boolean;
    authorAllowComment?: boolean;
    officialCommentClosed?: boolean;
    canManageComment?: boolean;
    canOfficialManageComment?: boolean;
    topics: string[];
    postType: PostType;
    visibility: Visibility;
    isListenDiary?: boolean;
    listenData?: ListenData;
    createTime: string;
    updateTime: string;
}
export interface TopicVO {
    id: string;
    name: string;
    description: string;
    cover: string;
    category: string;
    postCount: number;
    followerCount: number;
    isHot: boolean;
    isFollowed: boolean;
    createTime: string;
}
export interface HotEventVO {
    id: string;
    title: string;
    description: string;
    cover: string;
    eventType: string;
    eventDate: string;
    source: string;
    sourceUrl: string;
    fallbackSourceUrl?: string;
    sourceType?: 'internal' | 'external' | 'news';
    relatedArtists: number[];
    relatedSongs: number[];
    viewCount: number;
    createTime: string;
}
export interface VoteStatsVO {
    songId: string;
    songName: string;
    coverUrl: string;
    artistName: string;
    voteCount: number;
    todayVotes: number;
    isVoted: boolean;
}
export type ItemCategory = 'cd' | 'vinyl' | 'cassette' | 'other';
export type ItemCondition = 'new' | 'like_new' | 'good' | 'acceptable';
export type ItemStatus = 'available' | 'reserved' | 'sold' | 'removed' | 'deleted';
export type DeliveryMethod = 'pickup' | 'delivery' | 'both';
export interface ItemResource {
    type: 'album' | 'song';
    id: string;
    name: string;
    cover: string;
}
export interface ItemSeller {
    id: string;
    nickname: string;
    avatar: string;
    credit?: number;
}
export interface MarketplaceItemVO {
    id: string;
    title: string;
    category: ItemCategory;
    condition: ItemCondition;
    price: number;
    originalPrice?: number;
    description?: string;
    images: string[];
    resource?: ItemResource;
    location: string;
    deliveryMethod: DeliveryMethod;
    status: ItemStatus;
    viewCount: number;
    favoriteCount: number;
    isFavorited: boolean;
    sellerAvailable?: boolean;
    canOpen?: boolean;
    canFavorite?: boolean;
    availabilityMessage?: string;
    seller: ItemSeller;
    createTime: string;
    soldTime?: string;
}
export interface CreateItemForm {
    title: string;
    category: ItemCategory;
    condition: ItemCondition;
    price: number;
    originalPrice?: number;
    description?: string;
    images?: string[] | string;
    resourceType?: 'album' | 'song';
    resourceId?: string | number;
    resourceName?: string;
    resourceCover?: string;
    location?: string;
    deliveryMethod: DeliveryMethod;
}
export interface PageResponse<T> {
    records: T[];
    total: number;
    current: number;
    pages: number;
}
export interface ImageInfo {
    compressed: string;
    original: string;
    thumbnail: string;
    originalSize: string;
    compressedSize: string;
    compressionRatio: string;
}
export function uploadPostImage(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return request<ImageInfo>({
        url: '/music-square/posts/images/upload',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
export function uploadPostImages(files: File[]) {
    const formData = new FormData();
    files.forEach(file => {
        formData.append('files', file);
    });
    return request<ImageInfo[]>({
        url: '/music-square/posts/images/batch',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
export function getPostOriginalImage(postId: string, index: number) {
    return request<string>({
        url: `/music-square/posts/${postId}/images/${index}/original`,
        method: 'GET'
    });
}
export interface VideoInfo {
    '720p': string;
    '480p': string;
    thumbnail: string;
    original?: string;
    originalUrl?: string;
    baseName?: string;
    extension?: string;
    originalSize: number;
    compressedSize?: number;
    compressionRatio?: number;
    duration: number;
    status?: string;
    progress?: number;
    message?: string;
    qualities: QualityItem[];
}
export interface QualityItem {
    label: string;
    value: string;
    url: string;
}
export function uploadVideoPost(file: File, content: string, topics?: string, allowComment: boolean = true) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('content', content);
    if (topics) {
        formData.append('topics', topics);
    }
    formData.append('allowComment', String(allowComment));
    return request<{
        success: boolean;
        videoInfo: VideoInfo;
        baseName: string;
        postId?: string | number;
    }>({
        url: '/music-square/posts/videos/upload',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
export function getVideoStatus(postId: string) {
    return request<{
        status: string;
        message: string;
        progress?: number;
    }>({
        url: `/music-square/posts/videos/${postId}/status`,
        method: 'GET'
    });
}
export function getMyVideoPosts(status?: number | null) {
    return request<any[]>({
        url: '/music-square/posts/videos/my',
        method: 'GET',
        params: { status }
    });
}
export function getVideoPostStats() {
    return request<{
        totalCount: number;
        pendingCount: number;
        publishedCount: number;
        rejectedCount: number;
    }>({
        url: '/music-square/posts/videos/stats',
        method: 'GET'
    });
}
export function retryVideoProcessing(postId: string | number) {
    return request<{
        success: boolean;
        status: string;
        retryCount: number;
    }>({
        url: `/music-square/posts/videos/${postId}/retry`,
        method: 'POST'
    });
}
export function deleteVideoPost(postId: string | number) {
    return request<boolean>({
        url: `/music-square/posts/videos/${postId}`,
        method: 'DELETE'
    });
}
export function getVideoPlayUrl(postId: string, quality: string = '720p') {
    return request<string>({
        url: `/music-square/posts/videos/${postId}/play`,
        method: 'GET',
        params: { quality }
    });
}
export function getPosts(timeRange: TimeRange = 'today', type: string = 'recommend', topicId?: string | number, page: number = 1, size: number = 10) {
    return request<PageResponse<PostVO>>({
        url: '/music-square/posts',
        method: 'GET',
        params: { timeRange, type, topicId, page, size }
    });
}
export function getPostDetail(id: string | number) {
    return request<PostVO>({
        url: `/music-square/posts/${id}/detail`,
        method: 'GET'
    });
}
export function createPost(data: {
    content: string;
    images?: string[] | string;
    imageInfo?: ImageInfo[];
    imageInfos?: ImageInfo[];
    resourceType?: ResourceType;
    resourceId?: string | number;
    topics?: string[] | string;
    visibility?: Visibility;
    allowComment?: boolean | number;
}) {
    const { imageInfo: _imageInfo, imageInfos: _imageInfos, ...rest } = data;
    return request<number>({
        url: '/music-square/posts',
        method: 'POST',
        params: {
            ...rest,
            images: Array.isArray(data.images) ? data.images.join(',') : data.images,
            topics: Array.isArray(data.topics) ? data.topics.join(',') : data.topics
        }
    });
}
export function createListenDiary(data: {
    content: string;
    images?: string[] | string;
    topics?: string[] | string;
}) {
    return request<number>({
        url: '/music-square/posts/diary',
        method: 'POST',
        params: {
            ...data,
            images: Array.isArray(data.images) ? data.images.join(',') : data.images,
            topics: Array.isArray(data.topics) ? data.topics.join(',') : data.topics
        }
    });
}
export function deletePost(id: string) {
    return request<boolean>({
        url: `/music-square/posts/${id}`,
        method: 'DELETE'
    });
}
export function likePost(id: string) {
    return request<boolean>({
        url: `/music-square/posts/${id}/like`,
        method: 'POST'
    });
}
export function updatePostCommentSetting(id: string, allowComment: boolean) {
    return request<boolean>({
        url: `/music-square/posts/${id}/comment-settings`,
        method: 'PATCH',
        params: { allowComment }
    });
}
export function updateOfficialPostCommentSetting(id: string, closed: boolean) {
    return request<boolean>({
        url: `/music-square/posts/${id}/official-comment`,
        method: 'PATCH',
        params: { closed }
    });
}
export function unlikePost(id: string) {
    return request<boolean>({
        url: `/music-square/posts/${id}/like`,
        method: 'DELETE'
    });
}
export function getTopicDetail(id: string) {
    return request<TopicVO>({
        url: `/music-square/topics/${id}`,
        method: 'GET'
    });
}
export function getHotTopics(limit: number = 10) {
    return request<TopicVO[]>({
        url: '/music-square/topics/hot',
        method: 'GET',
        params: { limit }
    });
}
export function getTopicPosts(topicId: string | number, page: number = 1, size: number = 10) {
    return getPosts('all', 'topic', topicId, page, size);
}
export function followTopic(id: string) {
    return request<boolean>({
        url: `/music-square/topics/${id}/follow`,
        method: 'POST'
    });
}
export function unfollowTopic(id: string) {
    return request<boolean>({
        url: `/music-square/topics/${id}/follow`,
        method: 'DELETE'
    });
}
export function getMarketplaceItems(params: {
    category?: string;
    condition?: string;
    sortBy?: string;
    keyword?: string;
    page?: number;
    size?: number;
}) {
    return request<PageResponse<MarketplaceItemVO>>({
        url: '/music-square/marketplace',
        method: 'GET',
        params
    });
}
export function getMarketplaceItemDetail(id: string | number) {
    return request<MarketplaceItemVO>({
        url: `/music-square/marketplace/${id}`,
        method: 'GET'
    });
}
export function createMarketplaceItem(data: CreateItemForm) {
    return request<number>({
        url: '/music-square/marketplace',
        method: 'POST',
        params: {
            ...data,
            images: Array.isArray(data.images) ? data.images.join(',') : data.images
        }
    });
}
export function updateMarketplaceItem(id: string | number, data: CreateItemForm) {
    return request<boolean>({
        url: `/music-square/marketplace/${id}/edit`,
        method: 'PUT',
        params: {
            ...data,
            images: Array.isArray(data.images) ? data.images.join(',') : data.images
        }
    });
}
export function updateMarketplaceItemStatus(id: string | number, status: ItemStatus) {
    return request<boolean>({
        url: `/music-square/marketplace/${id}/status`,
        method: 'PUT',
        params: { status }
    });
}
export function deleteMarketplaceItem(id: string | number) {
    return request<boolean>({
        url: `/music-square/marketplace/${id}`,
        method: 'DELETE'
    });
}
export function favoriteMarketplaceItem(id: string | number) {
    return request<boolean>({
        url: `/music-square/marketplace/${id}/favorite`,
        method: 'POST'
    });
}
export function unfavoriteMarketplaceItem(id: string | number) {
    return request<boolean>({
        url: `/music-square/marketplace/${id}/favorite`,
        method: 'DELETE'
    });
}
export function getMyMarketplaceItems(params: {
    status?: string;
    page?: number;
    size?: number;
}) {
    return request<PageResponse<MarketplaceItemVO>>({
        url: '/music-square/marketplace/my',
        method: 'GET',
        params
    });
}
export function getMyFavoriteItems(params: {
    page?: number;
    size?: number;
}) {
    return request<PageResponse<MarketplaceItemVO>>({
        url: '/music-square/marketplace/favorites',
        method: 'GET',
        params
    });
}
export interface OperationStat {
    operation: string;
    count: number;
    limit: number;
    remaining: number;
}
export interface OperationStatsResponse {
    userId: string;
    timeRangeHours: number;
    operations: OperationStat[];
}
export function getOperationStats(hours: number = 24) {
    return request<OperationStatsResponse>({
        url: '/music-square/monitor/operation-stats',
        method: 'GET',
        params: { hours }
    });
}
export function getFeaturedEvents(limit: number = 10) {
    return request<HotEventVO[]>({
        url: '/music-square/events/featured',
        method: 'GET',
        params: { limit }
    });
}
export function getEventDetail(eventId: string) {
    return request<HotEventVO>({
        url: `/music-square/events/${eventId}`,
        method: 'GET'
    });
}
export function getHotVotedSongs(limit: number = 20) {
    return request<VoteStatsVO[]>({
        url: '/music-square/vote/hot',
        method: 'GET',
        params: { limit }
    });
}
export function voteSong(songId: string) {
    return request<boolean>({
        url: '/music-square/vote',
        method: 'POST',
        params: { songId }
    });
}
export function getRecommendUsers(limit: number = 10) {
    return request<{
        id: string;
        nickname: string;
        avatar: string;
        fansCount: number;
        followerCount: number;
        followingCount: number;
        isFollowing: boolean;
    }[]>({
        url: '/music-square/recommend-users',
        method: 'GET',
        params: { limit }
    });
}
export const musicSquareApi = {
    getPosts,
    getPostDetail,
    createPost,
    createListenDiary,
    deletePost,
    likePost,
    updatePostCommentSetting,
    updateOfficialPostCommentSetting,
    unlikePost,
    uploadPostImage,
    uploadPostImages,
    getPostOriginalImage,
    uploadVideoPost,
    getVideoStatus,
    getVideoPlayUrl,
    getTopicDetail,
    getHotTopics,
    getTopicPosts,
    followTopic,
    unfollowTopic,
    getMarketplaceItems,
    getMarketplaceItemDetail,
    createMarketplaceItem,
    updateMarketplaceItem,
    updateMarketplaceItemStatus,
    deleteMarketplaceItem,
    favoriteMarketplaceItem,
    unfavoriteMarketplaceItem,
    getMyMarketplaceItems,
    getMyFavoriteItems,
    getOperationStats,
    getFeaturedEvents,
    getEventDetail,
    getHotVotedSongs,
    voteSong,
    getRecommendUsers
};
export { musicSquareWorkApi } from './musicSquareWork';
