const STORAGE_PREFIX = 'hrm:verify-code:cooldown:';
function normalizePhone(phone: string) {
    return phone.trim();
}
function getKey(scene: string, phone: string) {
    return `${STORAGE_PREFIX}${scene}:${normalizePhone(phone)}`;
}
export function getVerifyCodeCooldownSeconds(scene: string, phone: string) {
    if (!phone)
        return 0;
    const key = getKey(scene, phone);
    const expireAt = Number(localStorage.getItem(key) || '0');
    const remaining = Math.ceil((expireAt - Date.now()) / 1000);
    if (remaining <= 0) {
        localStorage.removeItem(key);
        return 0;
    }
    return remaining;
}
export function startVerifyCodeCooldown(scene: string, phone: string, seconds = 60) {
    if (!phone)
        return 0;
    const expireAt = Date.now() + seconds * 1000;
    localStorage.setItem(getKey(scene, phone), String(expireAt));
    return seconds;
}
export function clearVerifyCodeCooldown(scene: string, phone: string) {
    if (!phone)
        return;
    localStorage.removeItem(getKey(scene, phone));
}
