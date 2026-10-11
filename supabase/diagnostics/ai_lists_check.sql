-- Read-only. Shows how AI-generated lists and used_questions are stored.
(select 'lists' as part,
        jsonb_build_object(
          'name', name,
          'count', jsonb_array_length(questions),
          'first_question', questions -> 0,
          'created_at', created_at,
          'share_code', share_code) as info
 from public.question_lists
 where name ilike '%AI%'
 order by created_at desc
 limit 5)
union all
(select 'used_questions',
        jsonb_build_object(
          'kind', jsonb_typeof(used_questions),
          'entries', case when jsonb_typeof(used_questions) = 'array' then jsonb_array_length(used_questions) end,
          'sample', case when jsonb_typeof(used_questions) = 'array' then used_questions -> 0 else used_questions end)
 from public.user_stats
 where used_questions is not null
 limit 2);
