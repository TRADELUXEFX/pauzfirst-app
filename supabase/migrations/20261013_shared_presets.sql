-- Run this in Supabase > SQL Editor.
-- Lets people share a saved preset by code. Stores the preset's settings and, when the
-- preset uses a question list, a copy of that list's questions.
-- Safe to run more than once.

create table if not exists public.shared_presets (
  code text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  preset_name text not null,
  settings jsonb not null default '{}'::jsonb,
  list_name text,
  questions jsonb,
  created_at timestamptz not null default now()
);

alter table public.shared_presets enable row level security;

drop policy if exists "shared presets: owner can add" on public.shared_presets;
create policy "shared presets: owner can add"
  on public.shared_presets for insert to authenticated
  with check (auth.uid() = user_id);

drop policy if exists "shared presets: owner can remove" on public.shared_presets;
create policy "shared presets: owner can remove"
  on public.shared_presets for delete to authenticated
  using (auth.uid() = user_id);

-- Reading goes through this function. Only an exact code returns a row,
-- so the table cannot be browsed or listed.
create or replace function public.get_shared_preset(p_code text)
returns table (preset_name text, settings jsonb, list_name text, questions jsonb)
language sql
security definer
set search_path = public
stable
as $$
  select sp.preset_name, sp.settings, sp.list_name, sp.questions
  from public.shared_presets sp
  where sp.code = p_code
  limit 1;
$$;

revoke all on function public.get_shared_preset(text) from public;
grant execute on function public.get_shared_preset(text) to authenticated;
