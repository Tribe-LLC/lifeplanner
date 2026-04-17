'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { supabase } from '@/lib/supabase';

interface SystemPrompt {
  key: string;
  name: string;
  description: string | null;
  content: string;
  updated_at: string;
}

export default function PromptsListPage() {
  const [prompts, setPrompts] = useState<SystemPrompt[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    supabase
      .from('system_prompts')
      .select('key,name,description,content,updated_at')
      .order('key')
      .then(({ data, error: err }) => {
        if (err) setError(err.message);
        else setPrompts((data as SystemPrompt[]) ?? []);
        setLoading(false);
      });
  }, []);

  if (loading) return <div className="p-8 text-gray-400 text-sm">Loading prompts…</div>;
  if (error) return <div className="p-8 text-red-400 text-sm">Error: {error}</div>;

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-1">System Prompts</h1>
      <p className="text-sm text-gray-400 mb-8">
        Edit AI behavior rules. Changes sync to the app on next launch.
        Use <code className="text-indigo-400">&#123;coach_name&#125;</code> in streaming instructions as a placeholder for the active coach's name.
      </p>

      <div className="space-y-4">
        {prompts.map((p) => (
          <div key={p.key} className="bg-gray-900 border border-gray-800 rounded-xl p-5 flex gap-4 items-start">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <p className="font-semibold text-sm">{p.name}</p>
                <span className="text-xs font-mono text-gray-600 bg-gray-800 px-2 py-0.5 rounded">{p.key}</span>
              </div>
              {p.description && <p className="text-xs text-gray-400 mb-2">{p.description}</p>}
              <p className="text-xs text-gray-600 line-clamp-2 font-mono">
                {p.content.slice(0, 120)}…
              </p>
              <p className="text-xs text-gray-700 mt-2">
                Updated {new Date(p.updated_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
              </p>
            </div>
            <Link
              href={`/admin/prompts/${p.key}`}
              className="flex-shrink-0 text-xs text-indigo-400 hover:text-indigo-300 font-medium"
            >
              Edit
            </Link>
          </div>
        ))}
      </div>
    </div>
  );
}
