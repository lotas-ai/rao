import { access } from 'fs/promises'

/**
 * Returns a value indicating whether or not the provided path exists (as in
 * whether it's visible to the current process or not).
 */
export const pathExists = (path: string) =>
  access(path).then(() => true, () => false)