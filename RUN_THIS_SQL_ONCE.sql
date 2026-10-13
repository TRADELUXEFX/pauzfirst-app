-- ONE FILE, RUN ONCE: Supabase > SQL Editor > paste all of this > Run.
-- It contains four steps in the right order. Safe to run again.

-- ===== STEP: 20261013_shared_presets =====
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

-- ===== STEP: 20261014_preset_share_codes =====
-- Run this in Supabase > SQL Editor AFTER 20261013_shared_presets.sql.
-- Makes each preset keep ONE share code. Sharing the same preset again returns the same code
-- and refreshes the shared copy. All of this logic lives in the database, not in the app.
-- Safe to run more than once.

-- 1. One shared row per owner per preset name. If older duplicates exist, keep the newest.
delete from public.shared_presets a
using public.shared_presets b
where a.user_id = b.user_id
  and a.preset_name = b.preset_name
  and a.created_at < b.created_at;

create unique index if not exists shared_presets_owner_name_key
  on public.shared_presets (user_id, preset_name);

-- 2. The one place a preset gets its code.
create or replace function public.share_preset(
  p_name text,
  p_settings jsonb,
  p_list_name text,
  p_questions jsonb
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_code text;
  v_chars constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  i int;
  attempt int;
begin
  if v_uid is null then
    raise exception 'not signed in';
  end if;

  -- This preset was shared before: keep its code and bring the shared copy up to date.
  select sp.code into v_code
  from public.shared_presets sp
  where sp.user_id = v_uid and sp.preset_name = p_name;

  if v_code is not null then
    update public.shared_presets
       set settings = p_settings,
           list_name = p_list_name,
           questions = p_questions
     where code = v_code and user_id = v_uid;
    return v_code;
  end if;

  -- New preset: make a fresh code. Retry if a random code is already taken.
  for attempt in 1..10 loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_chars, 1 + floor(random() * length(v_chars))::int, 1);
    end loop;

    begin
      insert into public.shared_presets (code, user_id, preset_name, settings, list_name, questions)
      values (v_code, v_uid, p_name, p_settings, p_list_name, p_questions);
      return v_code;
    exception
      when unique_violation then
        -- Either the same preset was saved at the same moment by another call (use its code),
        -- or the random code was taken (try again).
        select sp.code into v_code
        from public.shared_presets sp
        where sp.user_id = v_uid and sp.preset_name = p_name;
        if v_code is not null then
          return v_code;
        end if;
    end;
  end loop;

  raise exception 'could not create a share code';
end;
$$;

revoke all on function public.share_preset(text, jsonb, text, jsonb) from public;
grant execute on function public.share_preset(text, jsonb, text, jsonb) to authenticated;

-- ===== STEP: 20261015_list_share_codes =====
-- Run this in Supabase > SQL Editor.
-- Question lists get the same share-code rule as presets, decided in the database:
--  * The owner sharing a list again gets the same code, and the shared copy is brought up to date.
--  * Someone who imported a list and shares it again reuses the code it came with, as long as
--    the questions are still the same. If they changed the questions, they get a new code.
-- Safe to run more than once.

create or replace function public.share_list(p_name text, p_questions jsonb)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_code text;
  v_owner uuid;
  v_shared_q jsonb;
  v_chars constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  i int;
  attempt int;
