'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { supabase } from '@/lib/supabase';

interface SystemPrompt {
  key: string;
  name: string;
  description: string | null;
  content: string;
  updated_at: string;
}

export default function EditPromptPage() {
  const params = useParams();
  const router = useRouter();
  const key = params.key as string;

  const [prompt, setPrompt] = useState<SystemPrompt | null>(null);
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    supabase
      .from('system_prompts')
      .select('*')
      .eq('key', key)
      .single()
      .then(({ data, error: err }) => {
        if (err) { setError(err.message); setLoading(false); return; }
        const p = data as SystemPrompt;
        setPrompt(p);
        setContent(p.content);
        setLoading(false);
      });
  }, [key]);

  const handleSave = async () => {
    if (!prompt) return;
    setSaving(true);
    setError('');
    setSaved(false);

    const { error: err } = await supabase
      .from('system_prompts')
      .update({ content })
      .eq('key', key);

    setSaving(false);
    if (err) { setError(err.message); return; }
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  const handleReset = () => {
    if (prompt) setContent(prompt.content);
    setError('');
  };

  if (loading) return <div className="p-8 text-gray-400 text-sm">Loading…</div>;
  if (!prompt) return <div className="p-8 text-red-400 text-sm">Prompt not found: {key}</div>;

  const wordCount = content.trim().split(/\s+/).filter(Boolean).length;
  const charCount = content.length;

  return (
    <div className="p-8 max-w-4xl">
      {/* Header */}
      <div className="mb-6">
        <Link href="/admin/prompts" className="text-xs text-gray-500 hover:text-gray-300 mb-3 inline-block">
          ← Prompts
        </Link>
        <h1 className="text-2xl font-bold mb-1">{prompt.name}</h1>
        <div className="flex items-center gap-3">
          <span className="text-xs font-mono text-gray-500 bg-gray-800 px-2 py-0.5 rounded">{key}</span>
          {prompt.description && <p className="text-xs text-gray-400">{prompt.description}</p>}
        </div>
      </div>

      {/* Editor */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2 border-b border-gray-800 bg-gray-950">
          <span className="text-xs text-gray-500 font-mono">content</span>
          <span className="text-xs text-gray-600">{charCount} chars · {wordCount} words</span>
        </div>
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="w-full bg-transparent text-sm font-mono text-gray-100 p-4 resize-none focus:outline-none leading-relaxed"
          style={{ minHeight: 420 }}
          spellCheck={false}
        />
      </div>

      {/* Actions */}
      <div className="mt-4 flex items-center gap-3">
        <button
          onClick={handleSave}
          disabled={saving || content === prompt.content}
          className="bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors"
        >
          {saving ? 'Saving…' : 'Save changes'}
        </button>
        <button
          onClick={handleReset}
          disabled={content === prompt.content}
          className="text-sm text-gray-500 hover:text-gray-300 disabled:opacity-40 transition-colors"
        >
          Reset
        </button>
        {saved && <span className="text-xs text-green-400">Saved — app picks up changes on next launch</span>}
        {error && <span className="text-xs text-red-400">{error}</span>}
      </div>

      {/* Hint for streaming_instructions */}
      {key === 'streaming_instructions' && (
        <div className="mt-6 rounded-xl border border-gray-800 bg-gray-900 p-4 text-xs text-gray-500 leading-relaxed">
          <span className="text-gray-300 font-semibold">Tip: </span>
          Use <code className="text-indigo-400">&#123;coach_name&#125;</code> anywhere in this prompt — it will be replaced with the active coach&apos;s name (e.g. &quot;Luna&quot;, &quot;Alex&quot;) at runtime.
        </div>
      )}
    </div>
  );
}
