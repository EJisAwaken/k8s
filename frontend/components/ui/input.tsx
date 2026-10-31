'use client'

// Composant repris d'EcoleManagement (design system commun).

import { useId, useState } from 'react'
import { clsx } from 'clsx'
import { Eye, EyeOff } from 'lucide-react'
import { useT } from '@/providers/locale-provider'

function joinIds(...ids: (string | false | undefined)[]) {
  return ids.filter(Boolean).join(' ') || undefined
}

function FieldError({ id, children }: { id: string; children: React.ReactNode }) {
  return <p id={id} className="text-xs text-destructive">{children}</p>
}

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  revealable?: boolean
  /** React 19 : `ref` est une prop ordinaire, transmise à l'<input> avec le reste. */
  ref?: React.Ref<HTMLInputElement>
}

export function Input({
  label, error, className, id, type, revealable = true,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
  ...props
}: InputProps) {
  // Le libellé du bouton est un texte d'interface : il suit la langue comme le
  // reste. Le prendre en prop aux appels aurait remis 24 fois la même décision
  // entre les mains des écrans — et l'un d'eux l'aurait écrit en français en dur.
  const t = useT()
  const autoId = useId()
  const inputId = id ?? autoId
  const errorId = `${inputId}-error`

  const [revealed, setRevealed] = useState(false)
  const withEye = type === 'password' && revealable
  const showing = withEye && revealed

  const champ = (
    <input
      id={inputId}
      {...props}
      type={showing ? 'text' : type}
      spellCheck={showing ? (props.spellCheck ?? false) : props.spellCheck}
      autoCapitalize={showing ? (props.autoCapitalize ?? 'none') : props.autoCapitalize}
      autoCorrect={showing ? (props.autoCorrect ?? 'off') : props.autoCorrect}
      aria-invalid={error ? true : ariaInvalid}
      aria-describedby={joinIds(ariaDescribedBy, error && errorId)}
      suppressHydrationWarning
      className={clsx(
        'rounded-lg border bg-background px-3 py-2 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground',
        error
          ? 'border-destructive focus:border-destructive focus:ring-2 focus:ring-destructive'
          : 'border-input focus:border-ring focus:ring-2 focus:ring-ring',
        'disabled:bg-muted disabled:text-muted-foreground',
        withEye && [
          // L'`<input>` n'est plus l'enfant direct du conteneur en colonne (il est
          // dans le `relative` ci-dessous) : sans `w-full`, il retomberait sur sa
          // largeur intrinsèque d'élément en ligne, ~177 px.
          'w-full',
          // Réserve de l'œil. Sur pointeur grossier le bouton passe à 44 px de
          // large : sans élargir la réserve, la fin d'un mot de passe révélé
          // passerait SOUS lui — même calcul que la croix de `date-picker`.
          'pr-10 pointer-coarse:pr-14',
          'pointer-coarse:min-h-[44px]',
        ],
        className,
      )}
    />
  )

  return (
    <div className="flex flex-col gap-1" suppressHydrationWarning>
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-foreground">
          {label}
        </label>
      )}
      {withEye ? (
        <div className="relative">
          {champ}
          <button
            // ⚠️ `type="button"`. Dans un `<form>`, le défaut d'un `<button>` est
            // `submit` : un clic sur l'œil ENVERRAIT le formulaire — la page part,
            // et rien n'indique pourquoi. Défaut classique, et silencieux.
            type="button"
            onClick={() => setRevealed(v => !v)}
            // Un champ désactivé n'a rien à révéler, et laisser son œil actif
            // ferait de lui la seule chose cliquable d'un bloc inerte.
            disabled={props.disabled}
            aria-label={t(revealed ? 'ds.hidePassword' : 'ds.showPassword')}
            aria-pressed={revealed}
            className="absolute right-1 top-1/2 inline-flex -translate-y-1/2 items-center justify-center rounded-lg p-1.5 text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-40 pointer-coarse:min-h-[44px] pointer-coarse:min-w-[44px]"
          >
            {revealed ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
      ) : champ}
      {error && <FieldError id={errorId}>{error}</FieldError>}
    </div>
  )
}

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  error?: string
}

export function Textarea({
  label, error, className, id,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
  ...props
}: TextareaProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const errorId = `${inputId}-error`
  return (
    <div className="flex flex-col gap-1" suppressHydrationWarning>
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-foreground">
          {label}
        </label>
      )}
      <textarea
        id={inputId}
        {...props}
        aria-invalid={error ? true : ariaInvalid}
        aria-describedby={joinIds(ariaDescribedBy, error && errorId)}
        suppressHydrationWarning
        className={clsx(
          'min-h-24 rounded-lg border bg-background px-3 py-2 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground',
          error
            ? 'border-destructive focus:border-destructive focus:ring-2 focus:ring-destructive'
            : 'border-input focus:border-ring focus:ring-2 focus:ring-ring',
          'disabled:bg-muted disabled:text-muted-foreground',
          className,
        )}
      />
      {error && <FieldError id={errorId}>{error}</FieldError>}
    </div>
  )
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
}

export function Select({
  label, error, className, children, id,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
  ...props
}: SelectProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const errorId = `${inputId}-error`
  return (
    <div className="flex flex-col gap-1" suppressHydrationWarning>
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-foreground">
          {label}
        </label>
      )}
      <select
        suppressHydrationWarning
        id={inputId}
        {...props}
        aria-invalid={error ? true : ariaInvalid}
        aria-describedby={joinIds(ariaDescribedBy, error && errorId)}
        className={clsx(
          'rounded-lg border bg-background px-3 py-2 text-sm text-foreground outline-none transition-colors',
          error
            ? 'border-destructive focus:border-destructive focus:ring-2 focus:ring-destructive'
            : 'border-input focus:border-ring focus:ring-2 focus:ring-ring',
          className,
        )}
      >
        {children}
      </select>
      {error && <FieldError id={errorId}>{error}</FieldError>}
    </div>
  )
}
