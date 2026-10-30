import { useGetVersion } from '@api/endpoints/admin/admin';

export function AppFooter() {
  const { data: build } = useGetVersion({ query: { staleTime: Infinity } });

  return (
    <footer className="flex flex-wrap justify-between gap-x-4 gap-y-1 border-t px-4 py-2 text-xs text-muted-foreground">
      <span>
        Lightning data ©{' '}
        <a className="underline underline-offset-2" href="https://www.blitzortung.org" target="_blank" rel="noreferrer">
          Blitzortung.org
        </a>{' '}
        contributors (CC BY-SA 4.0) · Places:{' '}
        <a className="underline underline-offset-2" href="https://www.naturalearthdata.com" target="_blank" rel="noreferrer">
          Natural Earth
        </a>
      </span>
      {build && (
        <span className="ml-auto">
          v{build.version} · {build.commit.slice(0, 7)}
        </span>
      )}
    </footer>
  );
}
