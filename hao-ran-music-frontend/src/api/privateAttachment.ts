import { request } from '@/utils/request';
export type PrivateAttachmentPurpose = 'MESSAGE_IMAGE' | 'REPORT_EVIDENCE' | 'FEEDBACK_ATTACHMENT' | 'APPEAL_EVIDENCE' | 'MODERATION_APPEAL_EVIDENCE';
export interface PrivateAttachmentSession {
    sessionToken: string;
    purpose: PrivateAttachmentPurpose;
    maxFiles: number;
    expiresAt: string;
}
export interface PrivateAttachmentAsset {
    assetId: string;
    purpose: PrivateAttachmentPurpose;
    contentType: string;
    fileSize: string | number;
    scanStatus: 'CLEAN';
}
export interface PrivateAttachmentGrant {
    assetId: string;
    grant: string;
    contentUrl: string;
}
export function createPrivateAttachmentSession(purpose: PrivateAttachmentPurpose) {
    return request<PrivateAttachmentSession>({
        url: '/private-attachments/sessions',
        method: 'POST',
        data: { purpose }
    });
}
export async function uploadPrivateAttachment(sessionToken: string, file: File) {
    const data = new FormData();
    data.append('file', file);
    const response = await request<Omit<PrivateAttachmentAsset, 'assetId'> & {
        assetId: string | number;
    }>({
        url: `/private-attachments/sessions/${encodeURIComponent(sessionToken)}/assets`,
        method: 'POST',
        data,
        headers: { 'Content-Type': 'multipart/form-data' }
    });
    return { ...response, data: { ...response.data, assetId: String(response.data.assetId) } };
}
export function cancelPrivateAttachmentSession(sessionToken: string) {
    return request<void>({
        url: `/private-attachments/sessions/${encodeURIComponent(sessionToken)}`,
        method: 'DELETE'
    });
}
export async function issuePrivateAttachmentGrant(assetId: string) {
    const response = await request<Omit<PrivateAttachmentGrant, 'assetId'> & {
        assetId: string | number;
    }>({
        url: `/private-attachments/assets/${encodeURIComponent(assetId)}/grant`,
        method: 'POST'
    });
    return { ...response, data: { ...response.data, assetId: String(response.data.assetId) } };
}
