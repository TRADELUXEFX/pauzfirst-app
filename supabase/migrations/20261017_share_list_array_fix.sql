-- 2026-10-14. shared_lists.questions is a text[] column (made by the website), not jsonb.
-- share_list now converts the jsonb list to text[] before saving. Also adds the missing
-- UPDATE rule on shared_lists so an owner can refresh their shared copy.
-- Safe to run more than once. Supersedes share_list in 20261015 and 20261016.

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
  v_arr text[] := array(select jsonb_array_elements_text(p_questions));
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
        update public.shared_lists
           set list_name = p_name, questions = v_arr
         where code = v_code;
        update public.question_lists
           set share_snapshot = p_questions
         where user_id = v_uid and name = p_name;
        return v_code;
      elsif v_snap is not null and v_snap = p_questions then
        return v_code;
      end if;
    end if;
  end if;

  for attempt in 1..10 loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_chars, 1 + floor(random() * length(v_chars))::int, 1);
    end loop;
    begin
      insert into public.shared_lists (code, user_id, list_name, questions)
      values (v_code, v_uid, p_name, v_arr);

      update public.question_lists
         set share_code = v_code, share_snapshot = p_questions
       where user_id = v_uid and name = p_name;
      return v_code;
    exception
      when unique_violation then null;
    end;
  end loop;

  raise exception 'could not create a share code';
end;
$$;

revoke all on function public.share_list(text, jsonb) from public;
grant execute on function public.share_list(text, jsonb) to authenticated;

drop policy if exists "Users can update their own" on public.shared_lists;
create policy "Users can update their own"
  on public.shared_lists for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
