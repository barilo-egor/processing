import React from 'react';
import {createRoot} from 'react-dom/client';
import {initTheme} from '@shared/ThemeToggle.jsx';
import App from './App.jsx';
// Общие стили обоих кабинетов (frontend/shared) — до своих, чтобы свои могли их уточнить.
import '@shared/base.css';
import '@shared/docs.css';
import './index.css';

// Тема — до первого рендера, чтобы не мигала светлая при сохранённой тёмной.
initTheme();

createRoot(document.getElementById('root')).render(<App />);
