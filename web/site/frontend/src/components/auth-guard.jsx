'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import Loading from '@/components/Loading';

export function RequireAuth({ children }) {
  const router = useRouter();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (!loading && !user) router.replace('/login');
  }, [loading, router, user]);

  if (loading) return <Loading />;
  if (!user) return null;
  return children;
}

export function RequireAdmin({ children }) {
  const router = useRouter();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (!loading && !user) router.replace('/login');
    else if (!loading && user && !user.isAdmin) router.replace('/');
  }, [loading, router, user]);

  if (loading) return <Loading />;
  if (!user || !user.isAdmin) return null;
  return children;
}
