'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { supabase } from '@/lib/supabase';

interface BuiltinCoach {
  id: string;
  name: string;
  title: string;
  category: string;
  emoji: string;
  image_url: string | null;
  bio: string;
  is_active: boolean;
  display_order: number;
}

const CATEGORY_COLOR: Record<string, string> = {
  CAREER: 'bg-blue-900 text-blue-300',
  MONEY: 'bg-green-900 text-green-300',
  BODY: 'bg-red-900 text-red-300',
  PEOPLE: 'bg-purple-900 text-purple-300',
  WELLBEING: 'bg-yellow-900 text-yellow-300',
  PURPOSE: 'bg-teal-900 text-teal-300',
};

export default function CoachesListPage() {
  const [coaches, setCoaches] = useState<BuiltinCoach[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    supabase
      .from('builtin_coaches')
      .select('id,name,title,category,emoji,image_url,bio,is_active,display_order')
      .order('display_order')
      .then(({ data, error: err }) => {
        if (err) setError(err.message);
        else setCoaches((data as BuiltinCoach[]) ?? []);
        setLoading(false);
      });
  }, []);

  if (loading) return <div className="p-8 text-gray-400 text-sm">Loading coaches…</div>;
  if (error) return <div className="p-8 text-red-400 text-sm">Error: {error}</div>;

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-1">Coaches</h1>
      <p className="text-sm text-gray-400 mb-8">
        Edit the 6 built-in coaches. Changes sync to the app on next launch.
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
        {coaches.map((coach) => (
          <div
            key={coach.id}
            className="bg-gray-900 border border-gray-800 rounded-xl p-5 flex gap-4 items-start"
          >
            {/* Avatar */}
            <div className="w-14 h-14 rounded-full bg-gray-800 flex-shrink-0 overflow-hidden flex items-center justify-center text-2xl">
              {coach.image_url ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={coach.image_url} alt={coach.name} className="w-full h-full object-cover object-top" />
              ) : (
                <span>{coach.emoji}</span>
              )}
            </div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <p className="font-semibold text-sm">{coach.name}</p>
                {!coach.is_active && (
                  <span className="text-xs bg-gray-700 text-gray-400 px-2 py-0.5 rounded-full">inactive</span>
                )}
              </div>
              <p className="text-xs text-gray-400 mb-2">{coach.title}</p>
              <span
                className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  CATEGORY_COLOR[coach.category] ?? 'bg-gray-700 text-gray-300'
                }`}
              >
                {coach.category.charAt(0) + coach.category.slice(1).toLowerCase()}
              </span>
              <p className="text-xs text-gray-500 mt-2 line-clamp-2">{coach.bio}</p>
            </div>

            {/* Edit link */}
            <Link
              href={`/admin/coaches/${coach.id}`}
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
