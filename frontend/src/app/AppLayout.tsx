import { NavLink, Outlet } from 'react-router-dom';
import clsx from 'clsx';
import { useT, type MessageKey } from '@/lib/i18n';

const tabs: { to: string; label: MessageKey }[] = [
  { to: '/', label: 'nav.home' },
  { to: '/markets', label: 'nav.markets' },
  { to: '/favorites', label: 'nav.saved' },
  { to: '/wallet', label: 'nav.wallet' },
];

export function AppLayout() {
  const t = useT();
  return (
    <div className="flex h-full min-h-0 min-w-0 flex-col">
      <main className="min-h-0 min-w-0 max-w-full flex-1 overflow-y-auto overflow-x-hidden px-4 pb-24 pt-3">
        <Outlet />
      </main>
      <nav className="fixed inset-x-0 bottom-0 z-10 border-t border-black/5 bg-tg-bg/95 backdrop-blur dark:border-white/5">
        <ul className="mx-auto flex max-w-md justify-around py-2">
          {tabs.map((tab) => (
            <li key={tab.to}>
              <NavLink
                to={tab.to}
                end={tab.to === '/'}
                className={({ isActive }) =>
                  clsx(
                    'px-4 py-2 text-sm font-medium transition-colors',
                    isActive ? 'text-tg-link' : 'text-tg-hint hover:text-tg-text',
                  )
                }
              >
                {t(tab.label)}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
