# PauzFirst database: current state

**Last updated:** 2026-10-14 (shared_lists lock-down prepared, PENDING)
**Where:** Supabase project used by both the Android app and the website (`www/`).
**Last verified against the live database:** 2026-10-14 (schema report pasted by the owner).

---

## RULES FOR ANY AI WORKING ON THIS PROJECT (read first)

1. **Read this file before touching anything database-related.** Treat it as the source of truth for what exists. Do not ask the owner to re-run diagnostics just to find out what is there.
2. **Every database change must be recorded here in the same reply that makes it.** That means: a new or changed table, column, function, policy, index, constraint, edge function, or a data fix.
3. For each change do all three:
   - Update the matching section below (tables, policies, functions) so it shows the new state.
   - Add one line to the **Change log** at the bottom with a timestamp (`YYYY-MM-DD HH:MM`, plus timezone if known; if the time is unknown write the date and "time unknown").
   - Update **Last updated** at the top.
4. **Status matters.** A change is `PENDING` until the owner confirms the SQL ran with no error. Mark it `APPLIED` only after they say so. Never write `APPLIED` on a guess.
5. Every SQL change also gets a file in `supabase/migrations/` named `YYYYMMDD_short_name.sql`, safe to run more than once.
6. When the owner pastes a new schema report, replace the sections below with what it shows and log any differences found.
7. Before writing SQL that saves data into a column, check the column type here. (A past bug: `shared_lists.questions` is `text[]`, and SQL that wrote JSON into it failed.)
8. Explain to the owner in plain, simple words, not technical jargon.

To refresh this file from the live database: run `supabase/diagnostics/schema_check.sql` in the Supabase SQL Editor and paste the result to the AI.

---

## Tables (5)

All five have row security (RLS) turned on.

### `question_lists`: the user's saved question lists
| Column | Type | Null | Default |
|---|---|---|---|
| id | uuid | no | gen_random_uuid() |
| user_id | uuid | no | |
| name | text | no | |
| questions | jsonb | no | |
| bookmarks | jsonb | yes | '{}' |
| created_at | timestamp (no tz) | yes | now() |
| pinned | boolean | yes | false |
| share_code | text | yes | |
| share_snapshot | jsonb | yes | |

Keys: primary key `id`; user_id references auth.users; **unique (user_id, name)**.

### `shared_lists`: lists published under a share code
| Column | Type | Null | Default |
|---|---|---|---|
| id | uuid | no | gen_random_uuid() |
| code | text | no | |
| user_id | uuid | yes | |
| list_name | text | no | |
| questions | **text[] (array)** | no | |
| created_at | timestamp with tz | yes | now() |

Keys: primary key `id`; **unique (code)**; user_id references auth.users, on delete cascade.
Note: `questions` is a plain text array, not jsonb.

### `shared_presets`: presets published under a share code
| Column | Type | Null | Default |
|---|---|---|---|
| code | text | no | |
| user_id | uuid | no | |
| preset_name | text | no | |
| settings | jsonb | no | '{}' |
| list_name | text | yes | |
| questions | jsonb | yes | |
| created_at | timestamp with tz | no | now() |

Keys: primary key `code`; user_id references auth.users, on delete cascade.
Index expected from migration 20261014: unique (user_id, preset_name) `shared_presets_owner_name_key`. It was not visible in the last report (the report did not list indexes). Unverified.

### `user_stats`: one row per user, all their progress
| Column | Type | Null | Default |
|---|---|---|---|
| id | uuid (= user id) | no | |
| stats | jsonb | yes | '{}' |
| updated_at | timestamp (no tz) | yes | now() |
| settings | jsonb | yes | |
| used_questions | jsonb | yes | |
| default_settings | jsonb | yes | |
| session_history | jsonb | yes | |
| extras | jsonb | no | '{}' (favorites + "How did it feel?" ratings) |

Keys: primary key `id`; id references auth.users.

### `community_questions`: questions submitted by users
| Column | Type | Null | Default |
|---|---|---|---|
| id | uuid | no | gen_random_uuid() |
| question | text | no | |
| created_at | timestamp with tz | yes | now() |

Keys: primary key `id`; unique (question).

---

## Access rules (RLS policies)

