'use client';

import Admin from '@/views/Admin';
import { RequireAdmin } from '@/components/auth-guard';

export default function AdminPage() {
  return (
    <RequireAdmin>
      <Admin />
    </RequireAdmin>
  );
}
