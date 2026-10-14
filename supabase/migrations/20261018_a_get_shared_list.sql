-- 2026-10-14. STEP A (safe to run any time, run it FIRST).
-- Lets the app and website look up a shared list by its exact code without the table being open.
-- Safe to run more than once.
create or replace function public.get_shared_list(p_code text)
returns table (list_name text, questions text[])
language sql
security definer
set search_path = public
stable
as $$
  select sl.list_name, sl.questions
  from public.shared_lists sl
  where sl.code = p_code
  limit 1;
$$;

revoke all on function public.get_shared_list(text) from public;
grant execute on function public.get_shared_list(text) to anon, authenticated;
