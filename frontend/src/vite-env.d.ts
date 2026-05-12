/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_PRIVY_APP_ID?: string;
  readonly VITE_POLYGON_RPC_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
