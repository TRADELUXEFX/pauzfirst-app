-- Read-only. Returns one JSON cell describing the public schema. Changes nothing.
select jsonb_pretty(jsonb_build_object(
  'tables', (select coalesce(jsonb_agg(table_name order by table_name), '[]'::jsonb)
             from information_schema.tables where table_schema = 'public'),
  'columns', (select coalesce(jsonb_agg(jsonb_build_object(
                'table', table_name, 'column', column_name, 'type', data_type,
                'nullable', is_nullable, 'default', column_default)
                order by table_name, ordinal_position), '[]'::jsonb)
              from information_schema.columns where table_schema = 'public'),
  'constraints', (select coalesce(jsonb_agg(jsonb_build_object(
                'table', conrelid::regclass::text, 'name', conname, 'def', pg_get_constraintdef(oid))), '[]'::jsonb)
              from pg_constraint where connamespace = 'public'::regnamespace),
  'rls_enabled', (select coalesce(jsonb_agg(jsonb_build_object('table', relname, 'rls', relrowsecurity)), '[]'::jsonb)
              from pg_class where relnamespace = 'public'::regnamespace and relkind = 'r'),
  'policies', (select coalesce(jsonb_agg(jsonb_build_object(
                'table', tablename, 'name', policyname, 'cmd', cmd, 'roles', roles,
                'using', qual, 'check', with_check)), '[]'::jsonb)
              from pg_policies where schemaname = 'public'),
  'functions', (select coalesce(jsonb_agg(p.proname order by p.proname), '[]'::jsonb)
              from pg_proc p where p.pronamespace = 'public'::regnamespace),
  'sample_question_lists', (select coalesce(jsonb_agg(jsonb_build_object(
                'name', name, 'first_question', questions->0)), '[]'::jsonb)
              from (select name, questions from public.question_lists limit 3) t)
)) as schema_report;
