import { readonly, ref, shallowRef } from 'vue';
import { cancelPrivateAttachmentSession, createPrivateAttachmentSession, uploadPrivateAttachment, type PrivateAttachmentAsset, type PrivateAttachmentPurpose } from '@/api/privateAttachment';
interface UsePrivateAttachmentsOptions {
    purpose: PrivateAttachmentPurpose;
    maxFiles?: number;
}
export function usePrivateAttachments(options: UsePrivateAttachmentsOptions) {
    const sessionToken = shallowRef('');
    const uploading = shallowRef(false);
    const errorMessage = shallowRef('');
    const assets = ref<PrivateAttachmentAsset[]>([]);
    const maxFiles = options.maxFiles ?? (options.purpose === 'MESSAGE_IMAGE' ? 1 : 5);
    async function ensureSession() {
        if (sessionToken.value)
            return sessionToken.value;
        const response = await createPrivateAttachmentSession(options.purpose);
        sessionToken.value = response.data.sessionToken;
        return sessionToken.value;
    }
    async function upload(file: File) {
        if (assets.value.length >= maxFiles) {
            throw new Error(`最多上传${maxFiles}个附件`);
        }
        uploading.value = true;
        errorMessage.value = '';
        try {
            const token = await ensureSession();
            const response = await uploadPrivateAttachment(token, file);
            const asset = response.data;
            if (!assets.value.some(item => item.assetId === asset.assetId)) {
                assets.value = [...assets.value, asset];
            }
            return asset;
        }
        catch (error: any) {
            errorMessage.value = error?.message || '附件上传失败';
            throw error;
        }
        finally {
            uploading.value = false;
        }
    }
    async function remove(assetId: string) {
        assets.value = assets.value.filter(item => item.assetId !== assetId);
        if (assets.value.length === 0)
            await cancel();
    }
    async function cancel() {
        const token = sessionToken.value;
        sessionToken.value = '';
        assets.value = [];
        if (!token)
            return;
        try {
            await cancelPrivateAttachmentSession(token);
        }
        catch {
        }
    }
    function clearLocal() {
        sessionToken.value = '';
        assets.value = [];
        errorMessage.value = '';
    }
    return {
        assets: readonly(assets),
        uploading: readonly(uploading),
        errorMessage: readonly(errorMessage),
        upload,
        remove,
        cancel,
        clearLocal
    };
}
