-- #60 — Foyer partagé entre plusieurs familles.
--
-- Avant : children → households → groups. Un enfant appartenait à UN groupe ; rejoindre une
-- seconde famille obligeait à recréer le foyer et les enfants, avec deux listes de souhaits
-- distinctes et aucun anti-doublon entre familles.
--
-- Après : le foyer appartient à ses parents et se *partage* vers un ou plusieurs groupes
-- (table household_groups). Un enfant est unique ; il est visible dans toutes les familles où
-- son foyer est partagé. Une réservation faite dans un groupe rend le cadeau « pris » dans tous
-- les groupes où l'enfant est visible (décision produit, voir docs/SPEC.md) — jamais l'identité.
--
-- Les événements suivent la même logique :
--   christmas → global (group_id null, child_id null) : une seule « Noël <année> » pour tous ;
--   birthday  → porté par l'enfant (group_id null, child_id non null) ;
--   other     → reste propre à un groupe.

-- ---------------------------------------------------------------------------
-- 1. Table de partage
-- ---------------------------------------------------------------------------

create table public.household_groups (
  household_id uuid not null references public.households (id) on delete cascade,
  group_id uuid not null references public.groups (id) on delete cascade,
  shared_at timestamptz not null default now(),
  primary key (household_id, group_id)
);
create index on public.household_groups (group_id);

insert into public.household_groups (household_id, group_id)
select id, group_id from public.households;

-- ---------------------------------------------------------------------------
-- 2. Fusion des foyers en double (même utilisateur, un foyer par groupe)
--    Prudence : on ne fusionne que les foyers dont l'ensemble des parents est identique.
--
--    Le trigger wish_items_check_event vérifie qu'un cadeau vise un événement du groupe
--    de son enfant. Pendant la fusion, les cadeaux changent d'enfant avant que les
--    événements ne soient regroupés (section 3) : la vérification est donc prématurée et
--    doit être suspendue. Elle est rétablie juste après, et le trigger lui-même est
--    remplacé en section 7 par une version qui connaît les foyers partagés.
-- ---------------------------------------------------------------------------

alter table public.wish_items disable trigger wish_items_check_event;

do $$
declare
  v_keep uuid;
  v_drop uuid;
begin
  for v_keep, v_drop in
    with members as (
      select household_id, array_agg(user_id order by user_id) as parents
      from public.household_members group by household_id
    ),
    ranked as (
      select h.id, m.parents,
             first_value(h.id) over (partition by m.parents order by h.created_at, h.id) as keep_id
      from public.households h join members m on m.household_id = h.id
    )
    select keep_id, id from ranked where id <> keep_id
  loop
    -- Le foyer conservé hérite des groupes de celui qu'on supprime.
    insert into public.household_groups (household_id, group_id)
    select v_keep, group_id from public.household_groups where household_id = v_drop
    on conflict do nothing;

    -- Enfants : appariés par prénom (insensible à la casse) et date de naissance.
    update public.wish_items i set child_id = k.id
    from public.children d
    join public.children k on k.household_id = v_keep
      and lower(k.first_name) = lower(d.first_name)
      and k.birthdate is not distinct from d.birthdate
    where d.household_id = v_drop and i.child_id = d.id;

    update public.events e set child_id = k.id
    from public.children d
    join public.children k on k.household_id = v_keep
      and lower(k.first_name) = lower(d.first_name)
      and k.birthdate is not distinct from d.birthdate
    where d.household_id = v_drop and e.child_id = d.id;

    update public.budgets b set child_id = k.id
    from public.children d
    join public.children k on k.household_id = v_keep
      and lower(k.first_name) = lower(d.first_name)
      and k.birthdate is not distinct from d.birthdate
    where d.household_id = v_drop and b.child_id = d.id;

    -- Enfant sans équivalent dans le foyer conservé : on le rattache tel quel.
    update public.children d set household_id = v_keep
    where d.household_id = v_drop
      and not exists (
        select 1 from public.children k where k.household_id = v_keep
          and lower(k.first_name) = lower(d.first_name)
          and k.birthdate is not distinct from d.birthdate
      );

    delete from public.households where id = v_drop;  -- cascade : enfants en double, membres
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. Événements : Noël global, anniversaires portés par l'enfant
-- ---------------------------------------------------------------------------

alter table public.events alter column group_id drop not null;

-- Un seul Noël par date : on garde le plus ancien et on y rattache cadeaux et budgets.
do $$
declare
  v_keep uuid;
  v_drop uuid;
