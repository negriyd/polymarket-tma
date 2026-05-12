export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-6 text-tg-hint">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-tg-button border-t-transparent" />
      {label && <span className="text-sm">{label}</span>}
    </div>
  );
}
