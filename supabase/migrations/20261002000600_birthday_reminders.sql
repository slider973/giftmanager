-- Rappels d'anniversaires J-30 / J-7 (#43) et mécanique de tâches planifiées.
-- Les événements « Anniversaire de X » sont créés par la migration v2.
-- Destinataires : membres du groupe, hors foyer de l'enfant (ses parents ; pour un adulte : lui et son
-- conjoint), et seulement s'ils n'ont pas désactivé la préférence dans leur profil.

alter table public.profiles add column notify_birthday_reminders boolean not null default true;

-- Un rappel n'est envoyé qu'une fois par (événement, destinataire, échéance).
create table public.birthday_reminders_sent (
  event_id uuid not null references public.events (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  days_before smallint not null check (days_before in (30, 7)),
  sent_at timestamptz not null default now(),
  primary key (event_id, user_id, days_before)
);
alter table public.birthday_reminders_sent enable row level security;
revoke all on public.birthday_reminders_sent from anon, authenticated;

-- Rappels dus à la date p_today, sans écriture : destinataires déjà filtrés.
create function private.birthday_reminder_candidates(p_today date default current_date)
returns table (event_id uuid, child_name text, event_date date, days_before integer, user_id uuid)
language sql stable security definer set search_path = '' as $$
  select e.id, c.first_name, e.event_date, (e.event_date - p_today), gm.user_id
  from public.events e
  join public.children c on c.id = e.child_id
  join public.group_members gm on gm.group_id = e.group_id
  join public.profiles p on p.id = gm.user_id
  where e.kind = 'birthday'
    and (e.event_date - p_today) in (30, 7)
    and p.notify_birthday_reminders
    and not exists (
      select 1 from public.household_members hm
      where hm.household_id = c.household_id and hm.user_id = gm.user_id
    )
    and not exists (
      select 1 from public.birthday_reminders_sent s
      where s.event_id = e.id and s.user_id = gm.user_id and s.days_before = (e.event_date - p_today)
    );
$$;

-- Réserve les rappels du jour (les marque envoyés) et les renvoie. Atomique : deux exécutions
-- simultanées ne renvoient jamais le même rappel. Réservée au service role (fonction Edge).
create function public.claim_birthday_reminders(p_today date default current_date)
returns table (event_id uuid, child_name text, event_date date, days_before integer, user_id uuid)
language plpgsql volatile security definer set search_path = '' as $$
begin
  return query
  with due as (select * from private.birthday_reminder_candidates(p_today)),
  claimed as (
    insert into public.birthday_reminders_sent as s (event_id, user_id, days_before)
    select d.event_id, d.user_id, d.days_before from due d
    on conflict do nothing
    returning s.event_id, s.user_id, s.days_before
  )
  select d.event_id, d.child_name, d.event_date, d.days_before, d.user_id
  from due d join claimed c on (c.event_id, c.user_id, c.days_before) = (d.event_id, d.user_id, d.days_before);
end;
$$;

-- ---------------------------------------------------------------------------
-- Tâches planifiées : pg_cron appelle les fonctions Edge via pg_net.
-- L'URL des fonctions et le secret partagé (CRON_SECRET, vérifié par les fonctions) sont lus dans
-- Vault (secrets `edge_functions_url` et `cron_secret`), jamais écrits dans le dépôt : voir docs/SECRETS.md.
-- Tant qu'ils sont absents, l'appel est un no-op.
-- ---------------------------------------------------------------------------
create extension if not exists pg_cron;
create extension if not exists pg_net;

create function private.invoke_edge_function(p_name text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_url text;
  v_secret text;
begin
  select decrypted_secret into v_url from vault.decrypted_secrets where name = 'edge_functions_url';
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'cron_secret';
  if v_url is null or v_secret is null then
    raise notice 'invoke_edge_function(%): secrets Vault absents, ignoré', p_name;
    return;
  end if;
  perform net.http_post(
    url := rtrim(v_url, '/') || '/' || p_name,
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', v_secret),
    body := '{}'::jsonb,
    timeout_milliseconds := 5000
  );
end;
$$;

-- Tous les jours à 08:00 UTC.
select cron.schedule('birthday-reminders', '0 8 * * *', $$select private.invoke_edge_function('birthday-reminders')$$);

revoke execute on function
  private.birthday_reminder_candidates(date), private.invoke_edge_function(text) from public, anon, authenticated;
revoke execute on function public.claim_birthday_reminders(date) from public, anon, authenticated;
grant execute on function public.claim_birthday_reminders(date) to service_role;
