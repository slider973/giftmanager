-- Famille Cadeaux — schéma principal et règles d'accès (RLS).
-- Voir docs/SPEC.md. Principe : un utilisateur ne voit que les groupes dont il est membre ;
-- les parents gèrent leurs enfants et leurs listes ; les idées d'un enfant sont invisibles pour ses parents.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 60),
  country text not null default 'CH' check (country ~ '^[A-Z]{2}$'),
  currency text not null default 'CHF' check (currency ~ '^[A-Z]{3}$'),
  onboarded boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 60),
  invite_code text not null unique,
  created_by uuid references auth.users (id) on delete set null default auth.uid(),
  created_at timestamptz not null default now()
);

create table public.group_members (
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index on public.group_members (user_id);

create table public.households (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 60),
  country text not null default 'CH' check (country ~ '^[A-Z]{2}$'),
  -- Code à partager par un parent pour qu'un second parent rejoigne le foyer.
  invite_code text not null unique default upper(substr(md5(gen_random_uuid()::text), 1, 8)),
  created_at timestamptz not null default now()
);
create index on public.households (group_id);

-- Parents d'un foyer. Un utilisateur appartient à au plus un foyer par groupe.
create table public.household_members (
  household_id uuid not null references public.households (id) on delete cascade,
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  primary key (household_id, user_id),
  unique (group_id, user_id)
);
create index on public.household_members (user_id);

create table public.children (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  first_name text not null check (char_length(first_name) between 1 and 40),
  birthdate date,
  avatar_emoji text,
  avatar_color text,
  avatar_url text,
  created_at timestamptz not null default now()
);
create index on public.children (household_id);

