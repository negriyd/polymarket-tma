export function ErrorView({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="rounded-xl bg-rose-500/10 p-4 text-center text-sm text-rose-500">
      <p>{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-2 rounded-md bg-tg-button px-3 py-1 text-xs text-tg-buttonText"
        >
          Retry
        </button>
      )}
    </div>
  );
}
