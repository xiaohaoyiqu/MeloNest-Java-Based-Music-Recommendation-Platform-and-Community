import { request } from '@/utils/request';
export interface SocialRecommendPreference {
    enabled: boolean;
    showSource: boolean;
    allowShared: boolean;
    keywordRetentionDays: number;
}
export type MessageType = 'text' | 'image' | 'emoji' | 'song' | 'album' | 'mv' | 'playlist';
export type ID = string;
export interface Message {
    id: ID;
    senderId: ID;
    receiverId: ID;
    senderName?: string;
    senderAvatar?: string;
    receiverName?: string;
    messageType: MessageType;
    content: string | null;
    resourceId?: ID;
    attachmentAssetId?: ID;
    resourceAvailable?: boolean;
    resourceData?: string;
    songData?: string;
    albumData?: string;
    mvData?: string;
    playlistData?: string;
    isRead: boolean;
    isRecalled: boolean;
    status?: string;
    createTime: string;
    extraData?: string;
}
export interface Conversation {
    conversationId: ID;
    otherUserId: ID;
    otherUserNickname: string;
    otherUserName: string;
    otherUserAvatar: string | null;
    otherUserStatus?: number;
    otherUserIsBanned?: boolean | number;
    otherUserDeleted?: boolean | number;
    lastMessage: string | null;
    lastMessageType: MessageType | null;
    lastMessageTime: string | null;
    unreadCount: number;
    isPinned: boolean;
    isBlocked: boolean;
}
type RawMessage = Omit<Message, 'id' | 'senderId' | 'receiverId' | 'resourceId' | 'attachmentAssetId' | 'isRead' | 'isRecalled'> & {
    id: string | number;
    senderId: string | number;
    receiverId: string | number;
    resourceId?: string | number | null;
    attachmentAssetId?: string | number | null;
    isRead: boolean | number | string;
    isRecalled: boolean | number | string;
};
type RawConversation = Omit<Conversation, 'conversationId' | 'otherUserId'> & {
    conversationId: string | number;
    otherUserId: string | number;
};
function booleanValue(value: boolean | number | string): boolean {
    return value === true || value === 1 || value === '1';
}
function normalizeMessage(item: RawMessage): Message {
    return {
        ...item,
        id: String(item.id),
        senderId: String(item.senderId),
        receiverId: String(item.receiverId),
        resourceId: item.resourceId === null || item.resourceId === undefined ? undefined : String(item.resourceId),
        attachmentAssetId: item.attachmentAssetId === null || item.attachmentAssetId === undefined
            ? undefined
            : String(item.attachmentAssetId),
        isRead: booleanValue(item.isRead),
        isRecalled: booleanValue(item.isRecalled)
    };
}
function normalizeConversation(item: RawConversation): Conversation {
    return {
        ...item,
        conversationId: String(item.conversationId),
        otherUserId: String(item.otherUserId)
    };
}
export function sendTextMessage(receiverId: ID, content: string) {
    return request<ID>({
        url: '/message/send/text',
        method: 'POST',
        data: { receiverId, content }
    });
}
export function sendImageMessage(receiverId: ID, attachmentAssetId: ID) {
    return request<ID>({
        url: '/message/image',
        method: 'POST',
        data: { receiverId, attachmentAssetId }
    });
}
export function sendEmojiMessage(receiverId: ID, emojiCode: string) {
    return request<ID>({
        url: '/message/send/emoji',
        method: 'POST',
        data: { receiverId, emojiCode }
    });
}
export function sendSongMessage(receiverId: ID, songId: ID, songData: string) {
    return request<ID>({
        url: '/message/send/song',
        method: 'POST',
        data: { receiverId, songId, songData }
    });
}
export function sendAlbumMessage(receiverId: ID, albumId: ID, albumData: string) {
    return request<ID>({
        url: '/message/send/album',
        method: 'POST',
        data: { receiverId, albumId, albumData }
    });
}
export function sendMvMessage(receiverId: ID, mvId: ID, mvData: string) {
    return request<ID>({
        url: '/message/send/mv',
        method: 'POST',
        data: { receiverId, mvId, mvData }
    });
}
export function sendPlaylistMessage(receiverId: ID, playlistId: ID, playlistData: string) {
    return request<ID>({
        url: '/message/send/playlist',
        method: 'POST',
        data: { receiverId, playlistId, playlistData }
    });
}
export async function getChatMessages(otherUserId: ID, page = 1, size = 20) {
    const response = await request<{
        records: RawMessage[];
        total: number;
        current: number;
        size: number;
        pages: number;
    }>({
        url: `/message/chat/${otherUserId}`,
        method: 'GET',
        params: { page, size }
    });
    return {
        ...response,
        data: {
            ...response.data,
            records: (response.data?.records || []).map(normalizeMessage)
        }
    };
}
export async function getConversations() {
    const response = await request<RawConversation[]>({
        url: '/message/conversations',
        method: 'GET'
    });
    return {
        ...response,
        data: (Array.isArray(response.data) ? response.data : []).map(normalizeConversation)
    };
}
export function markAsRead(messageId: ID) {
    return request<boolean>({
        url: `/message/read/${messageId}`,
        method: 'POST'
    });
}
export function markAllAsRead(otherUserId: ID) {
    return request<number>({
        url: `/message/read-all/${otherUserId}`,
        method: 'POST'
    });
}
export function recallMessage(messageId: ID) {
    return request<boolean>({
        url: `/message/recall/${messageId}`,
        method: 'POST'
    });
}
export function deleteMessage(messageId: ID) {
    return request<boolean>({
        url: `/message/${messageId}`,
        method: 'DELETE'
    });
}
export function batchDeleteMessages(messageIds: ID[]) {
    return request<number>({
        url: '/message/batch-delete',
        method: 'POST',
        data: { messageIds }
    });
}
export function forwardMessages(messageIds: ID[], receiverId: ID) {
    return request<ID[]>({
        url: '/message/forward',
        method: 'POST',
        data: { messageIds, receiverId }
    });
}
export function getTotalUnreadCount() {
    return request<number>({
        url: '/message/unread/count',
        method: 'GET'
    });
}
export function getUnreadCountFromUser(otherUserId: ID) {
    return request<number>({
        url: `/message/unread/count/${otherUserId}`,
        method: 'GET'
    });
}
export function setConversationPinned(otherUserId: ID, pinned: boolean) {
    return request<boolean>({
        url: `/message/pin/${otherUserId}`,
        method: 'POST',
        params: { pinned }
    });
}
export function setUserBlocked(otherUserId: ID, blocked: boolean) {
    return request<boolean>({
        url: `/message/block/${otherUserId}`,
        method: 'POST',
        params: { blocked }
    });
}
export function clearChatHistory(otherUserId: ID) {
    return request<number>({
        url: `/message/chat/${otherUserId}`,
        method: 'DELETE'
    });
}
export async function searchMessages(keyword: string, page = 1, size = 20) {
    const response = await request<{
        records: RawMessage[];
        total: number;
        current: number;
        size: number;
        pages: number;
    }>({
        url: '/message/search',
        method: 'GET',
        params: { keyword, page, size }
    });
    return {
        ...response,
        data: {
            ...response.data,
            records: (response.data?.records || []).map(normalizeMessage)
        }
    };
}
export const messageApi = {
    sendTextMessage,
    sendImageMessage,
    sendEmojiMessage,
    sendSongMessage,
    sendAlbumMessage,
    sendMvMessage,
    sendPlaylistMessage,
    getChatMessages,
    getConversations,
    markAsRead,
    markAllAsRead,
    recallMessage,
    deleteMessage,
    batchDeleteMessages,
    forwardMessages,
    getTotalUnreadCount,
    getUnreadCountFromUser,
    setConversationPinned,
    setUserBlocked,
    clearChatHistory,
    searchMessages
};
export function getSocialRecommendPreference() {
    return request<SocialRecommendPreference>({
        url: '/message/social/recommend/preference',
        method: 'GET'
    });
}
export function setSocialRecommendEnabled(enabled: boolean) {
    return request<void>({
        url: '/message/social/recommend/enabled',
        method: 'POST',
        data: { enabled }
    });
}
export function saveSocialRecommendPreference(preference: SocialRecommendPreference) {
    return request<void>({
        url: '/message/social/recommend/preference',
        method: 'POST',
        data: preference
    });
}
export function clearUserKeywords() {
    return request<void>({
        url: '/message/social/recommend/keywords',
        method: 'DELETE'
    });
}
export const socialRecommendApi = {
    getSocialRecommendPreference,
    setSocialRecommendEnabled,
    saveSocialRecommendPreference,
    clearUserKeywords
};
