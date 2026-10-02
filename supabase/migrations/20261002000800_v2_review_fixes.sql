-- Corrections issues de la revue de la migration v2 (#45) : anniversaires (#43), cagnotte (#35),
-- remerciements (#42). Migration séparée pour ne pas modifier celle déjà revue.

-- ---------------------------------------------------------------------------
-- #43 Anniversaires : pas de doublon en cas de course, et mise à jour de l'événement futur
-- quand la date de naissance ou le prénom change.
-- ---------------------------------------------------------------------------
create or replace function private.ensure_birthday_event(p_child uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_child public.children;
  v_group uuid;
  v_next date;
  v_title text;
  v_event public.events;
begin
  -- Verrou par enfant : deux appels simultanés (trigger, app) sont sérialisés, le second voit l'événement du premier.
  perform pg_advisory_xact_lock(hashtext(p_child::text));
  select * into v_child from public.children where id = p_child;
  if v_child.id is null or v_child.birthdate is null then return; end if;
  select group_id into v_group from public.households where id = v_child.household_id;
  -- Anniversaire de cette année (29 février → 28 février les années non bissextiles), sinon l'an prochain.
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
      -- La date a changé : les rappels J-30 / J-7 doivent pouvoir repartir.
      delete from public.birthday_reminders_sent where event_id = v_event.id;
    end if;
    return;
  end if;
  insert into public.events (group_id, kind, title, event_date, child_id, created_by)
  values (v_group, 'birthday', v_title, v_next, p_child, null);
end;
$$;

drop trigger children_birthday on public.children;
create trigger children_birthday after insert or update of birthdate, first_name on public.children
  for each row execute function private.children_birthday_trigger();

-- ---------------------------------------------------------------------------
-- Droits : les rôles API n'ont pas à vider, référencer ou déclencher ces tables.
-- ---------------------------------------------------------------------------
revoke truncate, references, trigger on public.contributions, public.thanks from authenticated;
revoke truncate, references, trigger on public.budgets from authenticated;
revoke all on public.price_history from authenticated;
grant select on public.price_history to authenticated;

-- ---------------------------------------------------------------------------
-- Un participant ou donateur devenu parent de l'enfant ne lit plus les autres participations
-- ni les remerciements adressés aux donateurs.
-- ---------------------------------------------------------------------------
create function private.item_child(p_item uuid) returns uuid
language sql stable security definer set search_path = '' as $$
  select child_id from public.wish_items where id = p_item;
$$;
revoke execute on function private.item_child(uuid) from public, anon;
grant execute on function private.item_child(uuid) to authenticated;

drop policy "cagnotte : participants entre eux" on public.contributions;
create policy "cagnotte : participants entre eux" on public.contributions for select to authenticated
  using (
    user_id = auth.uid()
    or (private.is_pot_participant(item_id) and not private.is_parent_of(private.item_child(item_id)))
  );

drop policy "remerciements : expéditeur et donateurs" on public.thanks;
create policy "remerciements : expéditeur et donateurs" on public.thanks for select to authenticated
  using (
    sender_id = auth.uid()
    or (private.is_item_donor(item_id) and not private.is_parent_of(private.item_child(item_id)))
  );

create or replace function public.my_thanks()
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
  where private.is_item_donor(t.item_id) and not private.is_parent_of(i.child_id)
  order by t.created_at desc;
$$;

-- ---------------------------------------------------------------------------
-- join_pot : l'état « possédé » est relu après le verrou du cadeau.
-- ---------------------------------------------------------------------------
create or replace function public.join_pot(p_item uuid, p_amount numeric, p_currency text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_item public.wish_items;
  v_currency text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not private.can_view_item(p_item) then raise exception 'ITEM_NOT_FOUND'; end if;
  -- Verrou sur le cadeau (sérialise réservation et cagnotte), puis lecture de l'état à jour.
  select * into v_item from public.wish_items where id = p_item for update;
  if v_item.id is null then raise exception 'ITEM_NOT_FOUND'; end if;
  if v_item.owned then raise exception 'ITEM_OWNED'; end if;
  -- Les parents ne peuvent pas participer : même réponse qu'un cadeau déjà pris, pour ne rien révéler.
  if private.is_parent_of(v_item.child_id) then raise exception 'ITEM_UNAVAILABLE'; end if;
  if exists (select 1 from public.reservations where item_id = p_item) then raise exception 'ITEM_UNAVAILABLE'; end if;
  select currency into v_currency from public.contributions where item_id = p_item and user_id <> auth.uid() limit 1;
  if v_currency is not null and v_currency <> upper(p_currency) then raise exception 'CURRENCY_MISMATCH'; end if;
  insert into public.contributions (item_id, user_id, amount, currency)
  values (p_item, auth.uid(), p_amount, upper(p_currency))
  on conflict (item_id, user_id) do update set amount = excluded.amount, currency = excluded.currency;
end;
$$;

-- ---------------------------------------------------------------------------
-- thanks.photo_url : null, ou une image du bucket Storage public `images` (forme produite par uploadImage).
-- ---------------------------------------------------------------------------
alter table public.thanks add constraint thanks_photo_url_storage check (
  photo_url is null
  or photo_url ~ '^https://[A-Za-z0-9.-]+(:[0-9]+)?/storage/v1/object/public/images/[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'
);
