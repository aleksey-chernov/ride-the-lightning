export function AppFooter() {
  return (
    <footer className="border-t px-4 py-2 text-xs text-muted-foreground">
      Lightning data ©{' '}
      <a className="underline underline-offset-2" href="https://www.blitzortung.org" target="_blank" rel="noreferrer">
        Blitzortung.org
      </a>{' '}
      contributors (CC BY-SA 4.0) · Places:{' '}
      <a className="underline underline-offset-2" href="https://www.naturalearthdata.com" target="_blank" rel="noreferrer">
        Natural Earth
      </a>
    </footer>
  );
}
