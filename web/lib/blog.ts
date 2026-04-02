import { getSupabase } from './supabase';

export interface BlogPost {
  slug: string;
  title: string;
  description: string;
  date: string;
  readTime: string;
  category: string;
  tags: string[];
  content: string;
}

function mapRow(row: Record<string, unknown>): BlogPost {
  return {
    slug: row.slug as string,
    title: row.title as string,
    description: row.description as string,
    date: row.date as string,
    readTime: row.read_time as string,
    category: row.category as string,
    tags: row.tags as string[],
    content: (row.content ?? '') as string,
  };
}

export async function getAllPosts(): Promise<BlogPost[]> {
  const { data } = await getSupabase()
    .from('blog_posts')
    .select('slug, title, description, date, read_time, category, tags')
    .eq('published', true)
    .order('date', { ascending: false });
  return (data ?? []).map(mapRow);
}

export async function getPostBySlug(slug: string): Promise<BlogPost | null> {
  const { data } = await getSupabase()
    .from('blog_posts')
    .select('*')
    .eq('slug', slug)
    .eq('published', true)
    .single();
  return data ? mapRow(data) : null;
}

export async function getAllSlugs(): Promise<string[]> {
  const { data } = await getSupabase()
    .from('blog_posts')
    .select('slug')
    .eq('published', true);
  return (data ?? []).map((r: Record<string, unknown>) => r.slug as string);
}