begin
  for v_keep, v_drop in
    select first_value(id) over (partition by event_date order by created_at, id), id
    from public.events where kind = 'christmas'
  loop
    if v_keep = v_drop then continue; end if;
    update public.wish_items set event_id = v_keep where event_id = v_drop;
    update public.budgets set event_id = v_keep where event_id = v_drop;
    delete from public.events where id = v_drop;
  end loop;
end;
$$;

update public.events set group_id = null, child_id = null where kind = 'christmas';

-- Un seul anniversaire futur par enfant.
do $$
declare
  v_keep uuid;
  v_drop uuid;
begin
  for v_keep, v_drop in
    select first_value(id) over (partition by child_id order by event_date, created_at, id), id
    from public.events where kind = 'birthday'
  loop
    if v_keep = v_drop then continue; end if;
    update public.wish_items set event_id = v_keep where event_id = v_drop;
    update public.budgets set event_id = v_keep where event_id = v_drop;
    delete from public.events where id = v_drop;
  end loop;
end;
$$;

update public.events set group_id = null where kind = 'birthday';

alter table public.events
  add constraint events_scope_check check (
    (kind = 'christmas' and group_id is null and child_id is null)
    or (kind = 'birthday' and group_id is null and child_id is not null)
    or (kind = 'other' and group_id is not null)
  );

create unique index events_christmas_unique on public.events (event_date) where kind = 'christmas';

-- Les événements sont regroupés : la cohérence cadeau ↔ événement peut de nouveau être vérifiée.
alter table public.wish_items enable trigger wish_items_check_event;

-- ---------------------------------------------------------------------------
-- 4. Le foyer n'est plus rattaché à un groupe
--    Les policies qui lisent households.group_id sont supprimées d'abord ;
--    elles sont réécrites en section 6, une fois les fonctions d'accès en place.
-- ---------------------------------------------------------------------------

drop policy "foyers visibles par le groupe" on public.households;
drop policy "créer un foyer dans son groupe" on public.households;
drop policy "enfants visibles par le groupe" on public.children;
drop policy "parents visibles par le groupe" on public.household_members;

drop index if exists public.households_group_id_idx;
alter table public.households drop column group_id;

alter table public.household_members drop constraint household_members_group_id_user_id_key;
alter table public.household_members drop column group_id;
-- Un utilisateur a désormais un seul foyer, toutes familles confondues.
alter table public.household_members add constraint household_members_user_unique unique (user_id);

-- ---------------------------------------------------------------------------
-- 5. Fonctions d'accès
-- ---------------------------------------------------------------------------

-- Remplace private.child_group (un seul groupe) : un enfant peut être visible dans plusieurs.
create function private.child_groups(p_child uuid) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select hg.group_id
  from public.children c
  join public.household_groups hg on hg.household_id = c.household_id
  where c.id = p_child;
$$;

create or replace function private.can_view_child(p_child uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
    from public.children c
    join public.household_groups hg on hg.household_id = c.household_id
    join public.group_members gm on gm.group_id = hg.group_id
    where c.id = p_child and gm.user_id = auth.uid()
  );
$$;

create function private.can_view_household(p_household uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.household_groups hg
    join public.group_members gm on gm.group_id = hg.group_id
    where hg.household_id = p_household and gm.user_id = auth.uid()
  );
$$;

-- Noël est global ; un anniversaire suit son enfant ; les autres événements leur groupe.
create function private.can_view_event(p_event uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.events e
    where e.id = p_event
      and (
        (e.group_id is null and e.child_id is null)
        or (e.child_id is not null and private.can_view_child(e.child_id))
        or (e.group_id is not null and private.is_group_member(e.group_id))
      )
  );
$$;

-- child_group (un seul groupe) n'a plus de sens : les policies qui s'en servent sont
-- réécrites juste après, puis la fonction est supprimée en fin de section 6.

-- ---------------------------------------------------------------------------
-- 6. Policies qui supposaient un groupe unique
--    (celles qui lisaient households.group_id ont déjà été supprimées en section 4)
-- ---------------------------------------------------------------------------

create policy "foyers visibles par les familles où ils sont partagés" on public.households
  for select to authenticated
  using (private.can_view_household(id) or private.is_household_member(id));

create policy "créer son foyer" on public.households for insert to authenticated
  with check (true);  -- create_household l'associe immédiatement à son créateur

create policy "parents visibles par les familles du foyer" on public.household_members
  for select to authenticated
  using (private.can_view_household(household_id) or user_id = auth.uid());

create policy "enfants visibles par les familles du foyer" on public.children
  for select to authenticated
  using (private.can_view_child(id) or private.is_household_member(household_id));

