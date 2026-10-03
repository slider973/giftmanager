-- #59 — Rappels d'achat côté serveur (J-30 / J-15 / J-7 / J-2).
--
-- Les rappels étaient purement locaux (NotificationService.scheduleReminders) : ils ne
-- partaient que si l'app avait été ouverte assez tôt pour les planifier, disparaissaient à
-- la réinstallation, et manquaient précisément celui qui n'ouvre plus l'app pendant trois
-- semaines. Le socle serveur (pg_cron, CRON_SECRET, Vault) existe déjà pour #43 et #39.
--
-- Chaque rappel ne concerne QUE l'auteur de la réservation : aucun autre membre n'apprend
-- quoi que ce soit, et les parents de l'enfant ne reçoivent rien sur leurs propres listes.

alter table public.profiles
  add column notify_purchase_reminders boolean not null default true;

create table public.purchase_reminders_sent (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  event_id uuid not null references public.events (id) on delete cascade,
  days_before integer not null check (days_before in (2, 7, 15, 30)),
  sent_at timestamptz not null default now(),
  unique (user_id, event_id, days_before)
);
create index on public.purchase_reminders_sent (user_id, event_id);

alter table public.purchase_reminders_sent enable row level security;
revoke all on public.purchase_reminders_sent from anon, authenticated;

-- Fuseau horaire déduit du pays du profil : suffisant pour la v1 (CH/FR et voisins),
-- et évite d'ajouter une colonne que personne ne renseignerait.
create function private.country_timezone(p_country text) returns text
language sql immutable set search_path = '' as $$
  select case upper(coalesce(p_country, ''))
    when 'CH' then 'Europe/Zurich'
    when 'FR' then 'Europe/Paris'
    when 'BE' then 'Europe/Brussels'
    when 'LU' then 'Europe/Luxembourg'
    when 'DE' then 'Europe/Berlin'
    when 'IT' then 'Europe/Rome'
    when 'ES' then 'Europe/Madrid'
    when 'PT' then 'Europe/Lisbon'
    when 'GB' then 'Europe/London'
    when 'CA' then 'America/Toronto'
    when 'US' then 'America/New_York'
    else 'Europe/Paris'
  end;
$$;

-- Réservations encore à acheter, par membre et par événement, dont l'échéance tombe
-- aujourd'hui à 18 h heure locale du destinataire.
-- p_now permet de rejouer la fonction dans les tests sans attendre la bonne heure.
create function private.purchase_reminder_candidates(p_now timestamptz default now())
returns table (user_id uuid, event_id uuid, event_title text, event_date date,
               days_before integer, item_count integer)
language sql stable security definer set search_path = '' as $$
  with pending as (
    select r.user_id, i.event_id, count(*)::int as item_count
    from public.reservations r
    join public.wish_items i on i.id = r.item_id
    where r.status = 'reserved' and not i.owned and i.event_id is not null
    group by r.user_id, i.event_id
  )
  select p.user_id, e.id, e.title, e.event_date, d.days_before, p.item_count
  from pending p
  join public.events e on e.id = p.event_id
  join public.profiles pr on pr.id = p.user_id
  cross join lateral (
    select (e.event_date - (p_now at time zone private.country_timezone(pr.country))::date) as days_before
  ) d
  where pr.notify_purchase_reminders
    and d.days_before in (2, 7, 15, 30)
    -- 18 h dans le fuseau du destinataire : le cron tourne toutes les heures.
    and extract(hour from (p_now at time zone private.country_timezone(pr.country))) = 18
    and not exists (
      select 1 from public.purchase_reminders_sent s
      where s.user_id = p.user_id and s.event_id = p.event_id and s.days_before = d.days_before
    );
$$;

-- Réserve les rappels à envoyer : un second appel le même jour ne renvoie rien.
create function public.claim_purchase_reminders(p_now timestamptz default now())
returns table (user_id uuid, event_id uuid, event_title text, event_date date,
               days_before integer, item_count integer)
language plpgsql volatile security definer set search_path = '' as $$
begin
  return query
  with due as (select * from private.purchase_reminder_candidates(p_now)),
  claimed as (
    insert into public.purchase_reminders_sent as s (user_id, event_id, days_before)
    select d.user_id, d.event_id, d.days_before from due d
    on conflict do nothing
    returning s.user_id, s.event_id, s.days_before
  )
  select d.user_id, d.event_id, d.event_title, d.event_date, d.days_before, d.item_count
  from due d join claimed c on (c.user_id, c.event_id, c.days_before) = (d.user_id, d.event_id, d.days_before);
end;
$$;

revoke execute on function
  private.purchase_reminder_candidates(timestamptz),
  public.claim_purchase_reminders(timestamptz),
  private.country_timezone(text)
from public, anon, authenticated;
grant execute on function
  private.purchase_reminder_candidates(timestamptz),
  public.claim_purchase_reminders(timestamptz)
to service_role;

-- Toutes les heures : seuls les profils pour qui il est 18 h localement sont servis.
select cron.schedule('purchase-reminders', '0 * * * *',
                     $$select private.invoke_edge_function('purchase-reminders')$$);
