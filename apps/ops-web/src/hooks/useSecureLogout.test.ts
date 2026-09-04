import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useSecureLogout } from './useSecureLogout';

const csrf = {
  headerName: 'X-XSRF-TOKEN',
  parameterName: '_csrf',
  token: 'test-token'
};

describe('secure logout', () => {
  it('blocks duplicate submissions while CSRF is loading', async () => {
    let resolveCsrf: ((value: typeof csrf) => void) | undefined;
    const loadCsrf = vi.fn(() => new Promise<typeof csrf>((resolve) => {
      resolveCsrf = resolve;
    }));
    const submit = vi.fn();
    const { result } = renderHook(() => useSecureLogout({ loadCsrf, submit }));

    await act(async () => {
      const first = result.current.logout();
      const second = result.current.logout();
      resolveCsrf?.(csrf);
      await Promise.all([first, second]);
    });

    expect(loadCsrf).toHaveBeenCalledTimes(1);
    expect(submit).toHaveBeenCalledTimes(1);
    expect(result.current.loggingOut).toBe(true);
  });

  it('restores the action and reports a recoverable error', async () => {
    const onError = vi.fn();
    const { result } = renderHook(() => useSecureLogout({
      loadCsrf: () => Promise.reject(new Error('offline')),
      onError
    }));

    await act(async () => {
      await result.current.logout();
    });

    expect(result.current.loggingOut).toBe(false);
    expect(onError).toHaveBeenCalledTimes(1);
  });
});
