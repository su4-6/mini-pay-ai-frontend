import { useCallback, useEffect, useState } from 'react';

export const SIDEBAR_PREFERENCE_KEY = 'minipay:ops:sidebar-collapsed';

function parseStoredPreference(value: string | null): boolean | undefined {
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return undefined;
}

export function resolveInitialSidebarCollapsed(
  storage: Pick<Storage, 'getItem'> | undefined,
  viewportWidth: number
): boolean {
  try {
    const stored = parseStoredPreference(storage?.getItem(SIDEBAR_PREFERENCE_KEY) ?? null);
    if (stored !== undefined) {
      return stored;
    }
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }
  return viewportWidth >= 901 && viewportWidth < 1200;
}

function readBrowserDefault(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  return resolveInitialSidebarCollapsed(window.localStorage, window.innerWidth);
}

export function useSidebarPreference() {
  const [collapsed, setCollapsed] = useState(readBrowserDefault);

  const updateCollapsed = useCallback((nextCollapsed: boolean) => {
    setCollapsed(nextCollapsed);
    try {
      window.localStorage.setItem(SIDEBAR_PREFERENCE_KEY, String(nextCollapsed));
    } catch {
      // The in-memory preference remains usable when persistent storage is blocked.
    }
  }, []);

  const toggleCollapsed = useCallback(() => {
    setCollapsed((current) => {
      const next = !current;
      try {
        window.localStorage.setItem(SIDEBAR_PREFERENCE_KEY, String(next));
      } catch {
        // The in-memory preference remains usable when persistent storage is blocked.
      }
      return next;
    });
  }, []);

  useEffect(() => {
    const syncPreference = (event: StorageEvent) => {
      if (event.key !== SIDEBAR_PREFERENCE_KEY) {
        return;
      }
      const next = parseStoredPreference(event.newValue);
      if (next !== undefined) {
        setCollapsed(next);
      }
    };
    window.addEventListener('storage', syncPreference);
    return () => window.removeEventListener('storage', syncPreference);
  }, []);

  return { collapsed, setCollapsed: updateCollapsed, toggleCollapsed };
}
