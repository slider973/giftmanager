-- Fonctionnalités v2 : cagnotte (#35), listes d'adultes (#37), équilibre (#41),
-- remerciements (#42), anniversaires automatiques (#43).
-- Règle inchangée : les « parents » d'une liste (membres du foyer) n'apprennent jamais qui offre quoi.

-- ---------------------------------------------------------------------------
-- #37 Listes d'adultes : un adulte est une entrée de `children` marquée is_adult.
-- Les membres de son foyer (lui-même et son conjoint) sont ses « parents » : mode surprise identique.
-- ---------------------------------------------------------------------------
alter table public.children add column is_adult boolean not null default false;

-- ---------------------------------------------------------------------------
-- #42 Levée volontaire de l'anonymat
-- ---------------------------------------------------------------------------
alter table public.reservations add column revealed boolean not null default false;

-- ---------------------------------------------------------------------------
-- #35 Cagnotte
-- ---------------------------------------------------------------------------
create table public.contributions (
  id uuid primary key default gen_random_uuid(),
  item_id uuid not null references public.wish_items (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade default auth.uid(),
  amount numeric(10, 2) not null check (amount > 0 and amount < 100000000),
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  revealed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (item_id, user_id)
);
create index on public.contributions (user_id);

create trigger contributions_touch before update on public.contributions
  for each row execute function public.touch_updated_at();

create function private.is_pot_participant(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.contributions where item_id = p_item and user_id = auth.uid());
$$;

-- Donateur d'un cadeau : auteur de la réservation ou participant à la cagnotte.
create function private.is_item_donor(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.reservations where item_id = p_item and user_id = auth.uid())
      or exists (select 1 from public.contributions where item_id = p_item and user_id = auth.uid());
$$;

alter table public.contributions enable row level security;
revoke all on public.contributions from anon;
-- Lecture : sa propre participation, ou celles des autres participants de la même cagnotte.
create policy "cagnotte : participants entre eux" on public.contributions for select to authenticated
  using (user_id = auth.uid() or private.is_pot_participant(item_id));

-- Participer (ou modifier sa participation). Les parents ne peuvent pas participer :
-- même réponse qu'un cadeau déjà pris, pour ne rien révéler.
create function public.join_pot(p_item uuid, p_amount numeric, p_currency text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_item public.wish_items;
  v_currency text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not private.can_view_item(p_item) then raise exception 'ITEM_NOT_FOUND'; end if;
  select * into v_item from public.wish_items where id = p_item;
  if v_item.owned then raise exception 'ITEM_OWNED'; end if;
  if private.is_parent_of(v_item.child_id) then raise exception 'ITEM_UNAVAILABLE'; end if;
  -- Verrou sur le cadeau : sérialise réservation et cagnotte.
  perform 1 from public.wish_items where id = p_item for update;
  if exists (select 1 from public.reservations where item_id = p_item) then raise exception 'ITEM_UNAVAILABLE'; end if;
  select currency into v_currency from public.contributions where item_id = p_item and user_id <> auth.uid() limit 1;
  if v_currency is not null and v_currency <> upper(p_currency) then raise exception 'CURRENCY_MISMATCH'; end if;
  insert into public.contributions (item_id, user_id, amount, currency)
  values (p_item, auth.uid(), p_amount, upper(p_currency))
  on conflict (item_id, user_id) do update set amount = excluded.amount, currency = excluded.currency;
end;
$$;

create function public.leave_pot(p_item uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  delete from public.contributions where item_id = p_item and user_id = auth.uid();
  if not found then raise exception 'RESERVATION_NOT_FOUND'; end if;
end;
$$;

-- Participants d'une cagnotte (prénom, montant) : réservé aux participants, jamais aux parents.
create function public.pot_participants(p_item uuid)
returns table (display_name text, amount numeric, currency text, is_me boolean)
language sql stable security definer set search_path = '' as $$
  select p.display_name, c.amount, c.currency, c.user_id = auth.uid()
  from public.contributions c
  join public.profiles p on p.id = c.user_id
  join public.wish_items i on i.id = c.item_id
  where c.item_id = p_item
    and private.is_pot_participant(p_item)
    and not private.is_parent_of(i.child_id)
  order by c.created_at;
$$;

-- La réservation simple refuse un cadeau en cagnotte.
create or replace function public.reserve_item(p_item uuid) returns text
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_owned boolean;
  v_holder uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not private.can_view_item(p_item) then raise exception 'ITEM_NOT_FOUND'; end if;
  select owned into v_owned from public.wish_items where id = p_item for update;
  if v_owned then raise exception 'ITEM_OWNED'; end if;
  if exists (select 1 from public.contributions where item_id = p_item) then raise exception 'ITEM_UNAVAILABLE'; end if;

  insert into public.reservations (item_id, user_id) values (p_item, auth.uid())
  on conflict (item_id) do nothing;

  select user_id into v_holder from public.reservations where item_id = p_item;
  if v_holder <> auth.uid() then raise exception 'ITEM_UNAVAILABLE'; end if;
  return 'reserved';
end;
$$;

-- Statut public, avec la cagnotte.
create or replace function public.item_public_status(p_item uuid) returns text
language plpgsql stable security definer set search_path = '' as $$
declare
  v_item public.wish_items;
  v_holder uuid;
begin
  select * into v_item from public.wish_items where id = p_item;
  if v_item.id is null or not private.can_view_item(p_item) or private.is_parent_of(v_item.child_id) then
    return null;
  end if;
  if v_item.owned then return 'owned'; end if;
  if exists (select 1 from public.contributions where item_id = p_item) then return 'pot'; end if;
  select user_id into v_holder from public.reservations where item_id = p_item;
  if v_holder is null then return 'available'; end if;
  if v_holder = auth.uid() then return 'mine'; end if;
  return 'taken';
end;
$$;

-- child_items enrichi : progression de cagnotte (jamais pour les parents).
drop function public.child_items(uuid, uuid, text);
create function public.child_items(p_child uuid, p_event uuid default null, p_kind text default null)
returns table (
  id uuid, child_id uuid, event_id uuid, kind text, title text, notes text, image_url text,
  priority smallint, "position" integer, owned boolean, created_by uuid, created_at timestamptz,
  status text, my_reservation text,
  pot_total numeric, pot_currency text, pot_count integer, my_contribution numeric
)
language sql stable security definer set search_path = '' as $$
  select i.id, i.child_id, i.event_id, i.kind, i.title, i.notes, i.image_url,
         i.priority, i.position, i.owned, i.created_by, i.created_at,
         case
           when parent.is_parent then null
           when i.owned then 'owned'
           when pot.count > 0 then 'pot'
           when r.user_id is null then 'available'
           when r.user_id = auth.uid() then 'mine'
           else 'taken'
         end,
         case when r.user_id = auth.uid() then r.status end,
         case when parent.is_parent then null else pot.total end,
         case when parent.is_parent then null else pot.currency end,
         case when parent.is_parent then null else pot.count end,
         case when parent.is_parent then null else pot.mine end
  from public.wish_items i
  cross join lateral (select private.is_parent_of(i.child_id) as is_parent) parent
  left join public.reservations r on r.item_id = i.id
  left join lateral (
    select sum(c.amount) as total, min(c.currency) as currency, count(*)::int as count,
           sum(c.amount) filter (where c.user_id = auth.uid()) as mine
    from public.contributions c where c.item_id = i.id
  ) pot on true
  where i.child_id = p_child
    and private.can_view_child(i.child_id)
    and (i.kind = 'wish' or not parent.is_parent)
    and (p_kind is null or i.kind = p_kind)
    and (p_event is null or i.event_id = p_event or i.event_id is null)
  order by i.owned, i.priority desc, i.position, i.created_at;
$$;

-- « Mes cagnottes » : mes participations.
create function public.my_contributions()
returns table (
  item_id uuid, title text, image_url text, amount numeric, currency text, owned boolean,
  pot_total numeric, pot_count integer, child_id uuid, child_name text,
  event_id uuid, event_title text, event_date date
)
language sql stable security definer set search_path = '' as $$
  select c.item_id, i.title, i.image_url, c.amount, c.currency, i.owned,
         (select sum(x.amount) from public.contributions x where x.item_id = c.item_id),
         (select count(*)::int from public.contributions x where x.item_id = c.item_id),
         ch.id, ch.first_name, e.id, e.title, e.event_date
  from public.contributions c
  join public.wish_items i on i.id = c.item_id
  join public.children ch on ch.id = i.child_id
  left join public.events e on e.id = i.event_id
  where c.user_id = auth.uid()
  order by e.event_date nulls last, ch.first_name, i.title;
$$;

-- ---------------------------------------------------------------------------
-- #41 Équilibre : nombre de cadeaux pris par enfant (sans identité), jamais pour ses propres listes.
-- ---------------------------------------------------------------------------
create function public.children_reservation_counts(p_event uuid default null)
returns table (child_id uuid, reserved_count integer, purchased_count integer, pot_count integer)
language sql stable security definer set search_path = '' as $$
  select ch.id,
         count(*) filter (where r.status = 'reserved')::int,
         count(*) filter (where r.status = 'purchased')::int,
         count(distinct c.item_id)::int
  from public.children ch
  join public.households h on h.id = ch.household_id
  left join public.wish_items i on i.child_id = ch.id and not i.owned
       and (p_event is null or i.event_id = p_event or i.event_id is null)
  left join public.reservations r on r.item_id = i.id
  left join public.contributions c on c.item_id = i.id
  where private.is_group_member(h.group_id)
    and not private.is_parent_of(ch.id)
  group by ch.id;
$$;

-- ---------------------------------------------------------------------------
-- #42 Remerciements
-- ---------------------------------------------------------------------------
create table public.thanks (
  id uuid primary key default gen_random_uuid(),
  item_id uuid not null references public.wish_items (id) on delete cascade,
  sender_id uuid references auth.users (id) on delete set null default auth.uid(),
  message text not null check (char_length(message) between 1 and 1000),
  photo_url text,
  created_at timestamptz not null default now()
);
create index on public.thanks (item_id);

alter table public.thanks enable row level security;
revoke all on public.thanks from anon;
-- Lisible par l'expéditeur et par les donateurs du cadeau (le serveur « route » le message).
create policy "remerciements : expéditeur et donateurs" on public.thanks for select to authenticated
  using (sender_id = auth.uid() or private.is_item_donor(item_id));

-- Envoyé par un parent de l'enfant. Réponse identique qu'il y ait un donateur ou non.
create function public.send_thanks(p_item uuid, p_message text, p_photo_url text default null) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_child uuid;
begin
  select child_id into v_child from public.wish_items where id = p_item;
  if v_child is null or not private.is_parent_of(v_child) then raise exception 'ITEM_NOT_FOUND'; end if;
  insert into public.thanks (item_id, sender_id, message, photo_url) values (p_item, auth.uid(), p_message, p_photo_url);
end;
$$;

-- Le donateur choisit de se faire connaître des parents pour ce cadeau.
create function public.reveal_myself(p_item uuid, p_reveal boolean default true) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  update public.reservations set revealed = p_reveal where item_id = p_item and user_id = auth.uid();
  update public.contributions set revealed = p_reveal where item_id = p_item and user_id = auth.uid();
end;
$$;

-- Donateurs qui se sont fait connaître (pour les parents de l'enfant uniquement).
create function public.item_donors(p_item uuid) returns table (display_name text)
language sql stable security definer set search_path = '' as $$
  select p.display_name
  from public.wish_items i
  join lateral (
    select r.user_id from public.reservations r where r.item_id = i.id and r.revealed
    union
    select c.user_id from public.contributions c where c.item_id = i.id and c.revealed
  ) d on true
  join public.profiles p on p.id = d.user_id
  where i.id = p_item and private.is_parent_of(i.child_id);
$$;

-- Remerciements reçus pour mes cadeaux.
create function public.my_thanks()
returns table (id uuid, item_id uuid, title text, child_name text, sender_name text,
               message text, photo_url text, created_at timestamptz, revealed boolean)
language sql stable security definer set search_path = '' as $$
  select t.id, t.item_id, i.title, ch.first_name, p.display_name, t.message, t.photo_url, t.created_at,
         coalesce((select r.revealed from public.reservations r where r.item_id = t.item_id and r.user_id = auth.uid()),
                  (select c.revealed from public.contributions c where c.item_id = t.item_id and c.user_id = auth.uid()), false)
  from public.thanks t
  join public.wish_items i on i.id = t.item_id
  join public.children ch on ch.id = i.child_id
  left join public.profiles p on p.id = t.sender_id
  where private.is_item_donor(t.item_id)
  order by t.created_at desc;
$$;

-- ---------------------------------------------------------------------------
-- #43 Anniversaires automatiques
-- ---------------------------------------------------------------------------
create function private.ensure_birthday_event(p_child uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_child public.children;
  v_group uuid;
  v_next date;
begin
  select * into v_child from public.children where id = p_child;
  if v_child.birthdate is null then return; end if;
  select group_id into v_group from public.households where id = v_child.household_id;
  -- Anniversaire de cette année (29 février → 28 février les années non bissextiles), sinon l'an prochain.
  v_next := (v_child.birthdate + make_interval(years => extract(year from age(current_date, v_child.birthdate))::int))::date;
  if v_next < current_date then
    v_next := (v_child.birthdate + make_interval(years => extract(year from age(current_date, v_child.birthdate))::int + 1))::date;
  end if;
  if exists (select 1 from public.events
             where child_id = p_child and kind = 'birthday' and event_date >= current_date) then
    return;
  end if;
  insert into public.events (group_id, kind, title, event_date, child_id, created_by)
  values (v_group, 'birthday', 'Anniversaire de ' || v_child.first_name, v_next, p_child, null);
end;
$$;

create function private.children_birthday_trigger() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform private.ensure_birthday_event(new.id);
  return new;
end;
$$;

create trigger children_birthday after insert or update of birthdate on public.children
  for each row execute function private.children_birthday_trigger();

-- Appelée par l'app au chargement : crée l'anniversaire de l'année suivante une fois le précédent passé.
create function public.ensure_birthday_events(p_group uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_child uuid;
begin
  if not private.is_group_member(p_group) then return; end if;
  for v_child in
    select c.id from public.children c join public.households h on h.id = c.household_id
    where h.group_id = p_group and c.birthdate is not null
  loop
    perform private.ensure_birthday_event(v_child);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Droits
-- ---------------------------------------------------------------------------
revoke execute on all functions in schema public from public, anon;
revoke execute on all functions in schema private from public, anon;
grant execute on function
  public.join_pot(uuid, numeric, text),
  public.leave_pot(uuid),
  public.pot_participants(uuid),
  public.reserve_item(uuid),
  public.item_public_status(uuid),
  public.child_items(uuid, uuid, text),
  public.my_contributions(),
  public.children_reservation_counts(uuid),
  public.send_thanks(uuid, text, text),
  public.reveal_myself(uuid, boolean),
  public.item_donors(uuid),
  public.my_thanks(),
  public.ensure_birthday_events(uuid)
to authenticated;
grant execute on function
  private.is_pot_participant(uuid),
  private.is_item_donor(uuid)
to authenticated;
