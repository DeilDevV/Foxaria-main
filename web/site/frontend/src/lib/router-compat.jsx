'use client';

import { useEffect } from 'react';
import NextLink from 'next/link';
import { useParams as useNextParams, usePathname, useRouter } from 'next/navigation';

export function Link({ to, href, children, ...props }) {
  const target = href || to || '/';
  return (
    <NextLink href={target} {...props}>
      {children}
    </NextLink>
  );
}

export function useNavigate() {
  const router = useRouter();
  return (to, options = {}) => {
    if (options.replace) router.replace(to);
    else router.push(to);
  };
}

export function useLocation() {
  const pathname = usePathname();
  return { pathname: pathname || '/' };
}

export function useParams() {
  return useNextParams() || {};
}

export function Navigate({ to, replace = false }) {
  const router = useRouter();

  useEffect(() => {
    if (replace) router.replace(to);
    else router.push(to);
  }, [replace, router, to]);

  return null;
}

export function Routes() {
  throw new Error('Routes is not supported in Next.js app router.');
}

export function Route() {
  throw new Error('Route is not supported in Next.js app router.');
}
