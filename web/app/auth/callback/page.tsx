'use client'

import { Suspense, useEffect } from 'react'
import { useSearchParams } from 'next/navigation'

function AuthCallbackContent() {
  const searchParams = useSearchParams()

  useEffect(() => {
    const params = searchParams.toString()
    const hash = window.location.hash
    const appUrl = `lifeplanner://auth${hash || (params ? `?${params}` : '')}`

    window.location.href = appUrl
  }, [searchParams])

  return (
    <div className="text-center">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-emerald-500 mx-auto mb-4"></div>
      <h1 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
        Opening Life Planner...
      </h1>
      <p className="text-gray-600 dark:text-gray-400">
        If the app doesn&apos;t open automatically,{' '}
        <a
          href="lifeplanner://auth"
          className="text-emerald-500 hover:text-emerald-600 underline"
        >
          tap here
        </a>
      </p>
    </div>
  )
}

function LoadingFallback() {
  return (
    <div className="text-center">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-emerald-500 mx-auto mb-4"></div>
      <h1 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
        Loading...
      </h1>
    </div>
  )
}

export default function LifePlannerAuthCallbackPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-[#0a0a0f]">
      <Suspense fallback={<LoadingFallback />}>
        <AuthCallbackContent />
      </Suspense>
    </div>
  )
}
