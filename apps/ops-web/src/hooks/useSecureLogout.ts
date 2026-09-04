import { useCallback, useRef, useState } from 'react';
import type { CsrfResponse } from '@minipay/api-contracts';
import { getCsrf } from '../services/auth';

interface SecureLogoutOptions {
  loadCsrf?: () => Promise<CsrfResponse>;
  submit?: (csrf: CsrfResponse) => void;
  onError?: () => void;
}

function submitLogoutForm(csrf: CsrfResponse) {
  const form = document.createElement('form');
  form.method = 'post';
  form.action = '/logout';
  form.hidden = true;

  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = csrf.parameterName;
  input.value = csrf.token;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}

export function useSecureLogout({
  loadCsrf = getCsrf,
  submit = submitLogoutForm,
  onError
}: SecureLogoutOptions = {}) {
  const [loggingOut, setLoggingOut] = useState(false);
  const inFlightRef = useRef(false);

  const logout = useCallback(async () => {
    if (inFlightRef.current) {
      return;
    }
    inFlightRef.current = true;
    setLoggingOut(true);

    try {
      const csrf = await loadCsrf();
      submit(csrf);
    } catch {
      inFlightRef.current = false;
      setLoggingOut(false);
      onError?.();
    }
  }, [loadCsrf, onError, submit]);

  return { loggingOut, logout };
}
