-- Corrections de revue (#39, #40, #43) : rotation des relectures, robustesse de record_price_check,
-- rattrapage des rappels, budget et contrainte de photo.

-- ---------------------------------------------------------------------------
-- #39 Dernière tentative de relecture par lien, dans une table à part : une colonne de item_links serait
-- modifiable et lisible par les parents, et révélerait qu'un cadeau est réservé.
-- ---------------------------------------------------------------------------
create table public.link_checks (
  link_id uuid primary key references public.item_links (id) on delete cascade,
  last_checked_at timestamptz not null default now()
);
alter table public.link_checks enable row level security;
revoke all on public.link_checks from anon, authenticated;

create function public.mark_link_checked(p_link uuid) returns void
language sql volatile security definer set search_path = '' as $$
  insert into public.link_checks (link_id, last_checked_at) values (p_link, now())
  on conflict (link_id) do update set last_checked_at = now();
$$;

-- Rotation : les liens jamais tentés, puis les moins récemment tentés (succès ou échec).
create or replace function public.links_to_check(p_limit integer default 200)
returns table (link_id uuid, item_id uuid, url text)
language sql stable security definer set search_path = '' as $$
  select l.id, l.item_id, l.url
  from public.item_links l
  join public.reservations r on r.item_id = l.item_id and r.status = 'reserved'
  join public.wish_items i on i.id = l.item_id and not i.owned
  left join public.link_checks c on c.link_id = l.id
  order by c.last_checked_at nulls first, l.id
  limit greatest(p_limit, 0);
$$;

-- Devise invalide → null ; prix hors plage numeric(10,2) ou négatif → ignoré (au lieu de lever une erreur).
create or replace function public.record_price_check(p_link uuid, p_price numeric, p_currency text, p_in_stock boolean)
returns table (kind text, user_id uuid, item_id uuid, title text, old_price numeric, new_price numeric, currency text)
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_link public.item_links;
  v_holder uuid;
  v_title text;
  v_prev_price numeric;
  v_prev_currency text;
  v_prev_stock boolean;
  v_price numeric := case when p_price >= 0 and p_price < 100000000 then round(p_price, 2) end;
  v_cur text := case when upper(p_currency) ~ '^[A-Z]{3}$' then upper(p_currency) end;
begin
  select * into v_link from public.item_links where id = p_link;
  if v_link.id is null then return; end if;
  perform public.mark_link_checked(p_link);
  if v_price is null and p_in_stock is null then return; end if;

  select h.price, h.currency into v_prev_price, v_prev_currency
  from public.price_history h where h.link_id = p_link and h.price is not null
  order by h.checked_at desc, h.id desc limit 1;
  if not found then v_prev_price := v_link.price; v_prev_currency := v_link.currency; end if;
  select h.in_stock into v_prev_stock
  from public.price_history h where h.link_id = p_link and h.in_stock is not null
  order by h.checked_at desc, h.id desc limit 1;

  insert into public.price_history (link_id, price, currency, in_stock)
  values (p_link, v_price, v_cur, p_in_stock);

  select r.user_id into v_holder from public.reservations r where r.item_id = v_link.item_id and r.status = 'reserved';
  if v_holder is null then return; end if;
  select i.title into v_title from public.wish_items i where i.id = v_link.item_id and not i.owned;
  if v_title is null then return; end if;

  if v_price is not null and v_prev_price is not null and v_price >= v_prev_price then
    delete from public.price_alerts_sent a where a.link_id = p_link and a.kind = 'price_drop';
  end if;
  if p_in_stock is true then
    delete from public.price_alerts_sent a where a.link_id = p_link and a.kind = 'out_of_stock';
  end if;

  if v_price is not null and v_prev_price is not null
     and (v_cur is null or v_prev_currency is null or v_cur = v_prev_currency)
     and v_price < v_prev_price then
    insert into public.price_alerts_sent as a (link_id, user_id, kind, price)
    values (p_link, v_holder, 'price_drop', v_price)
    on conflict on constraint price_alerts_sent_pkey do update set price = excluded.price, sent_at = now()
      where a.price is null or excluded.price < a.price;
    if found then
      return query select 'price_drop'::text, v_holder, v_link.item_id, v_title, v_prev_price, v_price,
                          coalesce(v_cur, v_prev_currency);
    end if;
  elsif p_in_stock is false and v_prev_stock is not false then
    insert into public.price_alerts_sent (link_id, user_id, kind) values (p_link, v_holder, 'out_of_stock')
    on conflict on constraint price_alerts_sent_pkey do nothing;
    if found then
      return query select 'out_of_stock'::text, v_holder, v_link.item_id, v_title, null::numeric, null::numeric, null::text;
    end if;
  end if;
end;
$$;

revoke execute on function public.mark_link_checked(uuid) from public, anon, authenticated;
grant execute on function public.mark_link_checked(uuid) to service_role;

-- ---------------------------------------------------------------------------
-- #43 Rattrapage : un événement futur dont l'échéance est passée sans rappel reçoit le plus proche.
-- days_before = 7 si l'événement a lieu dans 7 jours ou moins, 30 s'il a lieu dans 8 à 30 jours.
-- ---------------------------------------------------------------------------
create or replace function private.birthday_reminder_candidates(p_today date default current_date)
returns table (event_id uuid, child_name text, event_date date, days_before integer, user_id uuid)
language sql stable security definer set search_path = '' as $$
  select x.event_id, x.child_name, x.event_date, x.bucket, x.user_id
  from (
    select e.id as event_id, c.first_name as child_name, e.event_date,
           case when e.event_date - p_today <= 7 then 7 else 30 end as bucket,
           gm.user_id, c.household_id
    from public.events e
    join public.children c on c.id = e.child_id
    join public.group_members gm on gm.group_id = e.group_id
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

-- ---------------------------------------------------------------------------
-- #40 my_budgets : le prénom et le titre ne sont renvoyés que si l'utilisateur voit encore l'enfant / l'événement.
-- ---------------------------------------------------------------------------
create or replace function public.my_budgets()
returns table (
  id uuid, child_id uuid, child_name text, event_id uuid, event_title text,
  amount numeric, currency text, spent numeric
)
language sql stable security definer set search_path = '' as $$
  select b.id, b.child_id,
         case when b.child_id is not null and private.can_view_child(b.child_id) then c.first_name end,
         b.event_id,
         case when e.id is not null and private.is_group_member(e.group_id) then e.title end,
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
         ), 0) as spent
  from public.budgets b
  left join public.children c on c.id = b.child_id
  left join public.events e on e.id = b.event_id
  where b.user_id = auth.uid()
  order by e.event_date nulls last, c.first_name nulls last, b.currency;
$$;

-- ---------------------------------------------------------------------------
-- #42 Contrainte de photo : `not valid` pour ne pas bloquer d'éventuelles lignes existantes (nouvelles écritures contrôlées).
-- ---------------------------------------------------------------------------
alter table public.thanks drop constraint thanks_photo_url_storage;
alter table public.thanks add constraint thanks_photo_url_storage check (
  photo_url is null
  or photo_url ~ '^https://[A-Za-z0-9.-]+(:[0-9]+)?/storage/v1/object/public/images/[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'
) not valid;