alter table public.household_groups enable row level security;
revoke all on public.household_groups from anon;
create policy "partages visibles par le groupe et les parents" on public.household_groups
  for select to authenticated
  using (private.is_group_member(group_id) or private.is_household_member(household_id));
-- Écriture via share_household_with_group / unshare_household_from_group uniquement.

drop policy "événements visibles par le groupe" on public.events;
create policy "événements visibles selon leur portée" on public.events for select to authenticated
  using (
    (group_id is null and child_id is null)
    or (child_id is not null and private.can_view_child(child_id))
    or (group_id is not null and private.is_group_member(group_id))
  );

drop policy "événements créés par les membres" on public.events;
create policy "événements créés par les membres" on public.events for insert to authenticated
  with check (
    kind = 'other' and group_id is not null and private.is_group_member(group_id)
    and (child_id is null or private.can_view_child(child_id))
  );

drop policy "événements modifiés par les membres" on public.events;
create policy "événements modifiés par les membres" on public.events for update to authenticated
  using (group_id is not null and private.is_group_member(group_id))
  with check (group_id is not null and private.is_group_member(group_id));

drop policy "événements supprimés par leur auteur ou les parents" on public.events;
create policy "événements supprimés par leur auteur ou les parents" on public.events
  for delete to authenticated
  using (
    (group_id is not null and created_by = auth.uid())
    or (child_id is not null and private.is_parent_of(child_id))
  );

drop policy "budgets : création dans son groupe" on public.budgets;
create policy "budgets : création sur ce qu'on voit" on public.budgets for insert to authenticated
  with check (
    user_id = auth.uid()
    and (child_id is null or private.can_view_child(child_id))
    and (event_id is null or private.can_view_event(event_id))
  );

drop policy "budgets : modification des siens" on public.budgets;
create policy "budgets : modification des siens" on public.budgets for update to authenticated
  using (user_id = auth.uid())
  with check (
    user_id = auth.uid()
    and (child_id is null or private.can_view_child(child_id))
    and (event_id is null or private.can_view_event(event_id))
  );

-- Plus aucune policy ne dépend de child_group : la fonction peut disparaître.
drop function private.child_group(uuid);

-- ---------------------------------------------------------------------------
-- 7. Fonctions métier
-- ---------------------------------------------------------------------------

-- Noël de l'année courante, créé une seule fois pour toute l'application.
create function private.ensure_christmas_event() returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_date date := make_date(extract(year from now())::int, 12, 25);
  v_id uuid;
begin
  if v_date < current_date then v_date := v_date + interval '1 year'; end if;
  select id into v_id from public.events where kind = 'christmas' and event_date = v_date;
  if v_id is null then
    insert into public.events (group_id, kind, title, event_date, child_id, created_by)
    values (null, 'christmas', 'Noël ' || extract(year from v_date), v_date, null, null)
    on conflict do nothing
    returning id into v_id;
    if v_id is null then
      select id into v_id from public.events where kind = 'christmas' and event_date = v_date;
    end if;
  end if;
  return v_id;
end;
$$;

-- Partage le foyer de l'utilisateur avec un groupe dont il est membre.
create function public.share_household_with_group(p_group uuid) returns uuid
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_household uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not private.is_group_member(p_group) then raise exception 'NOT_A_MEMBER'; end if;
  select household_id into v_household from public.household_members where user_id = auth.uid();
  if v_household is null then raise exception 'NO_HOUSEHOLD'; end if;
  insert into public.household_groups (household_id, group_id) values (v_household, p_group)
  on conflict do nothing;
  return v_household;
end;
$$;

