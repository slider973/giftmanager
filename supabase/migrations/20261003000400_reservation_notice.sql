-- #58 — Notification anonyme quand un cadeau est réservé.
--
-- Sans elle, deux personnes peuvent acheter le même cadeau entre deux ouvertures de l'app :
-- le problème que giftmanager existe pour résoudre. La notification doit donc prévenir les
-- autres membres sans jamais révéler l'acheteur, et sans rien dire aux parents de l'enfant.
--
-- Garde-fous contre le recoupement (docs/SPEC.md § Règles d'anonymat) :
--   - le message ne contient ni nom d'acheteur ni titre de cadeau ;
--   - aucun envoi aux parents de l'enfant ni à l'auteur de la réservation ;
--   - un seul envoi par enfant et par tranche de 30 minutes, agrégé ;
--   - aucun envoi si moins de 2 destinataires : à un seul, « quelqu'un a réservé » désigne
--     forcément l'unique autre membre.
-- Rien n'est notifié sur « acheté » ni sur une annulation : l'un n'intéresse que l'acheteur,
-- l'autre permettrait de déduire qui a changé d'avis.

create table public.reservation_notifications_sent (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references public.children (id) on delete cascade,
  sent_at timestamptz not null default now(),
  item_count integer not null default 1
);
create index on public.reservation_notifications_sent (child_id, sent_at desc);

alter table public.reservation_notifications_sent enable row level security;
revoke all on public.reservation_notifications_sent from anon, authenticated;

-- Destinataires d'une notification de réservation, et agrégation sur 30 minutes.
-- Appelée par la fonction Edge avec le service role, après vérification du JWT appelant.
-- Renvoie une ligne vide quand il ne faut rien envoyer (fenêtre, audience trop faible).
create function public.claim_reservation_notice(p_item uuid, p_user uuid)
returns table (child_id uuid, child_name text, item_count integer, available_count integer, user_id uuid)
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_child uuid;
  v_name text;
  v_window constant interval := interval '30 minutes';
  v_since timestamptz := now() - v_window;
  v_recent integer;
  v_audience uuid[];
  v_available integer;
begin
  -- La réservation doit exister, appartenir à l'appelant et être récente (anti-rejeu).
  select i.child_id, c.first_name into v_child, v_name
  from public.reservations r
  join public.wish_items i on i.id = r.item_id
  join public.children c on c.id = i.child_id
  where r.item_id = p_item and r.user_id = p_user and r.created_at > now() - interval '2 minutes';
  if v_child is null then return; end if;

  -- Verrou par enfant : deux réservations simultanées ne produisent pas deux notifications.
  perform pg_advisory_xact_lock(hashtext('resa-notice-' || v_child::text));

  -- Membres des familles où l'enfant est visible, hors parents et hors acheteur.
  select array_agg(distinct gm.user_id) into v_audience
  from public.children c
  join public.household_groups hg on hg.household_id = c.household_id
  join public.group_members gm on gm.group_id = hg.group_id
  where c.id = v_child
    and gm.user_id <> p_user
    and not exists (
      select 1 from public.household_members hm
      where hm.household_id = c.household_id and hm.user_id = gm.user_id
    );
  if v_audience is null or array_length(v_audience, 1) < 2 then return; end if;

  -- Réservations de cet enfant dans la fenêtre : le message les agrège.
  select count(*)::int into v_recent
  from public.reservations r
  join public.wish_items i on i.id = r.item_id
  where i.child_id = v_child and r.created_at > v_since;

  -- Une notification a déjà couvert cette fenêtre : on se tait.
  if exists (
    select 1 from public.reservation_notifications_sent s
    where s.child_id = v_child and s.sent_at > v_since
  ) then
    return;
  end if;

  select count(*)::int into v_available
  from public.wish_items i
  where i.child_id = v_child and i.kind = 'wish' and not i.owned
    and not exists (select 1 from public.reservations r where r.item_id = i.id);

  insert into public.reservation_notifications_sent (child_id, item_count)
  values (v_child, greatest(v_recent, 1));

  return query
    select v_child, v_name, greatest(v_recent, 1), v_available, unnest(v_audience);
end;
$$;

revoke execute on function public.claim_reservation_notice(uuid, uuid) from public, anon, authenticated;
grant execute on function public.claim_reservation_notice(uuid, uuid) to service_role;
