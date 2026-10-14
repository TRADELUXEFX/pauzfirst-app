-- 2026-10-14. STEP B. Run ONLY AFTER Step A, AND after the new app and the new website
-- (www/index.html) are both installed/deployed. Older versions read the table directly and
-- would say "Code not found" once this is run.
-- Replaces "anyone can read every shared list" with "you can read your own rows".
-- Safe to run more than once.
drop policy if exists "Anyone can read shared lists" on public.shared_lists;
drop policy if exists "Users can read their own shared lists" on public.shared_lists;
create policy "Users can read their own shared lists"
  on public.shared_lists for select to authenticated
  using (auth.uid() = user_id);
