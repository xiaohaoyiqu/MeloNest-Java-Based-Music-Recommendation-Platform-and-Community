import { request } from '@/utils/request';
export interface FriendVO {
    userId: string;
    username: string;
    nickname: string;
    avatar: string;
    signature: string;
    role: string;
    status?: number;
    userType?: number;
    isBanned?: boolean | number;
    isCreator: boolean;
    creatorStatus: string;
    creatorType: string;
    isVip: boolean;
    vipLevel: number;
    creditScore: number;
    friendGroup: string;
    remark: string;
    specialMark: string;
    relationType: string;
    isMutual: boolean;
    fansCount: number;
    followingCount: number;
    friendSince: string;
}
export interface FriendRequestVO {
    id: string;
    fromUserId: string;
    fromUsername: string;
    fromNickname: string;
    fromAvatar: string;
    fromSignature: string;
    fromStatus?: number;
    fromUserType?: number;
    fromIsBanned?: boolean | number;
    toUserId: string;
    status: string;
    message: string;
    createTime: string;
    updateTime: string;
}
export interface FriendGroupVO {
    id: string;
    groupName: string;
    count: number;
}
export function sendFriendRequest(targetUserId: string) {
    return request<boolean>({
        url: `/friend/request/${targetUserId}`,
        method: 'POST'
    });
}
export function handleFriendRequest(requestId: string, approved: boolean) {
    return request<boolean>({
        url: `/friend/request/${requestId}`,
        method: 'PUT',
        params: { approved }
    });
}
export function getFriendList(groupId?: string | number) {
    return request<FriendVO[]>({
        url: '/friend/list',
        method: 'GET',
        params: { groupId }
    });
}
export function getFriendRequests() {
    return request<FriendRequestVO[]>({
        url: '/friend/requests',
        method: 'GET'
    });
}
export function unfriend(friendId: string) {
    return request<boolean>({
        url: `/friend/${friendId}`,
        method: 'DELETE'
    });
}
export function setSpecialMark(friendId: string, specialMark?: string | null) {
    return request<boolean>({
        url: `/friend/special/${friendId}`,
        method: 'PUT',
        params: { specialMark }
    });
}
export function setFriendGroup(friendId: string, groupId?: string | number) {
    return request<boolean>({
        url: '/friend/group',
        method: 'PUT',
        data: { friendId, groupId }
    });
}
export function setFriendRemark(friendId: string, remark?: string) {
    return request<boolean>({
        url: '/friend/remark',
        method: 'PUT',
        data: { friendId, remark }
    });
}
export function blockFriend(friendId: string, blocked: boolean) {
    return request<boolean>({
        url: `/friend/block/${friendId}`,
        method: 'PUT',
        params: { blocked }
    });
}
export function isFriend(targetUserId: string) {
    return request<boolean>({
        url: `/friend/check/${targetUserId}`,
        method: 'GET'
    });
}
export function isSpecialFollow(friendId: string) {
    return request<boolean>({
        url: `/friend/special/check/${friendId}`,
        method: 'GET'
    });
}
export function getSpecialFollows() {
    return request<FriendVO[]>({
        url: '/friend/special/list',
        method: 'GET'
    });
}
export function getMutualFriendsNotSpecial() {
    return request<FriendVO[]>({
        url: '/friend/mutual/not-special',
        method: 'GET'
    });
}
export function getFriendGroups() {
    return request<FriendGroupVO[]>({
        url: '/friend/groups',
        method: 'GET'
    });
}
export function createFriendGroup(groupName: string) {
    return request<number>({
        url: '/friend/group',
        method: 'POST',
        data: { groupName }
    });
}
export const friendApi = {
    sendFriendRequest: (targetUserId: string) => {
        return request<boolean>({
            url: `/friend/request/${targetUserId}`,
            method: 'POST'
        });
    },
    handleFriendRequest: (requestId: string, approved: boolean) => {
        return request<boolean>({
            url: `/friend/request/${requestId}`,
            method: 'PUT',
            params: { approved }
        });
    },
    getFriendList: (groupId?: string | number) => {
        return request<FriendVO[]>({
            url: '/friend/list',
            method: 'GET',
            params: { groupId }
        });
    },
    getFriendRequests: () => {
        return request<FriendRequestVO[]>({
            url: '/friend/requests',
            method: 'GET'
        });
    },
    unfriend: (friendId: string) => {
        return request({
            url: `/friend/${friendId}`,
            method: 'DELETE'
        });
    },
    setSpecialMark: (friendId: string, specialMark?: string | null) => {
        return request({
            url: `/friend/special/${friendId}`,
            method: 'PUT',
            params: { specialMark }
        });
    },
    setFriendGroup: (friendId: string, groupId?: string | number) => {
        return request({
            url: '/friend/group',
            method: 'PUT',
            data: { friendId, groupId }
        });
    },
    setFriendRemark: (friendId: string, remark?: string) => {
        return request({
            url: '/friend/remark',
            method: 'PUT',
            data: { friendId, remark }
        });
    },
    blockFriend: (friendId: string, blocked: boolean) => {
        return request({
            url: `/friend/block/${friendId}`,
            method: 'PUT',
            params: { blocked }
        });
    },
    isFriend: (targetUserId: string) => {
        return request<boolean>({
            url: `/friend/check/${targetUserId}`,
            method: 'GET'
        });
    },
    isSpecialFollow: (friendId: string) => {
        return request<boolean>({
            url: `/friend/special/check/${friendId}`,
            method: 'GET'
        });
    },
    getSpecialFollows: () => {
        return request<FriendVO[]>({
            url: '/friend/special/list',
            method: 'GET'
        });
    },
    getMutualFriendsNotSpecial: () => {
        return request<FriendVO[]>({
            url: '/friend/mutual/not-special',
            method: 'GET'
        });
    },
    getFriendGroups: () => {
        return request<FriendGroupVO[]>({
            url: '/friend/groups',
            method: 'GET'
        });
    },
    createFriendGroup: (groupName: string) => {
        return request<number>({
            url: '/friend/group',
            method: 'POST',
            data: { groupName }
        });
    },
    deleteFriendGroup: (groupId: string) => {
        return request({
            url: `/friend/group/${groupId}`,
            method: 'DELETE'
        });
    }
};
