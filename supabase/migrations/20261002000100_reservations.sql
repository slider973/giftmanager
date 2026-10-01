-- Réservation anonyme et mode surprise (#4).
-- Personne ne lit la réservation d'un autre ; le statut public d'un cadeau n'est jamais
-- communiqué aux parents de l'enfant.

-- Statut public d'un cadeau pour l'utilisateur courant :
--   null        → l'utilisateur est parent de l'enfant (mode surprise) ou ne peut pas voir le cadeau
--   'owned'     → l'enfant le possède déjà
--   'mine'      → réservé par l'utilisateur courant
--   'taken'     → réservé par quelqu'un d'autre (sans jamais dire qui)
--   'available' → libre
create function public.item_public_status(p_item uuid) returns text
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
  select user_id into v_holder from public.reservations where item_id = p_item;
  if v_holder is null then return 'available'; end if;
  if v_holder = auth.uid() then return 'mine'; end if;
  return 'taken';
end;
$$;

-- Liste des cadeaux d'un enfant avec leur statut, en un seul appel.
-- p_kind : 'wish' (liste), 'idea' (idées), null (tout ce que l'utilisateur peut voir).
create function public.child_items(p_child uuid, p_event uuid default null, p_kind text default null)
returns table (
  id uuid, child_id uuid, event_id uuid, kind text, title text, notes text, image_url text,
  priority smallint, "position" integer, owned boolean, created_by uuid, created_at timestamptz,
  status text, my_reservation text
)
language sql stable security definer set search_path = '' as $$
  select i.id, i.child_id, i.event_id, i.kind, i.title, i.notes, i.image_url,
         i.priority, i.position, i.owned, i.created_by, i.created_at,
         case
           when private.is_parent_of(i.child_id) then null
           when i.owned then 'owned'
           when r.user_id is null then 'available'
           when r.user_id = auth.uid() then 'mine'
           else 'taken'
         end as status,
         case when r.user_id = auth.uid() then r.status end as my_reservation
  from public.wish_items i
  left join public.reservations r on r.item_id = i.id
  where i.child_id = p_child
    and private.can_view_child(i.child_id)
    and (i.kind = 'wish' or not private.is_parent_of(i.child_id))
    and (p_kind is null or i.kind = p_kind)
    and (p_event is null or i.event_id = p_event or i.event_id is null)
  order by i.owned, i.priority desc, i.position, i.created_at;
$$;

-- Réserve un cadeau. Atomique : en cas de course, une seule réservation réussit.
-- Erreurs : ITEM_UNAVAILABLE (déjà pris, sans dire par qui), ITEM_OWNED, ITEM_NOT_FOUND.
create function public.reserve_item(p_item uuid) returns text
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_owned boolean;
  v_holder uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not private.can_view_item(p_item) then raise exception 'ITEM_NOT_FOUND'; end if;
  select owned into v_owned from public.wish_items where id = p_item;
  if v_owned then raise exception 'ITEM_OWNED'; end if;

  insert into public.reservations (item_id, user_id) values (p_item, auth.uid())
  on conflict (item_id) do nothing;

  select user_id into v_holder from public.reservations where item_id = p_item;
  if v_holder <> auth.uid() then raise exception 'ITEM_UNAVAILABLE'; end if;
  return 'reserved';
end;
$$;

-- Passe sa réservation en « acheté » (ou revient à « réservé »).
create function public.set_reservation_purchased(p_item uuid, p_purchased boolean default true) returns text
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_status text := case when p_purchased then 'purchased' else 'reserved' end;
begin
  update public.reservations set status = v_status where item_id = p_item and user_id = auth.uid();
  if not found then raise exception 'RESERVATION_NOT_FOUND'; end if;
  return v_status;
end;
$$;

-- Annule sa réservation : le cadeau redevient disponible.
create function public.cancel_reservation(p_item uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  delete from public.reservations where item_id = p_item and user_id = auth.uid();
  if not found then raise exception 'RESERVATION_NOT_FOUND'; end if;
end;
$$;

-- Note : un parent peut marquer « possède déjà » un cadeau réservé (le refuser lui révélerait
-- la réservation). La réservation est conservée et l'acheteur voit `owned` dans « Mes achats ».

-- « Mes achats » : mes réservations avec le cadeau, l'enfant et l'événement.
create function public.my_reservations()
returns table (
  item_id uuid, title text, image_url text, status text, owned boolean, reserved_at timestamptz,
  child_id uuid, child_name text, event_id uuid, event_title text, event_date date
)
language sql stable security definer set search_path = '' as $$
  select r.item_id, i.title, i.image_url, r.status, i.owned, r.created_at,
         c.id, c.first_name, e.id, e.title, e.event_date
  from public.reservations r
  join public.wish_items i on i.id = r.item_id
  join public.children c on c.id = i.child_id
  left join public.events e on e.id = i.event_id
  where r.user_id = auth.uid()
  order by e.event_date nulls last, c.first_name, i.title;
$$;

revoke execute on function
  public.item_public_status(uuid),
  public.child_items(uuid, uuid, text),
  public.reserve_item(uuid),
  public.set_reservation_purchased(uuid, boolean),
  public.cancel_reservation(uuid),
  public.my_reservations()
from public, anon;
grant execute on function
  public.item_public_status(uuid),
  public.child_items(uuid, uuid, text),
  public.reserve_item(uuid),
  public.set_reservation_purchased(uuid, boolean),
  public.cancel_reservation(uuid),
  public.my_reservations()
to authenticated;