-- Retire le partage. Les cadeaux ne sont plus visibles de ce groupe ; les réservations restent.
create function public.unshare_household_from_group(p_group uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_household uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select household_id into v_household from public.household_members where user_id = auth.uid();
  if v_household is null then raise exception 'NO_HOUSEHOLD'; end if;
  delete from public.household_groups where household_id = v_household and group_id = p_group;
end;
$$;

create or replace function public.create_group(p_name text, p_household_name text, p_country text default 'CH')
returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_group uuid;
  v_household uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;

  insert into public.groups (name, created_by) values (p_name, auth.uid()) returning id into v_group;
  insert into public.group_members (group_id, user_id) values (v_group, auth.uid());

  -- Le foyer existant est partagé ; sinon on le crée.
  select household_id into v_household from public.household_members where user_id = auth.uid();
  if v_household is null then
    insert into public.households (name, country) values (p_household_name, p_country)
      returning id into v_household;
    insert into public.household_members (household_id, user_id) values (v_household, auth.uid());
  end if;
  insert into public.household_groups (household_id, group_id) values (v_household, v_group)
  on conflict do nothing;

  perform private.ensure_christmas_event();
  return v_group;
end;
$$;

create or replace function public.join_group(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_group uuid;
  v_household uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select id into v_group from public.groups where invite_code = upper(trim(p_code));
  if v_group is null then raise exception 'INVALID_INVITE_CODE'; end if;
  insert into public.group_members (group_id, user_id) values (v_group, auth.uid()) on conflict do nothing;
  -- Les enfants suivent leur parent : plus besoin de les recréer dans la nouvelle famille.
  select household_id into v_household from public.household_members where user_id = auth.uid();
  if v_household is not null then
    insert into public.household_groups (household_id, group_id) values (v_household, v_group)
    on conflict do nothing;
  end if;
  return v_group;
end;
$$;

-- Crée le foyer de l'utilisateur s'il n'en a pas, puis le partage avec le groupe.
-- Idempotent : rappelée avec un foyer existant, elle se contente de le partager.
create or replace function public.create_household(p_group uuid, p_name text, p_country text default 'CH')
returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_household uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not private.is_group_member(p_group) then raise exception 'NOT_A_MEMBER'; end if;
  select household_id into v_household from public.household_members where user_id = auth.uid();
  if v_household is null then
    insert into public.households (name, country) values (p_name, p_country) returning id into v_household;
    insert into public.household_members (household_id, user_id) values (v_household, auth.uid());
  end if;
  insert into public.household_groups (household_id, group_id) values (v_household, p_group)
  on conflict do nothing;
  return v_household;
end;
$$;

-- Second parent : rejoint un foyer avec son code. Il faut partager une famille avec ce foyer.
create or replace function public.join_household(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_household public.households;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_household from public.households where invite_code = upper(trim(p_code));
  if v_household.id is null or not private.can_view_household(v_household.id) then
    raise exception 'INVALID_INVITE_CODE';
  end if;
  if exists (select 1 from public.household_members where user_id = auth.uid()) then
    raise exception 'ALREADY_IN_HOUSEHOLD';
  end if;
  insert into public.household_members (household_id, user_id) values (v_household.id, auth.uid());
  -- Le foyer devient visible dans toutes les familles du nouveau parent.
  insert into public.household_groups (household_id, group_id)
  select v_household.id, gm.group_id from public.group_members gm where gm.user_id = auth.uid()
  on conflict do nothing;
  return v_household.id;
end;
$$;

-- Un cadeau se rattache à Noël (global), à l'anniversaire de son enfant, ou à un événement
-- d'une famille où l'enfant est visible.
create or replace function private.check_item_event() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_event public.events;
begin
  if new.event_id is null then return new; end if;
  select * into v_event from public.events where id = new.event_id;
  if v_event.id is null then raise exception 'INVALID_EVENT'; end if;
  if v_event.kind = 'christmas' then return new; end if;
  if v_event.kind = 'birthday' then
    if v_event.child_id = new.child_id then return new; end if;
    raise exception 'INVALID_EVENT';
  end if;
  if exists (select 1 from private.child_groups(new.child_id) g where g = v_event.group_id) then
    return new;
  end if;
  raise exception 'INVALID_EVENT';
end;
$$;

-- #41 Équilibre : l'enfant est visible via ses familles, pas via un groupe unique.
create or replace function public.children_reservation_counts(p_event uuid default null)
returns table (child_id uuid, reserved_count integer, purchased_count integer, pot_count integer)
language sql stable security definer set search_path = '' as $$
  select ch.id,
         count(*) filter (where r.status = 'reserved')::int,
         count(*) filter (where r.status = 'purchased')::int,
         count(distinct c.item_id)::int
  from public.children ch
  left join public.wish_items i on i.child_id = ch.id and not i.owned
       and (p_event is null or i.event_id = p_event or i.event_id is null)
  left join public.reservations r on r.item_id = i.id
  left join public.contributions c on c.item_id = i.id
  where private.can_view_child(ch.id)
    and not private.is_parent_of(ch.id)
  group by ch.id;
$$;

-- #40 my_budgets : le titre de l'événement suit sa nouvelle portée.
create or replace function public.my_budgets()
returns table (
  id uuid, child_id uuid, child_name text, event_id uuid, event_title text,
  amount numeric, currency text, spent numeric
)
language sql stable security definer set search_path = '' as $$
  select b.id, b.child_id,
         case when b.child_id is not null and private.can_view_child(b.child_id) then c.first_name end,
         b.event_id,
         case when e.id is not null and private.can_view_event(e.id) then e.title end,
         b.amount, b.currency,
         coalesce((
           select sum(x.v) from (
             select (select min(l.price) from public.item_links l
                     where l.item_id = i.id and l.currency = b.currency and l.price is not null) as v
             from public.reservations r
             join public.wish_items i on i.id = r.item_id
             where r.user_id = b.user_id
               and (b.child_id is null or i.child_id = b.child_id)
               and (b.event_id is null or i.event_id = b.event_id)
             union all
             select k.amount
             from public.contributions k
             join public.wish_items i on i.id = k.item_id
             where k.user_id = b.user_id and k.currency = b.currency
               and (b.child_id is null or i.child_id = b.child_id)
               and (b.event_id is null or i.event_id = b.event_id)
           ) x
         ), 0)
  from public.budgets b
  left join public.children c on c.id = b.child_id
  left join public.events e on e.id = b.event_id
  where b.user_id = auth.uid();
$$;

-- #43 Rappels d'anniversaire : destinataires = membres des familles où l'enfant est visible.
create or replace function private.birthday_reminder_candidates(p_today date default current_date)
returns table (event_id uuid, child_name text, event_date date, days_before integer, user_id uuid)
language sql stable security definer set search_path = '' as $$
  select x.event_id, x.child_name, x.event_date, x.bucket, x.user_id
  from (
    select distinct e.id as event_id, c.first_name as child_name, e.event_date,
           case when e.event_date - p_today <= 7 then 7 else 30 end as bucket,
           gm.user_id, c.household_id
    from public.events e
    join public.children c on c.id = e.child_id
    join public.household_groups hg on hg.household_id = c.household_id
    join public.group_members gm on gm.group_id = hg.group_id
    join public.profiles p on p.id = gm.user_id
    where e.kind = 'birthday'
      and e.event_date between p_today and p_today + 30
      and p.notify_birthday_reminders
  ) x
  where not exists (
      select 1 from public.household_members hm
      where hm.household_id = x.household_id and hm.user_id = x.user_id
    )
    and not exists (
      select 1 from public.birthday_reminders_sent s
      where s.event_id = x.event_id and s.user_id = x.user_id and s.days_before <= x.bucket
    );
$$;

-- Anniversaire : un seul par enfant, sans groupe.
create or replace function private.ensure_birthday_event(p_child uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_child public.children;
  v_next date;
  v_title text;
  v_event public.events;
begin
  perform pg_advisory_xact_lock(hashtext(p_child::text));
  select * into v_child from public.children where id = p_child;
  if v_child.id is null or v_child.birthdate is null then return; end if;
  v_next := (v_child.birthdate + make_interval(years => extract(year from age(current_date, v_child.birthdate))::int))::date;
  if v_next < current_date then
    v_next := (v_child.birthdate + make_interval(years => extract(year from age(current_date, v_child.birthdate))::int + 1))::date;
  end if;
  v_title := 'Anniversaire de ' || v_child.first_name;

  select * into v_event from public.events
  where child_id = p_child and kind = 'birthday' and event_date >= current_date
  order by event_date limit 1;
  if v_event.id is not null then
    if v_event.event_date is distinct from v_next or v_event.title is distinct from v_title then
      update public.events set event_date = v_next, title = v_title where id = v_event.id;
      delete from public.birthday_reminders_sent where event_id = v_event.id;
    end if;
    return;
  end if;
  insert into public.events (group_id, kind, title, event_date, child_id, created_by)
  values (null, 'birthday', v_title, v_next, p_child, null);
end;
$$;

-- Rattrapage appelé par l'app : enfants visibles dans ce groupe, plus Noël de l'année.
create or replace function public.ensure_birthday_events(p_group uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_child uuid;
begin
  if not private.is_group_member(p_group) then return; end if;
  perform private.ensure_christmas_event();
  for v_child in
    select c.id from public.children c
    join public.household_groups hg on hg.household_id = c.household_id
    where hg.group_id = p_group and c.birthdate is not null
  loop
    perform private.ensure_birthday_event(v_child);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Droits
-- ---------------------------------------------------------------------------

revoke all on public.household_groups from anon;
grant select on public.household_groups to authenticated;

revoke execute on function
  public.share_household_with_group(uuid),
  public.unshare_household_from_group(uuid)
from public, anon;
grant execute on function
  public.share_household_with_group(uuid),
  public.unshare_household_from_group(uuid)
to authenticated;

revoke execute on function
  private.child_groups(uuid),
  private.can_view_household(uuid),
  private.can_view_event(uuid),
  private.ensure_christmas_event()
from public, anon;
grant execute on function
  private.child_groups(uuid),
  private.can_view_household(uuid),
  private.can_view_event(uuid)
to authenticated;
