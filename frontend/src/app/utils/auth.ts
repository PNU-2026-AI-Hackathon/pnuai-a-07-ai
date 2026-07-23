const ACCESS_TOKEN_KEY = "safework_access_token";
const TOKEN_TYPE_KEY = "safework_token_type";

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
};

export function saveAuthToken(response: AuthResponse) {
  localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
  localStorage.setItem(TOKEN_TYPE_KEY, response.tokenType || "Bearer");
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getAuthorizationHeader() {
  const token = getAccessToken();
  const tokenType = localStorage.getItem(TOKEN_TYPE_KEY) || "Bearer";

  return token ? `${tokenType} ${token}` : "";
}

export function isAuthenticated() {
  return Boolean(getAccessToken());
}

export function clearAuthToken() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(TOKEN_TYPE_KEY);
}