begin
  if v_uid is null then
    raise exception 'not signed in';
  end if;

  -- Does this list already have a code?
  select ql.share_code into v_code
  from public.question_lists ql
  where ql.user_id = v_uid and ql.name = p_name;

  if v_code is not null then
    select sl.user_id, sl.questions into v_owner, v_shared_q
    from public.shared_lists sl
    where sl.code = v_code;

    if found then
      if v_owner = v_uid then
        -- The owner: keep the code, bring the shared copy up to date.
        update public.shared_lists
           set list_name = p_name, questions = p_questions
         where code = v_code;
        return v_code;
      elsif v_shared_q = p_questions then
        -- Imported copy, same questions: reuse the code it came with.
        return v_code;
      end if;
      -- Imported copy that was changed: fall through and make a new code for this version.
    end if;
  end if;

  -- New code (also used when the old shared copy is gone or was changed).
  for attempt in 1..10 loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_chars, 1 + floor(random() * length(v_chars))::int, 1);
    end loop;

    begin
      insert into public.shared_lists (code, user_id, list_name, questions)
      values (v_code, v_uid, p_name, p_questions);

      update public.question_lists
         set share_code = v_code
       where user_id = v_uid and name = p_name;

      return v_code;
    exception
      when unique_violation then
        -- random code already taken: try again
        null;
    end;
  end loop;

  raise exception 'could not create a share code';
end;
$$;

revoke all on function public.share_list(text, jsonb) from public;
grant execute on function public.share_list(text, jsonb) to authenticated;

-- ===== STEP: 20261016_list_share_snapshot =====
-- Run this in Supabase > SQL Editor AFTER list_share_codes.sql.
-- Rule for question lists:
--  * The owner sharing a list again keeps the same code and updates the shared copy.
--  * Someone who imported a list keeps the code they got, as long as their list is unchanged
--    since they got it. Any edit (add, remove, change, reorder) gives them a new code.
-- The database remembers the questions each list had when it was last shared or imported
-- (share_snapshot), so later changes to the owner's shared copy don't affect imported copies.
-- Safe to run more than once.

alter table public.question_lists
  add column if not exists share_snapshot jsonb;

-- Called when a list is imported by code: links the new list to that code.
create or replace function public.link_imported_list(p_name text, p_code text, p_questions jsonb)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not signed in';
  end if;

  if not exists (select 1 from public.shared_lists sl where sl.code = p_code) then
    return false;
  end if;

  update public.question_lists
     set share_code = p_code,
         share_snapshot = p_questions
   where user_id = v_uid and name = p_name;

  return found;
end;
$$;

revoke all on function public.link_imported_list(text, text, jsonb) from public;
grant execute on function public.link_imported_list(text, text, jsonb) to authenticated;

-- Shares a list (owner or imported copy). See the rule at the top of this file.
create or replace function public.share_list(p_name text, p_questions jsonb)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_code text;
  v_snap jsonb;
  v_owner uuid;
  v_chars constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  i int;
  attempt int;
begin
  if v_uid is null then
    raise exception 'not signed in';
  end if;

  select ql.share_code, ql.share_snapshot into v_code, v_snap
  from public.question_lists ql
  where ql.user_id = v_uid and ql.name = p_name;

  if v_code is not null then
    select sl.user_id into v_owner from public.shared_lists sl where sl.code = v_code;

    if found then
      if v_owner = v_uid then
        -- The owner: same code, shared copy and snapshot brought up to date.
        update public.shared_lists
           set list_name = p_name, questions = p_questions
         where code = v_code;
        update public.question_lists
           set share_snapshot = p_questions
         where user_id = v_uid and name = p_name;
        return v_code;
      elsif v_snap is not null and v_snap = p_questions then
        -- Imported copy, unchanged since it was imported: keep the code it came with.
        return v_code;
      end if;
      -- Imported copy that was edited: a new code for this version (below).
    end if;
  end if;

  -- New code.
  for attempt in 1..10 loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_chars, 1 + floor(random() * length(v_chars))::int, 1);
    end loop;

    begin
      insert into public.shared_lists (code, user_id, list_name, questions)
      values (v_code, v_uid, p_name, p_questions);

      update public.question_lists
         set share_code = v_code,
             share_snapshot = p_questions
       where user_id = v_uid and name = p_name;

      return v_code;
    exception
      when unique_violation then
        null;   -- random code already taken: try again
    end;
  end loop;

  raise exception 'could not create a share code';
end;
$$;

revoke all on function public.share_list(text, jsonb) from public;
grant execute on function public.share_list(text, jsonb) to authenticated;

