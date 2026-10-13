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
