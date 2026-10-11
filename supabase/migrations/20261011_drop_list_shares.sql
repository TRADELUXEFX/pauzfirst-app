-- The app now shares through the website's existing shared_lists table.
-- Removes the unused table and function created by the earlier migration.
drop function if exists public.get_shared_list(text);
drop table if exists public.list_shares;
