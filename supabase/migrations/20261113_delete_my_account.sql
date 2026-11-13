-- 20261113_delete_my_account.sql
-- Lets a signed-in person delete their own account and all its data from the app.
-- Google Play requires in-app account deletion. Safe to run more than once.
-- The user's rows are deleted explicitly first, so this works whether or not a foreign key cascades.

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  delete from public.question_lists where user_id = uid;
  delete from public.shared_lists where user_id = uid;
  delete from public.shared_presets where user_id = uid;
  delete from public.user_stats where id = uid;
  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
