'use client'

import { useSyncExternalStore } from 'react'
import { useTheme } from 'next-themes'
import { Moon, Sun } from 'lucide-react'

const rien = () => () => {}

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme()
  // Le thème n'est connu qu'au client : rendu serveur = icône « lune ».
  const monte = useSyncExternalStore(rien, () => true, () => false)
  const sombre = monte && resolvedTheme === 'dark'
  return (
    <button
      type="button"
      onClick={() => setTheme(sombre ? 'light' : 'dark')}
      aria-label={sombre ? 'Passer en thème clair' : 'Passer en thème sombre'}
      className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
    >
      {sombre ? <Sun size={18} /> : <Moon size={18} />}
    </button>
  )
}
