import { request } from '@/utils/request';
import type { SongInfo } from '@/types/song';
import type { ExactCount } from '@/utils/exactCount';
export interface PlaylistPaidSettingsRequest {
    playlistId: string | number;
    monthlyPrice: number;
}
export interface PlaylistSubscriberInfo {
    id: string | number;
    userId?: string | number;
    nickname?: string;
    avatar?: string;
    subscribeTime?: string;
    expireTime?: string;
    status?: string;
    subscribeType?: string;
    price?: number;
    isActive?: boolean;
}
export type CollaboratorRole = 'owner' | 'editor' | 'viewer';
export interface CollaboratorInfo {
    userId: string | number;
    nickname?: string;
    avatar?: string;
    role: CollaboratorRole;
    roleName: string;
    canAdd?: boolean;
    canRemove?: boolean;
    canEdit?: boolean;
    joinedTime: string;
    isInviter?: boolean;
}
export interface CollaboratorPermissions {
    canAdd: boolean;
    canRemove: boolean;
    canEdit: boolean;
}
export interface PlaylistInfo {
    id: string | number;
    uuid?: string;
    name: string;
    description?: string;
    cover?: string;
    songCount: number;
    playCount: ExactCount;
    favoriteCount: ExactCount;
    visitCount?: ExactCount;
    isPublic: number;
    userId: string | number;
    creatorId?: string | number;
    creatorName?: string;
    creatorAvatar?: string;
    mainType?: string;
    tags?: string[];
    createTime?: string;
    updateTime?: string;
    songs?: SongInfo[];
    orderVersion?: string;
    type?: number;
    isFavorite?: boolean;
    currentPage?: number;
    pageSize?: number;
    totalPages?: number;
    filteredSongCount?: number;
    contentAccessible?: boolean;
    priority?: number;
    isPaid?: boolean | number;
    paidResourceId?: string | number;
    price?: number;
    subscribePeriod?: number;
    allowDownload?: number;
    allowComment?: number;
    allowShare?: number;
    intro?: string;
    category?: string;
    language?: string;
    primaryLanguage?: string;
    contentLanguages?: string[];
    languageCounts?: Record<string, number>;
    subscriberCount?: number;
    totalSubscribers?: number;
    activeSubscribers?: number;
    monthlyRevenue?: number;
    totalRevenue?: number;
    subscribers?: PlaylistSubscriberInfo[];
}
export interface CreatePlaylistRequest {
    name: string;
    description?: string;
    cover?: string;
    isPublic?: number;
    tags?: string[];
    mainType?: string;
}
export function getUserPlaylists() {
    return request<PlaylistInfo[]>({
        url: '/playlist/my',
        method: 'GET'
    });
}
export function createPlaylist(data: CreatePlaylistRequest) {
    return request<number>({
        url: '/playlist',
        method: 'POST',
        data
    });
}
export function addSongToPlaylist(playlistId: string | number, songId: string | number) {
    return request<number>({
        url: `/playlist/${playlistId}/songs`,
        method: 'POST',
        data: [songId]
    });
}
export function addSongsToPlaylist(playlistId: string | number, songIds: Array<string | number>) {
    return request<number>({
        url: `/playlist/${playlistId}/songs`,
        method: 'POST',
        data: songIds
    });
}
export function removeSongFromPlaylist(playlistId: string | number, songId: string | number) {
    return request<void>({
        url: `/playlist/${playlistId}/songs/${songId}`,
        method: 'DELETE'
    });
}
export function removeSongsFromPlaylist(playlistId: string | number, songIds: Array<string | number>) {
    return request<number>({
        url: `/playlist/${playlistId}/songs`,
        method: 'DELETE',
        data: songIds
    });
}
export function updatePlaylist(data: Partial<PlaylistInfo> & {
    intro?: string;
    category?: string;
    language?: string;
}) {
    return request<void>({
        url: '/playlist',
        method: 'PUT',
        data
    });
}
export function deletePlaylist(playlistId: string | number) {
    return request<void>({
        url: `/playlist/${playlistId}`,
        method: 'DELETE'
    });
}
export function favoritePlaylist(playlistId: string | number, isFavorite: boolean = true) {
    return request<void>({
        url: `/playlist/${playlistId}/favorite`,
        method: isFavorite ? 'POST' : 'DELETE'
    });
}
export function unfavoritePlaylist(playlistId: string | number) {
    return favoritePlaylist(playlistId, false);
}
export function getPlaylistDetail(playlistId: string | number, params?: {
    page?: number;
    size?: number;
    keyword?: string;
    language?: string;
    sortBy?: 'default' | 'name' | 'artist' | 'duration';
}, options?: {
    signal?: AbortSignal;
}) {
    return request<PlaylistInfo>({
        url: `/playlist/info/${playlistId}`,
        method: 'GET',
        ...(params ? { params } : {}),
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function getPlaylistById(playlistId: string | number, params?: Parameters<typeof getPlaylistDetail>[1], options?: {
    signal?: AbortSignal;
}) {
    return getPlaylistDetail(playlistId, params, options);
}
export function getPlaylistPage(params: {
    page: number;
    size: number;
    keyword?: string;
    language?: string;
    languages?: string[];
    languageMode?: 'any' | 'all';
    tag?: string;
    category?: string;
    minSongCount?: number;
    maxSongCount?: number;
    paymentType?: 'free' | 'paid';
    sortField?: 'time' | 'hot' | 'name';
    sortOrder?: 'asc' | 'desc';
}, options: {
    signal?: AbortSignal;
} = {}) {
    return request<{
        records: PlaylistInfo[];
        total: number;
        size: number;
        current: number;
        pages: number;
    }>({
        url: '/playlist/page',
        method: 'GET',
        params,
        ...(options.signal ? { signal: options.signal } : {})
    });
}
function fetchFavoritePlaylist() {
    return request<PlaylistInfo>({
        url: '/playlist/favorite',
        method: 'GET'
    });
}
let pendingFavoritePlaylistRequest: ReturnType<typeof fetchFavoritePlaylist> | null = null;
export function getFavoritePlaylist() {
    if (pendingFavoritePlaylistRequest)
        return pendingFavoritePlaylistRequest;
    pendingFavoritePlaylistRequest = fetchFavoritePlaylist();
    void pendingFavoritePlaylistRequest.then(() => { pendingFavoritePlaylistRequest = null; }, () => { pendingFavoritePlaylistRequest = null; });
    return pendingFavoritePlaylistRequest;
}
export function getFavoritePlaylists() {
    return request<PlaylistInfo[]>({
        url: '/playlist/favorites',
        method: 'GET'
    });
}
export const getFavoritePlaylistss = getFavoritePlaylists;
export function getHotPlaylists(typeOrLimit?: string | number, limit = 10) {
    const params: Record<string, string | number> = {};
    if (typeof typeOrLimit === 'number') {
        params.limit = typeOrLimit;
    }
    else {
        if (typeOrLimit)
            params.type = typeOrLimit;
        params.limit = limit;
    }
    return request<PlaylistInfo[]>({
        url: '/playlist/hot',
        method: 'GET',
        params
    });
}
export function copyPlaylist(sourcePlaylistId: string | number, targetPlaylistId: string | number) {
    return request<number>({
        url: `/playlist/${sourcePlaylistId}/copy/${targetPlaylistId}`,
        method: 'POST'
    });
}
export function movePlaylist(sourcePlaylistId: string | number, targetPlaylistId: string | number) {
    return request<number>({
        url: `/playlist/${sourcePlaylistId}/move/${targetPlaylistId}`,
        method: 'POST'
    });
}
export interface PlaylistCopyMoveResult {
    added: number;
    skipped: number;
    failed?: number;
    total?: number;
}
export function copySongsToPlaylist(sourcePlaylistId: string | number, targetPlaylistId: string | number, songIds: Array<string | number>) {
    return request<PlaylistCopyMoveResult>({
        url: `/playlist/${sourcePlaylistId}/copy/${targetPlaylistId}`,
        method: 'POST',
        data: songIds
    });
}
export function moveSongsToPlaylist(sourcePlaylistId: string | number, targetPlaylistId: string | number, songIds: Array<string | number>) {
    return request<PlaylistCopyMoveResult>({
        url: `/playlist/${sourcePlaylistId}/move/${targetPlaylistId}`,
        method: 'POST',
        data: songIds
    });
}
export function updatePlaylistOrder(playlistId: string | number, songIds: Array<string | number>, expectedOrderVersion: string) {
    return request<void>({
        url: '/playlist/order',
        method: 'PUT',
        data: { playlistId, songIds, expectedOrderVersion }
    });
}
export function uploadPlaylistCover(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return request<string>({
        url: '/playlist/cover/upload',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
export function setPlaylistPaid(data: PlaylistPaidSettingsRequest) {
    return request<void>({
        url: '/playlist/set-paid',
        method: 'POST',
        data
    });
}
export function updatePlaylistPaidSettings(data: PlaylistPaidSettingsRequest) {
    return request<void>({
        url: '/playlist/paid-settings',
        method: 'PUT',
        data
    });
}
export function enableCollaboration(playlistId: string | number) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/enable`,
        method: 'POST'
    });
}
export function disableCollaboration(playlistId: string | number) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/disable`,
        method: 'POST'
    });
}
export function inviteCollaborator(playlistId: string | number, userId: string | number, role: Exclude<CollaboratorRole, 'owner'> = 'editor') {
    return request<void>({
        url: `/playlist/collab/${playlistId}/invite`,
        method: 'POST',
        params: { userId, role }
    });
}
export function acceptCollabInvitation(playlistId: string | number) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/accept`,
        method: 'POST'
    });
}
export function declineCollabInvitation(playlistId: string | number) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/decline`,
        method: 'POST'
    });
}
export function removeCollaborator(playlistId: string | number, userId: string | number) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/collaborator/${userId}`,
        method: 'DELETE'
    });
}
export function leaveCollaboration(playlistId: string | number) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/leave`,
        method: 'POST'
    });
}
export function updateCollaboratorPermission(playlistId: string | number, userId: string | number, permissions: CollaboratorPermissions) {
    return request<void>({
        url: `/playlist/collab/${playlistId}/collaborator/${userId}`,
        method: 'PUT',
        data: permissions
    });
}
export function getCollaborators(playlistId: string | number) {
    return request<CollaboratorInfo[]>({
        url: `/playlist/collab/${playlistId}/collaborators`,
        method: 'GET'
    });
}
export function getPendingInvitations() {
    return request({
        url: '/playlist/collab/invitations/pending',
        method: 'GET'
    });
}
export function getMyCollabPlaylists() {
    return request<PlaylistInfo[]>({
        url: '/playlist/collab/my',
        method: 'GET'
    });
}
export const playlistApi = {
    getUserPlaylists,
    createPlaylist,
    addSongToPlaylist,
    addSongsToPlaylist,
    removeSongFromPlaylist,
    removeSongsFromPlaylist,
    updatePlaylist,
    deletePlaylist,
    favoritePlaylist,
    unfavoritePlaylist,
    getFavoritePlaylist,
    getFavoritePlaylists,
    getFavoritePlaylistss,
    getPlaylistDetail,
    getPlaylistById,
    getPlaylistPage,
    getHotPlaylists,
    copyPlaylist,
    movePlaylist,
    copySongsToPlaylist,
    moveSongsToPlaylist,
    updatePlaylistOrder,
    uploadPlaylistCover,
    setPlaylistPaid,
    updatePlaylistPaidSettings,
    enableCollaboration,
    disableCollaboration,
    inviteCollaborator,
    acceptCollabInvitation,
    declineCollabInvitation,
    removeCollaborator,
    leaveCollaboration,
    updateCollaboratorPermission,
    getCollaborators,
    getPendingInvitations,
    getMyCollabPlaylists
};