create table public.events (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  kind text not null check (kind in ('christmas', 'birthday', 'other')),
  title text not null check (char_length(title) between 1 and 80),
  event_date date not null,
  child_id uuid references public.children (id) on delete cascade,
  created_by uuid references auth.users (id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  check (kind <> 'birthday' or child_id is not null)
);
create index on public.events (group_id, event_date);

-- Cadeaux : souhaits (kind = wish, gérés par les parents) et idées (kind = idea, proposées
-- par les autres membres, invisibles pour les parents de l'enfant).
create table public.wish_items (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references public.children (id) on delete cascade,
  event_id uuid references public.events (id) on delete set null,
  kind text not null default 'wish' check (kind in ('wish', 'idea')),
  title text not null check (char_length(title) between 1 and 200),
  notes text check (char_length(notes) <= 2000),
  image_url text,
  priority smallint not null default 0 check (priority in (0, 1)),
  position integer not null default 0,
  owned boolean not null default false,
  created_by uuid references auth.users (id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (not (owned and kind = 'idea'))
);
create index on public.wish_items (child_id, kind, position);
create index on public.wish_items (event_id);

create table public.item_links (
  id uuid primary key default gen_random_uuid(),
  item_id uuid not null references public.wish_items (id) on delete cascade,
  url text not null check (url ~* '^https?://'),
  store text,
  country text check (country ~ '^[A-Z]{2}$'),
  price numeric(10, 2) check (price >= 0),
  currency text check (currency ~ '^[A-Z]{3}$'),
  created_at timestamptz not null default now()
);
create index on public.item_links (item_id);

-- Réservations : lisibles uniquement par leur auteur. Écriture via fonctions (voir migration suivante).
create table public.reservations (
  id uuid primary key default gen_random_uuid(),
  item_id uuid not null unique references public.wish_items (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade default auth.uid(),
  status text not null default 'reserved' check (status in ('reserved', 'purchased')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on public.reservations (user_id);

-- ---------------------------------------------------------------------------
-- Fonctions d'aide (security definer : contournent la RLS pour éviter la récursion).
-- Schéma `private` : utilisables par les policies mais non exposées en RPC par l'API.
-- ---------------------------------------------------------------------------

create schema private;
grant usage on schema private to authenticated;

create function private.is_group_member(p_group uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members where group_id = p_group and user_id = auth.uid());
$$;

create function private.shares_group_with(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.group_members a
    join public.group_members b on b.group_id = a.group_id
    where a.user_id = auth.uid() and b.user_id = p_user
  );
$$;

create function private.is_household_member(p_household uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.household_members where household_id = p_household and user_id = auth.uid());
$$;

create function private.child_group(p_child uuid) returns uuid
language sql stable security definer set search_path = '' as $$
  select h.group_id from public.children c join public.households h on h.id = c.household_id where c.id = p_child;
$$;

create function private.is_parent_of(p_child uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.children c
    join public.household_members hm on hm.household_id = c.household_id
    where c.id = p_child and hm.user_id = auth.uid()
  );
$$;

create function private.can_view_child(p_child uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select private.is_group_member(private.child_group(p_child));
$$;

-- Visibilité d'un cadeau pour l'utilisateur courant.
create function private.can_view_item(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.wish_items i
    where i.id = p_item
      and private.can_view_child(i.child_id)
      and (i.kind = 'wish' or not private.is_parent_of(i.child_id))
  );
$$;

-- Droit de modifier un cadeau : parents pour un souhait, auteur pour une idée.
create function private.can_edit_item(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.wish_items i
    where i.id = p_item
      and (
        (i.kind = 'wish' and private.is_parent_of(i.child_id))
        or (i.kind = 'idea' and i.created_by = auth.uid() and not private.is_parent_of(i.child_id))
      )
  );
$$;

create function public.generate_invite_code() returns text
language plpgsql volatile set search_path = '' as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text;
begin
  loop
    code := '';
    for i in 1..6 loop
      code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    exit when not exists (select 1 from public.groups where invite_code = code);
  end loop;
  return code;
end;
$$;

alter table public.groups alter column invite_code set default public.generate_invite_code();

create function public.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger wish_items_touch before update on public.wish_items
  for each row execute function public.touch_updated_at();
create trigger reservations_touch before update on public.reservations
  for each row execute function public.touch_updated_at();

-- Profil créé automatiquement à l'inscription.
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', ''))
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.households enable row level security;
alter table public.household_members enable row level security;
alter table public.children enable row level security;
alter table public.events enable row level security;
alter table public.wish_items enable row level security;
alter table public.item_links enable row level security;
alter table public.reservations enable row level security;

-- profiles
create policy "profils visibles par soi et sa famille" on public.profiles for select to authenticated
  using (id = auth.uid() or private.shares_group_with(id));
create policy "profil modifiable par soi" on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- groups (création via create_group, adhésion via join_group)
create policy "groupes visibles par leurs membres" on public.groups for select to authenticated
  using (private.is_group_member(id));
create policy "groupe renommable par ses membres" on public.groups for update to authenticated
  using (private.is_group_member(id)) with check (private.is_group_member(id));

-- group_members
create policy "membres visibles par le groupe" on public.group_members for select to authenticated
  using (private.is_group_member(group_id));
create policy "quitter un groupe" on public.group_members for delete to authenticated
  using (user_id = auth.uid());

-- households
create policy "foyers visibles par le groupe" on public.households for select to authenticated
  using (private.is_group_member(group_id));
create policy "créer un foyer dans son groupe" on public.households for insert to authenticated
  with check (private.is_group_member(group_id));
create policy "foyer modifiable par ses parents" on public.households for update to authenticated
  using (private.is_household_member(id)) with check (private.is_household_member(id));
create policy "foyer supprimable par ses parents" on public.households for delete to authenticated
  using (private.is_household_member(id));

-- household_members
create policy "parents visibles par le groupe" on public.household_members for select to authenticated
  using (private.is_group_member(group_id));
-- Aucune écriture directe : on rejoint un foyer via create_group, create_household ou join_household
-- (code du foyer), et on ne peut pas le quitter par l'API (sinon un parent pourrait sortir, lire les
-- réservations et idées de son enfant, puis revenir).

-- children
create policy "enfants visibles par le groupe" on public.children for select to authenticated
  using (private.is_group_member((select h.group_id from public.households h where h.id = household_id)));
create policy "enfants créés par les parents" on public.children for insert to authenticated
  with check (private.is_household_member(household_id));
create policy "enfants modifiés par les parents" on public.children for update to authenticated
  using (private.is_household_member(household_id)) with check (private.is_household_member(household_id));
create policy "enfants supprimés par les parents" on public.children for delete to authenticated
  using (private.is_household_member(household_id));

-- events
create policy "événements visibles par le groupe" on public.events for select to authenticated
  using (private.is_group_member(group_id));
create policy "événements créés par les membres" on public.events for insert to authenticated
  with check (
    private.is_group_member(group_id)
    and (child_id is null or private.child_group(child_id) = group_id)
  );
create policy "événements modifiés par les membres" on public.events for update to authenticated
  using (private.is_group_member(group_id)) with check (private.is_group_member(group_id));
create policy "événements supprimés par leur auteur ou les parents" on public.events for delete to authenticated
  using (created_by = auth.uid() or (child_id is not null and private.is_parent_of(child_id)));

-- wish_items
create policy "cadeaux visibles (idées masquées aux parents)" on public.wish_items for select to authenticated
  using (private.can_view_child(child_id) and (kind = 'wish' or not private.is_parent_of(child_id)));
create policy "souhaits ajoutés par les parents, idées par les autres" on public.wish_items for insert to authenticated
  with check (
    created_by = auth.uid()
    and (
      (kind = 'wish' and private.is_parent_of(child_id))
      or (kind = 'idea' and private.can_view_child(child_id) and not private.is_parent_of(child_id))
    )
  );
create policy "cadeaux modifiés par qui peut les gérer" on public.wish_items for update to authenticated
  using (private.can_edit_item(id))
  with check (
    (kind = 'wish' and private.is_parent_of(child_id))
    or (kind = 'idea' and created_by = auth.uid() and private.can_view_child(child_id)
        and not private.is_parent_of(child_id))
  );
create policy "cadeaux supprimés par qui peut les gérer" on public.wish_items for delete to authenticated
  using (private.can_edit_item(id));

-- item_links
create policy "liens visibles avec leur cadeau" on public.item_links for select to authenticated
  using (private.can_view_item(item_id));
create policy "liens ajoutés par qui gère le cadeau" on public.item_links for insert to authenticated
  with check (private.can_edit_item(item_id));
create policy "liens modifiés par qui gère le cadeau" on public.item_links for update to authenticated
  using (private.can_edit_item(item_id)) with check (private.can_edit_item(item_id));
create policy "liens supprimés par qui gère le cadeau" on public.item_links for delete to authenticated
  using (private.can_edit_item(item_id));

-- reservations : lecture de ses propres réservations uniquement, aucune écriture directe.
create policy "réservations lisibles par leur auteur" on public.reservations for select to authenticated
  using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Fonctions métier : groupe
-- ---------------------------------------------------------------------------

-- Crée un groupe, y ajoute l'utilisateur, crée son foyer et le Noël de l'année.
create function public.create_group(p_name text, p_household_name text, p_country text default 'CH')
returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_group uuid;
  v_household uuid;
  v_christmas date := make_date(extract(year from now())::int, 12, 25);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;

  insert into public.groups (name, created_by) values (p_name, auth.uid()) returning id into v_group;
  insert into public.group_members (group_id, user_id) values (v_group, auth.uid());
  insert into public.households (group_id, name, country) values (v_group, p_household_name, p_country)
    returning id into v_household;
  insert into public.household_members (household_id, group_id, user_id) values (v_household, v_group, auth.uid());
  if v_christmas < current_date then v_christmas := v_christmas + interval '1 year'; end if;
  insert into public.events (group_id, kind, title, event_date, created_by)
    values (v_group, 'christmas', 'Noël ' || extract(year from v_christmas), v_christmas, auth.uid());
  return v_group;
end;
$$;

-- Rejoint un groupe avec un code d'invitation. Retourne l'id du groupe.
create function public.join_group(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_group uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select id into v_group from public.groups where invite_code = upper(trim(p_code));
  if v_group is null then raise exception 'INVALID_INVITE_CODE'; end if;
  insert into public.group_members (group_id, user_id) values (v_group, auth.uid()) on conflict do nothing;
  return v_group;
end;
$$;

-- Crée un foyer dans un groupe et y ajoute l'utilisateur.
create function public.create_household(p_group uuid, p_name text, p_country text default 'CH')
returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_household uuid;
begin
  if not private.is_group_member(p_group) then raise exception 'NOT_A_MEMBER'; end if;
  if exists (select 1 from public.household_members where group_id = p_group and user_id = auth.uid()) then
    raise exception 'ALREADY_IN_HOUSEHOLD';
  end if;
  insert into public.households (group_id, name, country) values (p_group, p_name, p_country) returning id into v_household;
  insert into public.household_members (household_id, group_id, user_id) values (v_household, p_group, auth.uid());
  return v_household;
end;
$$;

-- Rejoint le foyer correspondant au code (second parent). Le foyer doit appartenir à un groupe
-- dont l'utilisateur est membre, et l'utilisateur ne doit pas déjà avoir de foyer dans ce groupe.
create function public.join_household(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_household public.households;
begin
  select * into v_household from public.households where invite_code = upper(trim(p_code));
  if v_household.id is null or not private.is_group_member(v_household.group_id) then
    raise exception 'INVALID_INVITE_CODE';
  end if;
  if exists (select 1 from public.household_members where group_id = v_household.group_id and user_id = auth.uid()) then
    raise exception 'ALREADY_IN_HOUSEHOLD';
  end if;
  insert into public.household_members (household_id, group_id, user_id) values (v_household.id, v_household.group_id, auth.uid());
  return v_household.id;
end;
$$;

-- Un cadeau ne peut être rattaché qu'à un événement du groupe de l'enfant.
create function private.check_item_event() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.event_id is not null and not exists (
    select 1 from public.events e where e.id = new.event_id and e.group_id = private.child_group(new.child_id)
  ) then
    raise exception 'INVALID_EVENT';
  end if;
  return new;
end;
$$;

create trigger wish_items_check_event before insert or update of event_id, child_id on public.wish_items
  for each row execute function private.check_item_event();

-- Suppression du compte (exigence App Store) : cascade sur profil, adhésions, réservations.
create function public.delete_account() returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  delete from auth.users where id = auth.uid();
end;
$$;

-- Droits : rien pour anon ; utilisateurs connectés limités au nécessaire (la RLS fait le reste).
revoke all on all tables in schema public from anon;
revoke truncate, references, trigger on all tables in schema public from authenticated;
revoke update on public.groups from authenticated;
grant update (name) on public.groups to authenticated;
revoke update on public.households from authenticated;
grant update (name, country) on public.households to authenticated;

revoke execute on all functions in schema public from public, anon;
revoke execute on all functions in schema private from public, anon;
grant execute on function
  public.create_group(text, text, text),
  public.join_group(text),
  public.create_household(uuid, text, text),
  public.join_household(text),
  public.delete_account()
to authenticated;
grant execute on function
  private.is_group_member(uuid),
  private.shares_group_with(uuid),
  private.is_household_member(uuid),
  private.child_group(uuid),
  private.is_parent_of(uuid),
  private.can_view_child(uuid),
  private.can_view_item(uuid),
  private.can_edit_item(uuid)
to authenticated;
