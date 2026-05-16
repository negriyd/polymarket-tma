import './polyfills';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './app/App';
import { bootstrapTelegram } from './lib/telegram/webApp';
import './index.css';

bootstrapTelegram();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
