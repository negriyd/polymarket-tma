import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import type { PartnerView } from '@/lib/api/types';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { formatError } from '@/lib/errors';
import { useLocale, useT } from '@/lib/i18n';
import { PartnerStats } from '@/features/partners/PartnerStats';

interface Terms {
  sharePct: string;
  lifetime: boolean;
  months: string;
  note: string;
  publicName: string;
}

const DEFAULT_TERMS: Terms = { sharePct: '50', lifetime: false, months: '24', note: '', publicName: '' };

/** Form values → request body, or null while something is out of range. */
function toInput(
  f: Terms,
): { shareBps: number; months: number | null; note: string; publicName: string } | null {
  const pct = Number(f.sharePct);
  const months = Number(f.months);
  if (!Number.isFinite(pct) || pct <= 0 || pct > 100) return null;
  if (!f.lifetime && (!Number.isInteger(months) || months < 1 || months > 1200)) return null;
  return {
    shareBps: Math.round(pct * 100),
    months: f.lifetime ? null : months,
    note: f.note.trim(),
    publicName: f.publicName.trim(),
  };
}

const input = 'w-full rounded-lg bg-tg-bg px-3 py-2 text-sm text-tg-text outline-none placeholder:text-tg-hint';

/** Share, term (months or lifetime) and note — shared by "add" and "edit". */
function TermsFields({ value, onChange }: { value: Terms; onChange: (v: Terms) => void }) {
  const t = useT();
  return (
    <>
      <label className="block">
        <span className="text-[11px] text-tg-hint">{t('opp.share')}</span>
        <input
          className={input}
          inputMode="decimal"
          value={value.sharePct}
          onChange={(e) => onChange({ ...value, sharePct: e.target.value })}
        />
      </label>
      <div>
        <span className="text-[11px] text-tg-hint">{t('opp.term')}</span>
        <div className="flex items-center gap-2">
          <input
            className={`${input} w-24`}
            inputMode="numeric"
            disabled={value.lifetime}
            value={value.lifetime ? '' : value.months}
            onChange={(e) => onChange({ ...value, months: e.target.value })}
          />
          <span className="text-xs text-tg-hint">{t('opp.months')}</span>
          <label className="ml-auto flex items-center gap-1.5 text-xs text-tg-text">
            <input
              type="checkbox"
              checked={value.lifetime}
              onChange={(e) => onChange({ ...value, lifetime: e.target.checked })}
            />
            {t('opp.lifetime')}
          </label>
        </div>
      </div>
      <label className="block">
        <span className="text-[11px] text-tg-hint">{t('opp.publicName')}</span>
        <input
          className={input}
          maxLength={64}
          value={value.publicName}
          onChange={(e) => onChange({ ...value, publicName: e.target.value })}
        />
      </label>
      <label className="block">
        <span className="text-[11px] text-tg-hint">{t('opp.note')}</span>
        <input
          className={input}
          maxLength={256}
          value={value.note}
          onChange={(e) => onChange({ ...value, note: e.target.value })}
        />
      </label>
    </>
  );
}

function AddPartner() {
  const t = useT();
  const qc = useQueryClient();
  const [user, setUser] = useState('');
  const [terms, setTerms] = useState(DEFAULT_TERMS);
  const body = toInput(terms);
  const add = useMutation({
    mutationFn: () => api.addPartner({ user: user.trim(), ...body! }),
    onSuccess: () => {
      setUser('');
      setTerms(DEFAULT_TERMS);
      void qc.invalidateQueries({ queryKey: ['operator-partners'] });
    },
  });
  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
      <h2 className="text-sm font-semibold text-tg-text">{t('opp.add')}</h2>
      <label className="block">
        <span className="text-[11px] text-tg-hint">{t('opp.user')}</span>
        <input className={input} placeholder="@channel_owner" value={user} onChange={(e) => setUser(e.target.value)} />
      </label>
      <p className="text-[11px] leading-snug text-tg-hint">{t('opp.userHint')}</p>
      <TermsFields value={terms} onChange={setTerms} />
      {add.isError && <p className="text-xs text-rose-500">{formatError(add.error)}</p>}
      <button
        type="button"
        disabled={!body || !user.trim() || add.isPending}
        onClick={() => add.mutate()}
        className="w-full rounded-lg bg-tg-button py-2 text-sm font-semibold text-tg-buttonText disabled:opacity-50"
      >
        {t('opp.add')}
      </button>
    </section>
  );
}

