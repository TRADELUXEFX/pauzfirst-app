-- Read-only. Shows how the website stores session_history.
select
  jsonb_typeof(session_history) as kind,
  case when jsonb_typeof(session_history) = 'array' then jsonb_array_length(session_history) end as entries,
  case when jsonb_typeof(session_history) = 'array' then session_history -> 0 end as first_entry,
  case when jsonb_typeof(session_history) = 'array' then session_history -> -1 end as last_entry
from public.user_stats
where session_history is not null
order by updated_at desc
limit 3;
