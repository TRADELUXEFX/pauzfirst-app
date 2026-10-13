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
