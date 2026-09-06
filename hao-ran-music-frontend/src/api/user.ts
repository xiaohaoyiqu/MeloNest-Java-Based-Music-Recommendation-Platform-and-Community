import { request } from '@/utils/request';
export interface LoginRequest {
    username: string;
    password: string;
    captchaCode?: string;
    captchaId?: string;
}
export interface LoginResponse {
    token?: never;
    userInfo: UserInfo;
    tokenType: string;
    expiresIn: number;
}
export interface UserInfo {
    id: string | number;
    uuid: string;
    username: string;
    nickname: string;
    phone: string;
    email: string;
    avatar: string;
    signature: string;
    gender: number;
    birthday: string;
    status: number;
    userType?: number;
    isBanned?: boolean | number;
    banReason?: string;
    banStartTime?: string;
    banEndTime?: string;
    fansCount: number;
    followingCount: number;
    wallpaper: string;
    createTime: string;
    updateTime: string;
    role: string;
    isCreator?: boolean;
    creatorStatus?: string;
    creditScore?: number;
    isVip?: boolean | number;
    vipStatus?: string | number;
    vipExpireTime?: string;
    verifiedInfo?: import('./verified').VerifiedInfoVO;
}
export type UserRelationType = 'self' | 'normal' | 'following' | 'follower' | 'mutual' | 'friend' | 'friend_pending' | 'blocked' | 'blocked_by' | 'trade';
export interface UserRelation {
    self: boolean;
    following: boolean;
    follower: boolean;
    mutual: boolean;
    friend: boolean;
    friendRequestPending: boolean;
    blacklisted: boolean;
    blockedByMe: boolean;
    blockedMe: boolean;
    trade: boolean;
    relationType: UserRelationType;
}
export interface PageResult<T> {
    records: T[];
    total: number;
    size: number;
    current: number;
    pages: number;
}
export interface RegisterRequest {
    username: string;
    password: string;
    confirmPassword: string;
    phone?: string;
    email?: string;
    nickname?: string;
    captchaCode?: string;
    captchaId?: string;
}
export interface ResetPasswordRequest {
    phone: string;
    newPassword: string;
    confirmPassword: string;
    verifyCode: string;
}
export interface SendVerifyCodeRequest {
    phone: string;
    type: 'register' | 'reset' | 'login';
}
export interface SendVerifyCodeResponse {
    channel: string;
    scene: string;
    provider: string;
    expireIn: number;
    message: string;
}
export interface SendEmailCodeRequest {
    email: string;
    type: 'register' | 'reset' | 'login' | 'change_email';
}
export type SendEmailCodeResponse = SendVerifyCodeResponse;
export interface VerifyEmailCodeRequest {
    email: string;
    type: 'register' | 'reset' | 'login' | 'change_email';
    verifyCode: string;
}
export interface AuthRiskStatusResponse {
    riskNeedCaptcha: boolean;
    captchaEnforced: boolean;
    reasons: string[];
    accountFailureCount: number;
    ipFailureCount: number;
}
export interface CaptchaCheckResponse {
    needCaptcha: boolean;
    baseNeedCaptcha: boolean;
    riskNeedCaptcha: boolean;
    captchaEnforced: boolean;
    scene: string;
    risk: AuthRiskStatusResponse;
}
export interface CaptchaRequest {
    type?: 'arithmetic' | 'image' | 'slide';
    scene?: 'login' | 'register' | 'reset' | string;
}
export interface CaptchaResponse {
    type: string;
    captcha?: string;
    answer?: number | string;
    expireAt?: number;
}
export function login(data: LoginRequest) {
    return request<LoginResponse>({
        url: '/auth/login',
        method: 'POST',
        data
    });
}
export function register(data: RegisterRequest) {
    return request<UserInfo>({
        url: '/auth/register',
        method: 'POST',
        data
    });
}
export function logout() {
    return request<void>({
        url: '/auth/logout',
        method: 'POST'
    });
}
export function getUserList(keyword?: string, page = 1, size = 20) {
    return request({
        url: '/user/list',
        method: 'GET',
        params: { keyword, page, size }
    });
}
export function getCurrentUserInfo() {
    return request<UserInfo>({
        url: '/user/current',
        method: 'GET',
        timeout: 10000
    });
}
export function getUserById(id: string) {
    return request<UserInfo>({
        url: `/user/info/${id}`,
        method: 'GET'
    });
}
export function sendVerifyCode(data: SendVerifyCodeRequest) {
    return request<SendVerifyCodeResponse>({
        url: '/auth/send-code',
        method: 'POST',
        data
    });
}
export function sendEmailCode(data: SendEmailCodeRequest) {
    return request<SendEmailCodeResponse>({
        url: '/auth/email/send-code',
        method: 'POST',
        data
    });
}
export function verifyEmailCode(data: VerifyEmailCodeRequest) {
    return request<void>({
        url: '/auth/email/verify',
        method: 'POST',
        data
    });
}
export function checkNeedCaptcha(params: {
    scene?: string;
    account?: string;
} = {}) {
    return request<CaptchaCheckResponse>({
        url: '/auth/captcha/check',
        method: 'GET',
        params
    });
}
export function getAuthRiskStatus(account?: string) {
    return request<AuthRiskStatusResponse>({
        url: '/auth/risk/status',
        method: 'GET',
        params: { account }
    });
}
export function getCaptcha(params: CaptchaRequest = {}) {
    return request<CaptchaResponse>({
        url: '/auth/captcha',
        method: 'GET',
        params
    });
}
export function resetPassword(data: ResetPasswordRequest) {
    return request<void>({
        url: '/auth/reset-password',
        method: 'POST',
        data
    });
}
export function followUser(followeeId: string) {
    return request<void>({
        url: `/user/follow/${followeeId}`,
        method: 'POST'
    });
}
export function unfollowUser(followeeId: string) {
    return request<void>({
        url: `/user/follow/${followeeId}`,
        method: 'DELETE'
    });
}
export function getUserRelation(targetUserId: string | number) {
    return request<UserRelation>({
        url: `/user/relation/${targetUserId}`,
        method: 'GET'
    });
}
export function removeFollower(followerId: string | number) {
    return request<void>({
        url: `/user/follower/remove`,
        method: 'DELETE',
        params: { followerId }
    });
}
export interface SendResetCodeRequest {
    phone: string;
}
export function getFavoriteCount() {
    return request<number>({
        url: '/song/favorite/count',
        method: 'GET'
    });
}
export function getRecentSongs(limit = 20) {
    return request<any[]>({
        url: '/history/listen/recent',
        method: 'GET',
        params: { limit }
    });
}
export const userApi = {
    login: (data: LoginRequest) => {
        return request<LoginResponse>({
            url: '/auth/login',
            method: 'POST',
            data
        });
    },
    register: (data: RegisterRequest) => {
        return request<UserInfo>({
            url: '/auth/register',
            method: 'POST',
            data
        });
    },
    logout: () => {
        return request<void>({
            url: '/auth/logout',
            method: 'POST'
        });
    },
    getCurrentUserInfo: () => {
        return request<UserInfo>({
            url: '/user/current',
            method: 'GET',
            timeout: 10000
        });
    },
    getUserById: (id: string) => {
        return request<UserInfo>({
            url: `/user/info/${id}`,
            method: 'GET'
        });
    },
    sendVerifyCode: (data: SendVerifyCodeRequest) => {
        return request<void>({
            url: '/auth/send-code',
            method: 'POST',
            data
        });
    },
    sendEmailCode: (data: SendEmailCodeRequest) => {
        return request<SendEmailCodeResponse>({
            url: '/auth/email/send-code',
            method: 'POST',
            data
        });
    },
    verifyEmailCode: (data: VerifyEmailCodeRequest) => {
        return request<void>({
            url: '/auth/email/verify',
            method: 'POST',
            data
        });
    },
    checkNeedCaptcha: (params: {
        scene?: string;
        account?: string;
    } = {}) => {
        return request<CaptchaCheckResponse>({
            url: '/auth/captcha/check',
            method: 'GET',
            params
        });
    },
    getAuthRiskStatus: (account?: string) => {
        return request<AuthRiskStatusResponse>({
            url: '/auth/risk/status',
            method: 'GET',
            params: { account }
        });
    },
    getCaptcha: (params: CaptchaRequest = {}) => {
        return request<CaptchaResponse>({
            url: '/auth/captcha',
            method: 'GET',
            params
        });
    },
    sendResetCode: (phone: string) => {
        return request<void>({
            url: '/auth/send-code',
            method: 'POST',
            data: { phone, type: 'reset' }
        });
    },
    resetPassword: (data: any) => {
        return request<void>({
            url: '/auth/reset-password',
            method: 'POST',
            data
        });
    },
    followUser: (followeeId: string | number) => {
        return request<void>({
            url: `/user/follow/${followeeId}`,
            method: 'POST'
        });
    },
    unfollowUser: (followeeId: string | number) => {
        return request<void>({
            url: `/user/follow/${followeeId}`,
            method: 'DELETE'
        });
    },
    removeFollower: (followerId: string | number) => {
        return request<void>({
            url: '/user/follower/remove',
            method: 'DELETE',
            params: { followerId }
        });
    },
    getFollowingList: (userId: string | number, page = 1, size = 20) => {
        return request<PageResult<UserInfo>>({
            url: `/user/following/${userId}`,
            method: 'GET',
            params: { page, size }
        });
    },
    getFollowersList: (userId: string | number, page = 1, size = 20) => {
        return request<PageResult<UserInfo>>({
            url: `/user/followers/${userId}`,
            method: 'GET',
            params: { page, size }
        });
    },
    isFollowing: (followeeId: string | number) => {
        return request<boolean>({
            url: `/user/is-following/${followeeId}`,
            method: 'GET'
        });
    },
    getUserRelation: (targetUserId: string | number) => {
        return request<UserRelation>({
            url: `/user/relation/${targetUserId}`,
            method: 'GET'
        });
    },
    uploadAvatar: (file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        return request<string>({
            url: '/user/avatar',
            method: 'POST',
            data: formData,
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
    },
    updateUserProfile: (data: {
        nickname?: string;
        signature?: string;
        gender?: number;
        birthday?: string;
    }) => {
        return request<UserInfo>({
            url: '/user/update',
            method: 'PUT',
            data
        });
    },
    getUserWallpapers: () => {
        return request<string>({
            url: '/user/wallpapers',
            method: 'GET'
        });
    },
    saveUserWallpapers: (wallpapers: string) => {
        return request<void>({
            url: '/user/wallpapers',
            method: 'POST',
            data: wallpapers,
            headers: {
                'Content-Type': 'application/json'
            }
        });
    },
    addWallpaper: (wallpaper: string) => {
        return request<void>({
            url: '/user/wallpaper/add',
            method: 'POST',
            params: { wallpaper }
        });
    },
    getFavoriteCount,
    getRecentSongs,
    removeWallpaper: (wallpaper: string) => {
        return request<void>({
            url: '/user/wallpaper/delete',
            method: 'DELETE',
            params: { wallpaper }
        });
    }
};
export default userApi;
