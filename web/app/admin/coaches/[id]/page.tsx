'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { supabase } from '@/lib/supabase';

interface BuiltinCoach {
  id: string;
  name: string;
  title: string;
  category: string;
  emoji: string;
  image_url: string | null;
  greeting: string;
  bio: string;
  fun_fact: string | null;
  specialties: string[];
  personality: string | null;
  city: string | null;
  timezone: string;
  country_flag: string | null;
  avatar_bg_color: string;
  avatar_accent_color: string;
  xp_to_unlock: number;
  is_active: boolean;
  display_order: number;
}

export default function CoachEditPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  const [coach, setCoach] = useState<BuiltinCoach | null>(null);
  const [form, setForm] = useState<Partial<BuiltinCoach>>({});
  const [specialtiesText, setSpecialtiesText] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    supabase
      .from('builtin_coaches')
      .select('*')
      .eq('id', id)
      .single()
      .then(({ data, error: err }) => {
        if (err || !data) { setError(err?.message ?? 'Not found'); }
        else {
          setCoach(data as BuiltinCoach);
          setForm(data as BuiltinCoach);
          setSpecialtiesText(((data as BuiltinCoach).specialties ?? []).join(', '));
        }
        setLoading(false);
      });
  }, [id]);

  const set = useCallback((key: keyof BuiltinCoach, value: unknown) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setError('');
    setSuccess(false);
    const specialties = specialtiesText
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const payload = { ...form, specialties };
    const { error: err } = await supabase
      .from('builtin_coaches')
      .update(payload)
      .eq('id', id);
    setSaving(false);
    if (err) { setError(err.message); }
    else { setSuccess(true); setTimeout(() => router.push('/admin/coaches'), 1200); }
  };

  if (loading) return <div className="p-8 text-gray-400 text-sm">Loading…</div>;
  if (!coach && error) return <div className="p-8 text-red-400 text-sm">Error: {error}</div>;
  if (!coach) return null;

  const field = (
    label: string,
    key: keyof BuiltinCoach,
    type: 'text' | 'textarea' | 'number' | 'checkbox' = 'text'
  ) => (
    <div>
      <label className="block text-xs font-semibold text-gray-400 mb-1">{label}</label>
      {type === 'textarea' ? (
        <textarea
          value={(form[key] as string) ?? ''}
          onChange={(e) => set(key, e.target.value)}
          rows={3}
          className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 resize-none"
        />
      ) : type === 'checkbox' ? (
        <input
          type="checkbox"
          checked={(form[key] as boolean) ?? false}
          onChange={(e) => set(key, e.target.checked)}
          className="w-4 h-4 accent-indigo-500"
        />
      ) : (
        <input
          type={type}
          value={(form[key] as string | number) ?? ''}
          onChange={(e) => set(key, type === 'number' ? Number(e.target.value) : e.target.value)}
          className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
        />
      )}
    </div>
  );

  return (
    <div className="p-8 max-w-2xl">
      <div className="flex items-center gap-3 mb-6">
        <Link href="/admin/coaches" className="text-xs text-gray-500 hover:text-gray-300">
          ← Coaches
        </Link>
        <span className="text-gray-700">/</span>
        <p className="text-sm font-semibold">{coach.name}</p>
      </div>

      {/* Avatar preview */}
      <div className="flex items-center gap-4 mb-8 p-4 bg-gray-900 rounded-xl border border-gray-800">
        <div className="w-16 h-16 rounded-full bg-gray-800 overflow-hidden flex-shrink-0 flex items-center justify-center text-3xl">
          {form.image_url ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={form.image_url} alt={form.name} className="w-full h-full object-cover object-top" />
          ) : (
            <span>{form.emoji}</span>
          )}
        </div>
        <div>
          <p className="font-bold">{form.name}</p>
          <p className="text-sm text-gray-400">{form.title}</p>
        </div>
      </div>

      <div className="space-y-5">
        <div className="grid grid-cols-2 gap-4">
          {field('Name', 'name')}
          {field('Title', 'title')}
        </div>
        <div className="grid grid-cols-2 gap-4">
          {field('Emoji', 'emoji')}
          {field('Image URL', 'image_url')}
        </div>
        {field('Greeting', 'greeting', 'textarea')}
        {field('Bio', 'bio', 'textarea')}
        {field('Fun Fact', 'fun_fact')}

        <div>
          <label className="block text-xs font-semibold text-gray-400 mb-1">
            Specialties (comma-separated)
          </label>
          <input
            type="text"
            value={specialtiesText}
            onChange={(e) => setSpecialtiesText(e.target.value)}
            placeholder="Goal setting, Motivation, Life balance"
            className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
          />
        </div>

        <div className="grid grid-cols-3 gap-4">
          {field('Personality', 'personality')}
          {field('City', 'city')}
          {field('Country Flag', 'country_flag')}
        </div>
        <div className="grid grid-cols-2 gap-4">
          {field('Timezone', 'timezone')}
          {field('Avatar BG Color', 'avatar_bg_color')}
        </div>
        <div className="grid grid-cols-2 gap-4">
          {field('XP to Unlock', 'xp_to_unlock', 'number')}
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-2">Active</label>
            {field('', 'is_active', 'checkbox')}
          </div>
        </div>
      </div>

      {error && <p className="mt-4 text-xs text-red-400">{error}</p>}
      {success && <p className="mt-4 text-xs text-green-400">Saved! Redirecting…</p>}

      <div className="mt-8 flex gap-3">
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-5 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm font-semibold rounded-lg transition-colors"
        >
          {saving ? 'Saving…' : 'Save changes'}
        </button>
        <Link
          href="/admin/coaches"
          className="px-5 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 text-sm font-medium rounded-lg transition-colors"
        >
          Cancel
        </Link>
      </div>
    </div>
  );
}
