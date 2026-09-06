import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue';
export interface PreviewLimits {
    mobile: number;
    tablet: number;
    desktop: number;
    wide: number;
}
export function getResponsivePreviewLimit(width: number, limits: PreviewLimits): number {
    if (width < 640)
        return limits.mobile;
    if (width < 960)
        return limits.tablet;
    if (width < 1280)
        return limits.desktop;
    return limits.wide;
}
export function useResponsivePreviewLimit(limits: PreviewLimits): Ref<number> {
    const previewLimit = ref(limits.desktop);
    const update = () => {
        previewLimit.value = getResponsivePreviewLimit(window.innerWidth, limits);
    };
    onMounted(() => {
        update();
        window.addEventListener('resize', update, { passive: true });
    });
    onBeforeUnmount(() => window.removeEventListener('resize', update));
    return previewLimit;
}
