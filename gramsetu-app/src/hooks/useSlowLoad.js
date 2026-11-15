import { useEffect, useState } from 'react'

// True once `active` has been true for `ms` milliseconds. The free backend can take up to a minute
// to wake up, so screens use this to swap a spinner for a friendly "waking up the server" message.
export default function useSlowLoad(active, ms = 8000) {
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    if (!active) {
      setSlow(false)
      return undefined
    }
    const id = setTimeout(() => setSlow(true), ms)
    return () => clearTimeout(id)
  }, [active, ms])

  return slow
}
