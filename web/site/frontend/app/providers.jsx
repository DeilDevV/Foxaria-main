'use client';

import { Toaster } from 'react-hot-toast';
import { AuthProvider } from '@/context/AuthContext';

export default function Providers({ children }) {
  return (
    <AuthProvider>
      {children}
      <Toaster
        position="bottom-right"
        toastOptions={{
          style: {
            background: 'rgba(17, 20, 26, 0.92)',
            color: '#fff',
            border: '1px solid rgba(255, 122, 26, 0.24)',
            borderRadius: '16px',
            backdropFilter: 'blur(18px)',
          },
          success: { iconTheme: { primary: '#FF7A1A', secondary: '#fff' } },
          error: { iconTheme: { primary: '#ef4444', secondary: '#fff' } },
        }}
      />
    </AuthProvider>
  );
}