function PartnerCard({ view }: { view: PartnerView }) {
  const t = useT();
  const locale = useLocale();
  const qc = useQueryClient();
  const p = view.partner;
  const [editing, setEditing] = useState(false);
  const [active, setActive] = useState(p.active);
  const [terms, setTerms] = useState<Terms>({
    sharePct: String(p.shareBps / 100),
    lifetime: p.months == null,
    months: String(p.months ?? 24),
    note: p.note ?? '',
    publicName: p.publicName ?? '',
  });
  const body = toInput(terms);
  const save = useMutation({
    mutationFn: () => api.updatePartner(p.userId, { ...body!, active }),
    onSuccess: () => {
      setEditing(false);
      void qc.invalidateQueries({ queryKey: ['operator-partners'] });
    },
  });

  const term = p.months == null ? t('opp.lifetime') : t('opp.monthsShort', { n: p.months });
  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-tg-text">
            {p.publicName ?? (p.username ? `@${p.username}` : p.name || p.telegramId)}
            {!p.active && <span className="ml-2 text-xs font-normal text-rose-500">{t('opp.paused')}</span>}
          </p>
          <p className="text-xs text-tg-hint">
            {p.shareBps / 100}% · {term} · {t('pt.since', { date: new Date(p.since).toLocaleDateString(locale) })}
          </p>
          {p.note && <p className="mt-0.5 text-xs text-tg-text">{p.note}</p>}
          {!p.hasWallet && <p className="mt-0.5 text-[11px] text-amber-500">{t('opp.noWallet')}</p>}
        </div>
        {!editing && (
          <button type="button" onClick={() => setEditing(true)} className="shrink-0 text-xs text-tg-link">
            {t('opp.edit')}
          </button>
        )}
      </div>

      {editing ? (
        <div className="space-y-2">
          <TermsFields value={terms} onChange={setTerms} />
          <label className="flex items-center gap-1.5 text-xs text-tg-text">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t('opp.active')}
          </label>
          {!active && <p className="text-[11px] leading-snug text-tg-hint">{t('opp.pausedHint')}</p>}
          {save.isError && <p className="text-xs text-rose-500">{formatError(save.error)}</p>}
          <div className="flex gap-2">
            <button
              type="button"
              disabled={!body || save.isPending}
              onClick={() => save.mutate()}
              className="flex-1 rounded-lg bg-tg-button py-2 text-sm font-semibold text-tg-buttonText disabled:opacity-50"
            >
              {t('opp.save')}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="flex-1 rounded-lg bg-tg-bg py-2 text-sm text-tg-text"
            >
              {t('opp.cancel')}
            </button>
          </div>
        </div>
      ) : (
        <>
          <PartnerStats totals={p.totals} />
          <p className="break-all font-mono text-[11px] text-tg-hint">{view.link}</p>
        </>
      )}
    </section>
  );
}

/** Operator's list of partners with individual terms. */
export function OperatorPartnersPage() {
  const t = useT();
  const list = useQuery({ queryKey: ['operator-partners'], queryFn: api.listPartners });
  return (
    <div className="max-w-full min-w-0 space-y-4">
      <Link to="/operator" className="inline-flex items-center text-sm text-tg-hint">
        {t('common.back')}
      </Link>
      <h1 className="text-2xl font-semibold">{t('opp.title')}</h1>
      <AddPartner />
      {list.isLoading && <Spinner />}
      {list.isError && <ErrorView message={formatError(list.error)} onRetry={() => list.refetch()} />}
      {list.data?.length === 0 && <p className="text-sm text-tg-hint">{t('opp.empty')}</p>}
      {list.data?.map((v) => <PartnerCard key={v.partner.userId} view={v} />)}
    </div>
  );
}
