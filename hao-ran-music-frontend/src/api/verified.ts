import { request } from '@/utils/request';
export interface VerifiedInfoVO {
    isVerified: boolean;
    verifiedType?: string;
    verifiedTypeName?: string;
    verifiedLevel?: string;
    verifiedLevelName?: string;
    verifiedReason?: string;
    verifiedTime?: string;
    verifiedIcon?: string;
    verifiedColor?: string;
}
export interface VerifiedUserVO {
    id: string;
    nickname: string;
    avatar: string;
    fansCount: number;
    worksCount: number;
    isFollowing: boolean;
    verifiedInfo?: VerifiedInfoVO;
}
export function getUserVerifiedInfo(userId: string) {
    return request<VerifiedInfoVO>({
        url: `/verified/user/${userId}`,
        method: 'GET'
    });
}
export function getVerifiedList(params?: {
    type?: string;
    level?: string;
    page?: number;
    size?: number;
}) {
    return request<{
        records: VerifiedUserVO[];
        total: number;
    }>({
        url: '/verified/list',
        method: 'GET',
        params
    });
}
export function reviewVerified(data: {
    creatorId: string;
    approved: boolean;
    verifiedType: string;
    verifiedLevel: string;
    reason?: string;
}) {
    return request<boolean>({
        url: `/verified/review/${data.creatorId}`,
        method: 'POST',
        params: {
            approved: data.approved,
            verifiedType: data.verifiedType,
            verifiedLevel: data.verifiedLevel,
            reason: data.reason
        }
    });
}
export const verifiedApi = {
    getUserVerifiedInfo,
    getVerifiedList,
    reviewVerified
};