| Table | Rule | Who | Allows |
|---|---|---|---|
| user_stats | own stats | public | ALL, only where auth.uid() = id |
| question_lists | own lists | public | ALL, only where auth.uid() = user_id |
| community_questions | Anyone can read community questions | anon, authenticated | SELECT all |
| community_questions | Authenticated users can submit questions | authenticated | INSERT |
| shared_lists | Anyone can read shared lists | public | SELECT all |
| shared_lists | Users can insert their own | public | INSERT where auth.uid() = user_id |
| shared_lists | Users can update their own | authenticated | UPDATE own rows. APPLIED (20261017) |
| shared_presets | shared presets: owner can add | authenticated | INSERT own |
| shared_presets | shared presets: owner can remove | authenticated | DELETE own |

Known weak spots:
- `shared_lists`: anyone can read every row, so all codes and questions can be browsed. Fix prepared, PENDING: step A `20261018_a_get_shared_list.sql` (run first), then step B `20261018_b_close_shared_lists.sql` (run only after the new app and the new website are live). Step B replaces "Anyone can read shared lists" with "Users can read their own shared lists" (SELECT, authenticated, own rows).
- `shared_lists`: no DELETE rule, so users cannot remove a shared copy.
- `shared_presets`: no UPDATE rule needed (done inside `share_preset`).

---

## Functions (4 live + 1 pending), all `security definer`, callable by signed-in users only

| Function | What it does | Status |
|---|---|---|
| `share_list(p_name text, p_questions jsonb) returns text` | Gives a list its 6-character code. Owner sharing again keeps the same code and refreshes the shared copy. An imported list keeps its code while unchanged, otherwise gets a new one. Converts jsonb to text[] for `shared_lists`. | APPLIED (20261017 fixed version; the old one wrote jsonb into a text[] column and failed) |
| `link_imported_list(p_name, p_code, p_questions) returns boolean` | Links an imported list to the code it came with. | APPLIED |
| `share_preset(p_name, p_settings, p_list_name, p_questions) returns text` | One code per preset per owner; sharing again refreshes it. | APPLIED |
| `get_shared_preset(p_code) returns table` | Reads one preset by exact code. | APPLIED |
| `get_shared_list(p_code text) returns table(list_name, questions text[])` | Reads one shared list by exact code, so the table does not need to be open to everyone. Callable by anon + signed-in. | PENDING (20261018_a) |

## Edge functions (not visible in the SQL report; unverified)
- `gemini-proxy`: used by the app for AI question generation. Confirm it is deployed in Supabase > Edge Functions.

## Removed / not present
- `list_shares` table and `get_shared_list` function: dropped (migration 20261011_drop_list_shares). Confirmed absent on 2026-10-14.

---

## How the app uses the database

- Android app (`Api.kt`) and website (`www/index.html`) talk to the same tables.
- `question_lists`: read, add, rename, pin, delete lists.
- `shared_lists`: reads by code to import; the app falls back to writing here directly if `share_list` fails.
- `shared_presets`: only through `share_preset` and `get_shared_preset`.
- `user_stats`: stats, session history, used questions, extras.
- `community_questions`: read and submit.

---

## Change log (newest first)

| When | Change | Status |
|---|---|---|
| 2026-10-14 (time unknown) | Prepared lock-down of `shared_lists`: new function `get_shared_list` (step A) and policy swap (step B). App (`Api.kt`) and website (`www/index.html`) now look codes up through the function, with a fallback to the old table read. | PENDING: step A not run yet; step B must wait until app + website are updated |
| 2026-10-14 (time unknown) | Fixed `share_list` to convert jsonb to text[] (cause of "Couldn't create a share code"). Added UPDATE rule on `shared_lists`. File: `20261017_share_list_array_fix.sql`. | APPLIED. Owner ran it and generated a share code from the share icon (tested on the old app, before any app update) |
| 2026-10-14 (time unknown) | Ran RUN_THIS_SQL_ONCE.sql (steps 20261013 to 20261016): created `shared_presets`, `share_preset`, `get_shared_preset`, `link_imported_list`, `share_list` (old version), `question_lists.share_snapshot`. | APPLIED (owner reported "Success") |
| 2026-10-14 (time unknown) | Full schema report taken from the live database. This file created from it. | APPLIED |
| 2026-10-11 | `user_stats.extras` column added (20261011_user_stats_extras). | APPLIED (column present in report) |
| 2026-10-11 | `list_shares` table dropped (20261011_drop_list_shares). | APPLIED (table absent in report) |
