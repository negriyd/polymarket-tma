import clsx from 'clsx';
import type { Leaderboard, LeaderboardRow } from '@/lib/api/types';
import { useT } from '@/lib/i18n';

const MEDALS = ['🥇', '🥈', '🥉'];

function Row({ r }: { r: LeaderboardRow }) {
  const t = useT();
  return (
    <li
      className={clsx(
        'flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm',
        r.me ? 'bg-tg-button/15 font-semibold' : 'bg-tg-bg',
      )}
    >
      <span className="w-7 shrink-0 text-center tabular-nums text-tg-hint">{MEDALS[r.rank - 1] ?? r.rank}</span>
      <span className="min-w-0 flex-1 truncate text-tg-text">
        {r.name}
        {r.me && <span className="ml-1 text-xs text-tg-hint">({t('lb.you')})</span>}
      </span>
      <span className="shrink-0 tabular-nums text-tg-text">
        {t('lb.score', { correct: r.correct, resolved: r.resolved })}
      </span>
      <span className="w-10 shrink-0 text-right text-xs tabular-nums text-tg-hint">{r.pct}%</span>
    </li>
  );
}

export function LeaderboardTable({ board }: { board: Leaderboard }) {
  const t = useT();
  if (board.top.length === 0) return <p className="text-xs text-tg-hint">{t('lb.empty')}</p>;
  return (
    <ol className="space-y-1">
      {board.top.map((r) => (
        <Row key={r.rank} r={r} />
      ))}
      {board.me && (
        <>
          <li className="text-center text-xs text-tg-hint">…</li>
          <Row r={board.me} />
        </>
      )}
    </ol>
  );
}
