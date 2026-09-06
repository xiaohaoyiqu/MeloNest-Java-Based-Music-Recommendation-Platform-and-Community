import { onScopeDispose, shallowRef } from 'vue';
export interface LatestRequestToken {
    id: number;
    signal: AbortSignal;
}
export function useLatestRequest() {
    const loading = shallowRef(false);
    let sequence = 0;
    let activeController: AbortController | null = null;
    function begin(): LatestRequestToken {
        activeController?.abort();
        activeController = new AbortController();
        loading.value = true;
        return { id: ++sequence, signal: activeController.signal };
    }
    function isCurrent(token: LatestRequestToken): boolean {
        return token.id === sequence && !token.signal.aborted;
    }
    function finish(token: LatestRequestToken) {
        if (!isCurrent(token))
            return;
        loading.value = false;
        activeController = null;
    }
    function cancel() {
        activeController?.abort();
        activeController = null;
        sequence++;
        loading.value = false;
    }
    onScopeDispose(cancel);
    return { loading, begin, isCurrent, finish, cancel };
}
