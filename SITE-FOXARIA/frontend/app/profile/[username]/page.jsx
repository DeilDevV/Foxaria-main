'use client';

import Profile from '@/views/Profile';
import { RequireAuth } from '@/components/auth-guard';

export default function UserProfilePage() {
  return (
    <RequireAuth>
      <Profile />
    </RequireAuth>
  );
}
