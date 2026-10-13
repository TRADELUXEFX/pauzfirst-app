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
