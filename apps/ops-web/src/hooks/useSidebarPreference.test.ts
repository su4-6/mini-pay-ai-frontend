import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import {
  resolveInitialSidebarCollapsed,
  SIDEBAR_PREFERENCE_KEY,
  useSidebarPreference
} from './useSidebarPreference';

describe('sidebar preference', () => {
  it('uses the responsive desktop default only when no valid preference exists', () => {
    const emptyStorage = { getItem: () => null };
    expect(resolveInitialSidebarCollapsed(emptyStorage, 1440)).toBe(false);
    expect(resolveInitialSidebarCollapsed(emptyStorage, 1024)).toBe(true);
    expect(resolveInitialSidebarCollapsed(emptyStorage, 768)).toBe(false);
    expect(resolveInitialSidebarCollapsed({ getItem: () => 'invalid' }, 1024)).toBe(true);
    expect(resolveInitialSidebarCollapsed({ getItem: () => 'false' }, 1024)).toBe(false);
  });

  it('persists toggles and synchronizes valid cross-tab changes', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
    const { result } = renderHook(() => useSidebarPreference());

    act(() => result.current.toggleCollapsed());
    expect(result.current.collapsed).toBe(true);
    expect(window.localStorage.getItem(SIDEBAR_PREFERENCE_KEY)).toBe('true');

    act(() => {
      window.dispatchEvent(new StorageEvent('storage', {
        key: SIDEBAR_PREFERENCE_KEY,
        newValue: 'false'
      }));
    });
    expect(result.current.collapsed).toBe(false);
  });
});
