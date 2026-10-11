import './polyfills';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './app/App';
import { bootstrapTelegram } from './lib/telegram/webApp';
import { useLocaleStore } from './lib/i18n';
import './index.css';

bootstrapTelegram();
document.documentElement.lang = useLocaleStore.getState().locale;

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
