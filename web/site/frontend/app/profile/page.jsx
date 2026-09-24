'use client';

import Profile from '@/views/Profile';
import { RequireAuth } from '@/components/auth-guard';

export default function ProfilePage() {
  return (
    <RequireAuth>
      <Profile />
    </RequireAuth>
  );
}
