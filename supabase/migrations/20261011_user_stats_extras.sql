-- Run this in Supabase > SQL Editor.
-- Adds one column to the existing user_stats table. It holds the favorites and the
-- "How did it feel?" ratings, so they follow the account to a new phone.
-- Safe to run more than once. Existing rows and policies are not touched.
alter table public.user_stats
  add column if not exists extras jsonb not null default '{}'::jsonb;
