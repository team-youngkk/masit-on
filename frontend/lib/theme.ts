export const THEME_STORAGE_KEY = 'masiton-theme'

export type Theme = 'light' | 'dark'

export function isTheme(value: string | null | undefined): value is Theme {
  return value === 'light' || value === 'dark'
}

export function resolveTheme(
  storedTheme: string | null | undefined,
  prefersDark: boolean,
): Theme {
  return isTheme(storedTheme) ? storedTheme : prefersDark ? 'dark' : 'light'
}

export const themeInitScript = `(() => {
  const storageKey = ${JSON.stringify(THEME_STORAGE_KEY)};
  let storedTheme = null;
  try {
    storedTheme = window.localStorage.getItem(storageKey);
  } catch {}
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  const theme = storedTheme === 'dark' || storedTheme === 'light'
    ? storedTheme
    : prefersDark ? 'dark' : 'light';
  document.documentElement.dataset.theme = theme;
})();`
